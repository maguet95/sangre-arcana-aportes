# Armadura 3D (CEM) vs armaduras de mods — investigación completa

**Fecha:** 2026-09-03
**Entorno:** Fabric 1.21.11 · EMF 3.3.5 · ETF 7.2.1 · HMI 5.1.1 · Advanced Netherite 2.4.0
**Pack analizado:** Just 3D Armors HMI 1.4 (de *nagi*, se distribuye con Whimscape)
**Motivo:** el modpack **Living Vanilla** no podía añadir armaduras nuevas sin que se rompieran.
**Estado: ✅ RESUELTO** — ver sección 10.

Todo lo de aquí está **verificado en juego o leído del bytecode**. Nada es suposición.

---

## 1. El bug

Cualquier armadura de mod, equipada, se ve deformada: parches de color que no
corresponden, manchas oscuras y huecos por los que se ve la piel. La vanilla se ve bien.

- Solo ocurre en la armadura **puesta**, no en el icono del inventario.
- Afecta también a zombis, esqueletos, piglins y armor stands con armadura de mod.
- **Solo se manifiesta con EMF instalado** (es quien lee los `.jem` en Fabric).

### Causa raíz

El pack trae **72 modelos CEM** en `assets/minecraft/optifine/cem/`. Un `*_chestplate.jem`
**no describe "el peto de diamante"**: describe la capa de armadura de la entidad, venga
del item que venga.

1. **Ninguno de los 72 declara `"texture"`** → heredan la textura del item equipado.
2. Las texturas de nagi son **64×64**; toda armadura de mod usa el layout vanilla **64×32**.
3. El motor normaliza las UV por el `textureSize` declarado (`[64,64]`) y las aplica a la
   textura real: con una de 64×32 **no se salen, se comprimen** → caen en la zona equivocada.

Medido píxel a píxel sobre las 180 caras del modelo:

| Métrica | Valor |
|---|---|
| Caras cuyo centro cae en la mitad inferior del lienzo 64×64 | **56 (31 %)** |
| De esas, las que sacan un color distinto al correcto | **21 (38 %)** |
| Caras que caen en zona transparente de la textura del mod | **43** |

Paleta que acaba pintando sobre la armadura del mod — coincide con lo observado en pantalla:

```
TRANSPARENTE x43   -> huecos, se ve la piel del jugador
#a1fbe8 #4aedd9 #08bda7 #11727a   -> parches verde-turquesa
#322727 #49393f #51444e #3f303b   -> manchas marrón / gris
```

### Descartado con evidencia

| Sospecha | Veredicto |
|---|---|
| Scripts Lua de HoldMyItems | Limpios: filtran item por item con IDs `minecraft:` explícitos |
| Choque de core shaders | El pack trae `shaders/core/`, pero Whimscape e Immersive Ores 3D no los tocan |
| `items/*.json` y `models/item/*.json` | Todos bajo namespace `minecraft`, no pisan mods |
| Culpa del mod de armadura | No: Advanced Netherite usa el layout vanilla correctamente |

---

## 2. Cómo funciona EMF con la armadura (medido en el log de debug)

Con `logModelCreationData: true`, EMF traza su resolución de modelos. Hallazgos:

**Nombres que EMF evalúa realmente:**
`player_helmet`, `player_chestplate`, `player_leggings`, `player_boots` (+ `player_slim`,
`armor_stand`, `armor_stand_small`, `zombie`, `husk`, `drowned`, `skeleton`, `stray`,
`bogged`, `piglin`, `zombified_piglin`, `zombie_villager`).

> ⚠️ **EMF NUNCA evalúa `*_outer_armor` ni `*_inner_armor`.** Son formato legacy de
> OptiFine y en esta versión son código muerto. El pack los incluye pero no se usan.

**Orden de búsqueda por modelo:**
```
1. <nombre>.jem          (base, variante 1)
2. <nombre>.properties   (las reglas)
3. <nombre>2.jem         (variante 2)     <- SOLO si el paso 1 existe
4. si nada: "EMF vanilla part made: ..."  (modelo vanilla)
```

> ⚠️ **El `.jem` base es obligatorio.** Sin él, EMF ni siquiera busca `<nombre>2.jem`.
> Esto contradice el changelog de EMF, que dice que la variante 1 se provee sola.
> La opción `enforceOptifineVariationRequiresDefaultModel` estaba en `false` y aun así
> lo exige.

**Truco útil:** un `.jem` base con `"models": []` (sin partes declaradas) hace que EMF
conserve la geometría vanilla. Sirve como "variante 1 = vanilla" sin tener que replicar
el modelo vanilla a mano.

---

## 3. Las condiciones: qué funciona y qué no

Probado en juego, una condición por pieza para aislar variables:

| Condición | Resultado | Conclusión |
|---|---|---|
| `items.1=<lista vanilla>` | nunca coincide | — |
| `items.1=any` | nunca coincide | la lista está **vacía** |
| `items.1=none` | **siempre** coincide | confirma que está vacía |
| `name.1=<jugador>` | **funciona**, sin delay | la **entidad sí es accesible** |
| `nbt.1.ArmorItems.<n>.id` | no coincide | formato antiguo, no aplica en 1.21.11 |
| `nbt.1.Inventory.*.id` | **funciona** | impreciso (cualquier slot del inventario) |
| `nbt.1.equipment.<slot>.id=raw:<item>` | **funciona** | preciso por slot ✅ |
| `nbt.1.equipment.<slot>.id=minecraft:*_<pieza>` (comodín) | inconsistente | no fiable |

### Conclusiones clave

- **`items=` está roto para capas de armadura**: la lista de items equipados llega vacía.
  La prueba definitiva es que `items=none` coincide *teniendo armadura puesta*.
- **`nbt.equipment.<slot>.id` con `raw:` sí funciona**, y es la única vía sin mod.
- El **comodín** en NBT no es fiable; hay que enumerar cada material.
- La vía NBT tiene **delay visible** al cambiarse de armadura y es cara
  (7 materiales × 4 piezas × 12 entidades = **336 reglas**). Ver issue #256 de EMF,
  "Extreme fps drop caused by nbt property".

### Ajustes que reducen (no eliminan) el delay
```
EMF  modelUpdateFrequency       : Average -> Instant
ETF  textureUpdateFrequency_V2  : Fast    -> Instant
```

---

## 4. El mod: qué se resolvió y qué no

`ArmorCemCompat` registra en ETF una propiedad `armor_item` vía la API pública
`ETFApi.registerCustomRandomPropertyFactory(...)`. Lee el item con
`((LivingEntity) state.entity()).getItemBySlot(slot)` — sin NBT, sin caché, sin serializar.

Sintaxis: `armor_item.1=chest:minecraft:*_chestplate` (admite comodines, varios patrones y
negación con `!`).

### Lo que se resolvió — verificado en log

| | Resultado |
|---|---|
| Reglas parseadas | **80, sin errores ni excepciones** |
| `entity()` llega como `LivingEntity` | ✅ siempre |
| Lee el item real | ✅ `advancednetherite:netherite_diamond_chestplate` |
| **Acierto de la condición** | **800 evaluaciones, 100 % correctas** |

```
100 CHEST=minecraft:diamond_chestplate                  => true    ✅
100 CHEST=advancednetherite:netherite_diamond_chestplate => false   ✅
```

**Dos correcciones que resultaron imprescindibles:**

1. `setCanUpdate(true)` en el constructor de la propiedad — sin ello ETF cachea el primer
   resultado por entidad y no vuelve a evaluar.
2. **Mixin sobre `PropertiesRandomProvider.entityCanUpdate(UUID)` → `true`**. ETF solo
   concede permiso de re-evaluación la primera vez que una regla coincide. Efecto medido:
   las evaluaciones pasaron de **4 (solo al entrar)** a **400+ (continuas)**.

### 🔴 El límite que quedaba (RESUELTO en la sección 10)

> **EMF calcula la variante correcta pero no la aplica en vivo.** El modelo de la capa de
> armadura se construye una vez por entidad y se reutiliza. La armadura que se ve es
> **siempre la que se tenía al entrar al mundo**, aunque la propiedad devuelva el valor
> correcto en cada frame.

Prueba: entrando con netherita → se ve bien (`false` ×400, correcto). Al cambiar a diamante
→ la propiedad devuelve `true` ×400, pero el modelo sigue siendo el de la variante 1.
Y al revés, entrando con diamante, es el diamante el que se ve bien.

Es decir: la cadena de decisión funcionaba entera y fallaba el último paso.
**Resuelto** con el mixin sobre `HumanoidArmorLayer` — ver sección 10.

---

## 5. Para reportar a Traben (autor de EMF/ETF)

Dos issues ya abiertos que esto complementa:

- **#347** "Armor NBT/Components for .properties from its item data" — el autor confirma
  que el NBT que se lee es el de la entidad que lleva la armadura.
- **#272** "Nbt.Inventory issue" — los mobs tienen `ArmorItems.<n>`, pero los jugadores usan
  `Inventory` con slots dinámicos y no hay forma de direccionar un slot concreto.
- **#256** "Extreme fps drop caused by nbt property" — el coste de la vía NBT.

**Lo nuevo que aporta esta investigación:**

1. La propiedad `items` de ETF **llega vacía** al evaluar capas de armadura
   (demostrable en un renglón: `items.1=none` coincide con armadura puesta).
2. `entityCanUpdate(UUID)` **bloquea la re-evaluación** de condiciones que dependen del
   estado cambiante del jugador, como el equipo.
3. Incluso re-evaluando correctamente, **EMF no aplica el cambio de variante en las capas
   de armadura** hasta que el modelo se reconstruye.
4. El `.jem` base es **obligatorio** para que se busquen las variantes, pese a lo que dice
   el changelog y con `enforceOptifineVariationRequiresDefaultModel=false`.

Con estos cuatro puntos arreglados, un pack de armadura 3D podría convivir con cualquier mod
de armadura sin tocar nada más.

---

## 6. Para nagi — el arreglo de raíz, sin depender de nadie

Su modelo lee de un atlas propio de 64×64. Si leyera de **las mismas zonas que el modelo
vanilla (64×32)**, entonces:

- armadura vanilla → 3D con su textura → igual de bien que ahora
- armadura de **cualquier mod** → 3D con la textura del mod → **bien, y encima en 3D**

**El 69 % del modelo (124 de 180 caras) ya está dentro del rango vanilla.** Habría que
remapear las **56 caras** restantes para que reutilicen regiones del layout vanilla en vez
de la mitad inferior de su atlas. La geometría 3D no cambia; solo de dónde toma el color.

**Aparte:** el pack incluye `assets/minecraft/shaders/core/` (`entity.fsh/vsh`,
`rendertype_item_entity_translucent_cull.*`). Los core shaders son globales y solo uno puede
ganar: cualquier otro pack que los sobrescriba (Fresh Animations y similares) entrará en
conflicto silencioso. No causa este bug, pero conviene documentarlo en la página del pack.

---

## 7. Estado de las soluciones

| Opción | Vanilla | Mods | Requiere | Estado |
|---|---|---|---|---|
| **Pack + mod `armor_item`** | 3D | correcta | 1 jar | ✅ **RESUELTO** — ver sección 10 |
| Pack con `nbt.equipment` (336 reglas) | 3D | correcta | nada | funciona, con delay; superado |
| Remapear el modelo (nagi) | 3D | **3D** | nada | no implementado; sigue siendo la mejora ideal |
| Pack sin CEM de armadura | plana | correcta | nada | funciona; se pierde el 3D equipado |

---

## 12. Versión final: el parche integrado en el mod (2026-09-04)

Mejora sobre la sección 10. En vez de entregar el pack de armadura **modificado**, el mod
trae **un resource pack integrado** con las reglas, y el pack del autor se usa **intacto**.

### Cómo funciona

El parche solo AÑADE archivos que ningún pack de armadura trae:

```
<entidad>_<pieza>2.jem         -> modelo vanilla (variante 2)
<entidad>_<pieza>.properties   -> models.1=2 + armor_item.1=!<slot>:minecraft:*_<pieza>
```

La regla va **negada**: el modelo 3D del autor sigue siendo la variante 1 (por defecto), y
solo se cambia a la variante 2 (vanilla) cuando la pieza **no** es de `minecraft:`.

Registro con la API de Fabric:
```java
ResourceManagerHelper.registerBuiltinResourcePack(
    Identifier.fromNamespaceAndPath(MOD_ID, "compat"), container,
    ResourcePackActivationType.DEFAULT_ENABLED);
```

> ⚠️ En 1.21.11 con Mojang mappings, `ResourceLocation` se llama **`Identifier`**
> (`net.minecraft.resources.Identifier`).

### Ventajas

| | Pack modificado (sección 10) | **Parche integrado** |
|---|---|---|
| Archivos a instalar | 2 | **1 (solo el jar)** |
| Pack del autor | modificado | **intacto, el de Modrinth** |
| Si el autor actualiza | hay que rehacerlo | **sigue funcionando** |
| Redistribuye arte ajeno | sí | **no — publicable sin permiso** |
| Sirve para otros packs 3D | no | **sí, es genérico** |

Los nombres de archivo (`player_chestplate.jem`…) los define **EMF**, no el autor del pack,
así que el parche vale para cualquier pack de armadura 3D con CEM.

### Verificado en juego
Pack original de nagi sin tocar + el mod → armadura vanilla en 3D, armadura de mods correcta,
cambio en vivo instantáneo.

### Limitación conocida: el orden de packs

**El pack integrado debe ir POR ENCIMA del pack de armadura.** Si va debajo, EMF descarta el
`.properties` (compara los `pack order indices` y exige que no venga de un pack inferior) y
vuelve el bug.

- Por defecto **queda encima**, sin tocar nada.
- Se puede mover, y si se mueve **hay que reiniciar el juego**: recargar recursos no basta,
  porque EMF no reconstruye los modelos de armadura en caliente.

Se dejó `DEFAULT_ENABLED` (desactivable) en vez de `ALWAYS_ENABLED` a propósito: si algún día
el pack de armadura trae sus propias reglas, las nuestras las pisarían al ir por encima; en
ese caso el usuario apaga el pack integrado y conserva la propiedad `armor_item`, que las
reglas del autor siguen necesitando.
