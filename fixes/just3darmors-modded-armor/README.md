# 🛡️ Fix: los packs de armadura 3D (CEM) rompen la armadura de todos los mods

> **Afecta a:** cualquier modpack con un resource pack de armadura 3D (CEM) + **EMF** + algún
> mod que añada armadura. Detectado con **Just 3D Armors HMI 1.4** en el modpack
> **Living Vanilla** (Fabric 1.21.11), pero el problema es general.
>
> **Estado: ✅ RESUELTO y verificado en juego.**
> **Probado con:** EMF `3.3.5`, ETF `7.2.1`, HMI `5.1.1`, Advanced Netherite `2.4.0`, Fabric 1.21.11.
>
> El diagnóstico completo, con todo lo que se probó y descartó, está en
> [**HALLAZGOS.md**](HALLAZGOS.md).

---

## 🔴 El síntoma

Te pones una armadura de cualquier mod y se ve **deformada**: parches de color que no
corresponden, manchas oscuras y **huecos por los que se ve la piel**. La vanilla se ve bien.
Rompe **todas** las armaduras de mods, sin excepción.

Dos detalles que despistan:
- El **icono del inventario se ve bien**. El bug solo aparece en la armadura **puesta** (F5).
- También afecta a **zombis, esqueletos, piglins y armor stands** con armadura de mod.

**Y no es cosmético: bloquea el modpack.** Cualquier tier de armadura nuevo entra roto, así
que no se pueden añadir dimensiones ni progresión. El pack de armadura acaba congelando el
árbol de progresión entero.

---

## ✅ La causa

Un `*_chestplate.jem` **no describe "el peto de diamante"**: describe **la capa de armadura de
la entidad**, venga del item que venga.

1. Los `.jem` **no declaran `"texture"`** → heredan la textura del item equipado.
2. Las texturas del pack 3D son **64×64**; toda armadura de mod usa el layout vanilla **64×32**.
3. El motor normaliza las UV por el `textureSize` declarado y las aplica a la textura real:
   con una de 64×32 **no se salen, se comprimen** → leen la zona equivocada.

Medido píxel a píxel sobre las 180 caras del modelo: **56 caras (31%)** caen en la mitad
inferior del lienzo, que en una textura de mod no existe; **43** caen en zona transparente
(de ahí los huecos).

> No es culpa del pack ni del mod de armadura. Al modelo CEM le falta una condición: nunca se
> le dijo *a qué* armaduras aplicarse. Y **EMF no ofrecía forma de expresarla** — de ahí este fix.

---

## 🧩 Por qué no bastaba con un `.properties`

Se intentaron todas las condiciones que EMF/ETF ofrecen. Resumen (detalle en HALLAZGOS.md):

| Condición | Resultado |
|---|---|
| `items.1=<lista vanilla>` | nunca coincide |
| `items.1=any` | nunca coincide |
| `items.1=none` | **siempre** coincide ← prueba de que la lista llega **vacía** |
| `nbt.1.equipment.<slot>.id=raw:...` | funciona, pero lento (336 reglas) y se congela |

**`items=` está roto para capas de armadura**: la lista de items equipados llega vacía. La
prueba definitiva es que `items=none` coincide *teniendo armadura puesta*.

---

## 🛠️ La solución

Un mod cliente de **10 KB** que arregla los tres eslabones rotos:

| # | Problema | Solución |
|---|---|---|
| 1 | `items=` llega vacío | añade la propiedad **`armor_item`**, que lee `getItemBySlot(slot)` de la entidad viva — sin NBT, sin caché |
| 2 | ETF solo permite re-evaluar la primera vez | mixin en `PropertiesRandomProvider.entityCanUpdate()` |
| 3 | El layer conserva el modelo del arranque | mixin en `HumanoidArmorLayer.getArmorModel()` |

El punto 3 es el que hace que, **sin este mod, cualquier variante de modelo de armadura no se
actualice hasta pulsar F3+T**. Aplica a todo pack CEM con variantes, no solo a este.

### El detalle clave

`HumanoidRenderState` **no implementa** `EMFEntityRenderState`, así que el `instanceof` falla
en silencio. La conversión correcta la expone el propio EMF:

```java
EMFEntityRenderState emfState = EMFEntityRenderState.from(state);
if (emfState != null) emfRoot.doVariantCheck(emfState);
```

### Resultado

- Armadura **vanilla** → 3D del pack ✅
- Armadura de **mods** → correcta ✅
- **Cambio en vivo, sin delay ni recargar** ✅
- **Cero píxeles del pack modificados** (293 archivos de arte idénticos byte a byte)

---

## 📥 Instalación

**1. El mod** → copia [`mod/armor-cem-compat-1.0.0.jar`](mod/) a tu carpeta `mods/`.
Requiere **EMF** y **ETF** (los mismos que ya necesita cualquier pack CEM).

**2. Las reglas** → hay que añadirlas al pack de armadura. En
`assets/minecraft/optifine/cem/` del pack:

1. Renombra cada `<entidad>_<pieza>.jem` a `<entidad>_<pieza>2.jem` (pasa a ser la variante 2)
2. Copia ahí un `<entidad>_<pieza>.jem` con el modelo **vanilla**, como variante 1
3. Copia los 48 [`properties/`](properties/) de este repo

> ℹ️ El paso 2 es obligatorio: **sin el `.jem` base, EMF ni siquiera busca la variante 2.**
> Se obtiene poniendo `modelExportMode: ALL_LOG_AND_JEM` en la config de EMF, entrando al
> juego una vez, y recogiendo los `.jem` de `.minecraft/emf/export/`.

⚠️ **Si pones las reglas sin el mod, vuelve el bug original**: EMF no entiende `armor_item`,
descarta la regla y aplica el 3D a todo.

⚠️ Si rearmas el `.zip` en Windows, **no uses `Compress-Archive`**: guarda las rutas con `\` y
Minecraft no las reconoce, así que el pack carga vacío.

---

## 📝 Sintaxis de `armor_item`

```properties
models.1=2
armor_item.1=chest:minecraft:*_chestplate
```

```
armor_item.<n>=<slot>:<id>  [más patrones separados por espacio]

  slot : head | chest | legs | feet   (también helmet/chestplate/leggings/boots)
  id   : admite '*'; sin namespace se asume minecraft
  '!'  : niega el patrón
```

Ejemplos:
```properties
armor_item.1=head:minecraft:*_helmet head:minecraft:turtle_helmet
armor_item.1=chest:!advancednetherite:*
```

**`minecraft:*_chestplate` excluye cualquier mod por namespace** — incluidos los que se
instalen en el futuro. No hay lista que mantener.

---

## 🔧 Código fuente

En [`codigo-fuente/`](codigo-fuente/). MIT, sin dependencias más allá de Fabric + EMF/ETF.
Para compilarlo: `./gradlew build` (Java 21).

Son dos clases y dos mixins; se lee en cinco minutos y está comentado en español.

---

## 📢 Para compartir (copiar y pegar)

> **Fix: los packs de armadura 3D rompen la armadura de todos los mods**
>
> Si usas un resource pack de armadura 3D (CEM) con EMF y la armadura de cualquier mod se ve
> deformada, con colores raros y huecos, no es cosa tuya ni del mod de armadura.
>
> **Causa:** los modelos CEM se aplican a la capa de armadura de la **entidad**, no al item,
> así que capturan también las armaduras de otros mods, que usan otro mapa de textura.
> Y EMF **no ofrece ninguna condición** para filtrarlas: su propiedad `items` llega vacía en
> las capas de armadura (se demuestra en un renglón — `items=none` coincide con armadura puesta).
>
> **Solución:** un mod de 10 KB que añade la condición que falta (`armor_item`) y arregla dos
> cachés que impedían que el modelo se actualizara al cambiarte de armadura. Cero
> modificaciones al arte del pack.
>
> Diagnóstico completo, el mod, las reglas y el código fuente 👇
> https://github.com/maguet95/sangre-arcana-aportes/tree/main/fixes/just3darmors-modded-armor

---

## 📌 Resumen en una línea

> Los modelos CEM de armadura se aplican a **toda** armadura equipada y EMF no ofrece forma de
> filtrarlos por item (`items=` llega vacío). Se arregla con un mod que añade la propiedad
> `armor_item` y destraba los dos cachés que impedían actualizar el modelo en vivo.
