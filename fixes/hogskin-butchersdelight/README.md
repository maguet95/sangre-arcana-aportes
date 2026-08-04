# 🐗 Fix: el Hogskin de Iron's Spells nunca dropea (Butcher's Delight)

> **Afecta a:** cualquier modpack con **Iron's Spells 'n Spellbooks** + **Butcher's Delight**
> instalados a la vez. Incluye a **Perfection** (donde se detectó).
> **Verificado el 2026-08-03** leyendo los `.jar` y el bytecode, no por suposición.
> **Versiones probadas:** `irons_spellbooks-1.20.1-3.4.0.9`, `butchersdelight-1.20.12.1.0`, Forge 1.20.1.

---

## 🔴 El síntoma

Matas hoglins en el Nether y **nunca cae el `Hogskin`** de Iron's Spells.
Solo cae la **"Hoglin Carcass"** de Butcher's Delight.

Y eso te deja **bloqueada la progresión de magia**, porque el Hogskin no es decorativo:

| Receta de Iron's Spells | Hogskin que pide |
|---|---|
| Gold Spell Book | **2** |
| Diamond Spell Book | **4** |
| Lesser Spell Slot Upgrade | **6** |

Sin Hogskin te quedas en el spell book básico. **No hay otra fuente en todo el juego**:
es dropeo de hoglin y nada más (verificado: no está en ninguna loot table de cofre).

---

## 🧩 Lo que NO es (descartado con evidencia)

Antes de dar con la causa se descartó lo obvio:

- ❌ **No es un choque de IDs.** Son items de mods distintos y Minecraft los separa por
  namespace: `irons_spellbooks:hogskin` vs `butchersdelight:hoglinskin`. Nunca colisionan.
- ❌ **No es Nether Exp.** Tiene una textura `hoglin_hide.png` suelta, pero **sin modelo ni
  nombre traducido** → no es un item registrado. Es un archivo huérfano.
- ❌ **No es un mod que borre loot modifiers.** Los 19 mods del pack que traen
  `global_loot_modifiers.json` usan **todos** `"replace": false`. Ninguno pisa a los demás.
- ❌ **No es que sobrescriban la tabla de botín del hoglin.** Ningún mod incluye
  `data/minecraft/loot_tables/entities/hoglin.json`.
- ❌ **No es mala suerte.** El drop es `uniform 0–1` + saqueo `0–2`. Con matar unos pocos
  hoglins ya deberías tener varios.

---

## ✅ La causa real

Iron's Spells entrega el Hogskin con un **Global Loot Modifier**:

```
data/forge/loot_modifiers/global_loot_modifiers.json
  └── irons_spellbooks:entity_drops/hoglin_modifier
        condición: la entidad es minecraft:hoglin
        añade:     irons_spellbooks:entities/additional_hoglin_loot
```

Un loot modifier **solo se ejecuta si la entidad muere de verdad** y su tabla de botín se
tira. Y ahí está el problema.

Butcher's Delight, en `net.mcreator.butchersdelight.procedures.CustomDropsProcedure`,
escucha `LivingDeathEvent` y para el hoglin hace esto (bytecode real):

```
514: instanceof  .../monster/hoglin/Hoglin      ← ¿es un hoglin?
548: ButchersdelightModItems.DEADHOGLIN         ← suelta la "Hoglin Carcass"
593: Event.setCanceled(true)                    ← CANCELA el LivingDeathEvent
609: Entity.discard()                           ← borra al hoglin del mundo
```

**Al cancelar `LivingDeathEvent`, el hoglin nunca "muere" para el juego.**
`die()` no corre → la tabla de botín no se tira → **el loot modifier de Iron's Spells
nunca llega a ejecutarse**. Por eso el Hogskin es literalmente inobtenible.

> No es un bug de Iron's Spells ni de Butcher's Delight por separado. Cada uno hace algo
> razonable; juntos se anulan. Y afecta a **todos** los animales que Butcher's Delight
> convierte en carcasa: vaca, oveja, cerdo, cabra, llama, pollo, conejo, strider y hoglin.
> Si otro mod le añade drops a alguno de esos, también se pierden.

---

## 🛠️ La solución (recomendada)

**Un datapack que añade una receta**: `1 Hoglinhide` → `1 Hogskin`.

Así los dos mods siguen intactos y completos: sigues carneando con Butcher's Delight, y el
Hogskin se obtiene procesando la piel — que además es *más* trabajo que el drop original
(matar → carcasa → colgar en el gancho → 3 fases → tijeras → curtidor), así que no rompe
el balance. Sale en JEI/EMI como cualquier otra receta.

**Por qué 1 a 1 y no un 50/50 aleatorio.** Un datapack no puede hacer que Butcher's Delight
cancele "a medias": el `setCanceled(true)` está fijo en el código, sin condición de azar
(lo único que lo apaga es `onlyknifedrops`, y es global para los 9 animales). Con la receta
1:1 el reparto lo decides tú en vez de la ruleta: **cada piel la usas para cuero *o* la
conviertes en Hogskin**. Mismo efecto práctico, cero riesgo, y sin RNG que frustre.

### Instalar

Elige **una** de las tres. Todas hacen lo mismo.

**A) Servidor** *(recomendado en multijugador)*
1. Copia la carpeta `sa_compat_hogskin` (o el `.zip`) a `world/datapacks/`
2. Reinicia el servidor, o corre `/reload`
3. Comprueba con `/datapack list` que aparece habilitado

> ℹ️ **Basta con ponerlo en el servidor** — las recetas se sincronizan solas a todos los
> clientes. Los jugadores no tienen que instalar nada.

**B) Un jugador solo (singleplayer)**
1. Copia la carpeta o el `.zip` a `.minecraft/saves/<tu mundo>/datapacks/`
2. Sal y entra al mundo, o corre `/reload`

**C) Con OpenLoader** *(lo más cómodo si juegas Perfection)*

Perfection ya incluye **OpenLoader**, que carga datapacks globales:

1. Copia el `.zip` a `.minecraft/openloader/data/`
2. Reinicia el juego

Ventaja: **aplica a todos tus mundos, presentes y futuros**, sin tocar ningún save. Si
creas un mundo nuevo el fix ya está puesto. Es la vía que menos se rompe al actualizar.

### El contenido (por si lo quieres hacer a mano)

`pack.mcmeta`
```json
{"pack":{"pack_format":15,"description":"Compat Hogskin"}}
```

`data/sa_compat/recipes/hoglinskin_to_hogskin.json`
```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [{ "item": "butchersdelight:hoglinskin" }],
  "result": { "item": "irons_spellbooks:hogskin", "count": 1 }
}
```

> `count` es el número de Hogskin por piel. Si te parece muy lento para el
> Lesser Spell Slot Upgrade (pide 6), súbelo a 2 o 3 y listo — es el único número
> que hay que tocar para ajustar el balance a tu gusto.

---

## 🔀 Las otras alternativas (y por qué no)

| Alternativa | Qué hace | Veredicto |
|---|---|---|
| **`"onlyknifedrops": true`** en `config/ButchersDelight.json` | El bytecode confirma que con `true` la procedure hace `return` de inmediato: los animales mueren normal y todos los drops vuelven | ❌ **Apaga las carcasas de TODOS los animales.** Deja a Butcher's Delight sin su mecánica principal. Arregla el síntoma matando el mod |
| **Quitar uno de los dos mods** | — | ❌ Los dos aportan; es tirar contenido por un item |
| **Loot modifier propio sobre la carcasa** | Añadir Hogskin al botín del bloque de carcasa | 🟡 Funciona, pero es más frágil y no se ve en JEI. La receta es más simple y más clara para el jugador |
| **Dar Hogskin por cofres del Nether** | Añadirlo a loot de cofres | 🟡 Rompe la lógica ("piel de hoglin" saliendo de un cofre) y lo hace depender de la suerte |
| **Script de CraftTweaker** | Soltar el Hogskin al 50% escuchando `LivingDeathEvent`, sin tocar nada más | 🟡 Es la única vía **quirúrgica** (solo afecta al hoglin) y CraftTweaker 14.0.57 sí expone `ExpandLivingDeathEvent`. Pero **no está verificado** que el listener corra antes de que Butcher's Delight cancele el evento — hay que probarlo en vivo. Si alguien lo confirma, es una mejora legítima sobre la receta |

---

## 🔎 Cómo se verificó (para quien quiera repetirlo)

```bash
# 1. ¿Quién tiene items "hogskin" / "hoglin hide"?
unzip -l <mod>.jar | grep -iE "hogskin|hoglin_hide"

# 2. ¿Algún mod borra los loot modifiers de los demás?
unzip -p <mod>.jar data/forge/loot_modifiers/global_loot_modifiers.json | grep replace

# 3. ¿Alguien pisa la tabla de botín del hoglin?
unzip -l <mod>.jar data/minecraft/loot_tables/entities/hoglin.json

# 4. La prueba definitiva — leer el bytecode
unzip -o butchersdelight-*.jar -d bd
javap -p -c -classpath bd \
  net.mcreator.butchersdelight.procedures.CustomDropsProcedure \
  | grep -E "Hoglin|setCanceled|discard|onlyknifedrops"
```

`javap` viene con cualquier JDK — si usas un launcher con Java propio, ya lo tienes
(ej. `.../runtime/x64/jdk-17.../bin/javap`).

---

## 📢 Para compartir (copiar y pegar)

> **Fix: el Hogskin de Iron's Spells nunca dropea en Perfection**
>
> Si matas hoglins y nunca te cae el **Hogskin**, no es mala suerte ni un bug tuyo.
>
> **Causa:** Butcher's Delight cancela el evento de muerte del hoglin para poner su
> "Hoglin Carcass" en su lugar. Al cancelarlo, el hoglin nunca "muere" para el juego, su
> tabla de botín no se tira, y el drop que Iron's Spells añade ahí **nunca llega a
> ejecutarse**. El Hogskin queda literalmente inobtenible.
>
> Y eso **bloquea la progresión de magia**: Gold Spell Book pide 2, Diamond Spell Book 4 y
> Lesser Spell Slot Upgrade 6. No hay ninguna otra fuente en el juego.
>
> **Solución:** un datapack de 2 archivos que añade la receta **1 Hoglinhide → 1 Hogskin**.
> Los dos mods quedan intactos, sale en JEI, y en servidor solo hace falta instalarlo en el
> servidor (las recetas se sincronizan solas).
>
> Lo sueltas en `.minecraft/openloader/data/` (Perfection ya trae OpenLoader) y listo, te
> sirve para todos tus mundos.
>
> Diagnóstico completo con el bytecode, el datapack ya armado y los comandos para
> verificarlo tú mismo 👇
> https://github.com/maguet95/sangre-arcana-aportes/tree/main/fixes/hogskin-butchersdelight

---

## 📌 Resumen en una línea

> Butcher's Delight cancela el `LivingDeathEvent` del hoglin para poner su carcasa, y eso
> impide que se tire la tabla de botín — así que el loot modifier de Iron's Spells que da
> el **Hogskin** nunca corre. Se arregla con un datapack de 2 archivos que convierte
> **Hoglinhide → Hogskin**.
