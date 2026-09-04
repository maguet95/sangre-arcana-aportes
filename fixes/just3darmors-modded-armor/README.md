# 🛡️ Fix: los packs de armadura 3D (CEM) rompen la armadura de todos los mods

> **Afecta a:** cualquier modpack con un resource pack de armadura 3D (CEM) + **EMF** + algún
> mod que añada armadura. Detectado con **Just 3D Armors HMI 1.4** en el modpack
> **Living Vanilla** (Fabric 1.21.11), pero el problema es general: la causa está en EMF, no
> en el pack.
>
> **Estado: ✅ RESUELTO y verificado en juego.**
> **Probado con:** EMF `3.3.5`, ETF `7.2.1`, HMI `5.1.1`, Advanced Netherite `2.4.0`, Fabric 1.21.11.
>
> Diagnóstico completo, con todo lo probado y descartado: [**HALLAZGOS.md**](HALLAZGOS.md).
> El mod tiene repo propio: **https://github.com/maguet95/armor-cem-compat**

---

## 🔴 El síntoma

Te pones una armadura de cualquier mod y se ve **deformada**: parches de color que no
corresponden, manchas oscuras y **huecos por los que se ve la piel**. La vanilla se ve bien.
Rompe **todas** las armaduras de mods, sin excepción.

Dos detalles que despistan:
- El **icono del inventario se ve bien**. El bug solo aparece en la armadura **puesta** (F5).
- También afecta a **zombis, esqueletos, piglins y armor stands** con armadura de mod.

**Y no es cosmético: bloquea el modpack.** Cualquier tier de armadura nuevo entra roto, así
que no se pueden añadir dimensiones ni progresión por encima de netherite.

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
> le dijo *a qué* armaduras aplicarse. Y **EMF no ofrecía forma de expresarla**.

### Por qué no bastaba con un `.properties`

| Condición | Resultado |
|---|---|
| `items.1=<lista vanilla>` | nunca coincide |
| `items.1=any` | nunca coincide |
| `items.1=none` | **siempre** coincide ← prueba de que la lista llega **vacía** |
| `nbt.1.equipment.<slot>.id=raw:...` | funciona, pero lento (336 reglas) y se congela |

**`items=` está roto para capas de armadura.** La prueba definitiva es que `items=none`
coincide *teniendo armadura puesta*.

---

## 🛠️ La solución

Un mod cliente de **53 KB** que arregla los tres eslabones rotos y trae las reglas dentro:

| # | Problema | Solución |
|---|---|---|
| 1 | `items=` llega vacío | añade la propiedad **`armor_item`**, que lee `getItemBySlot(slot)` de la entidad viva — sin NBT, sin caché |
| 2 | ETF solo permite re-evaluar la primera vez | mixin en `PropertiesRandomProvider.entityCanUpdate()` |
| 3 | El layer conserva el modelo del arranque | mixin en `HumanoidArmorLayer.getArmorModel()` |
| 4 | Escribir 48 reglas a mano | **resource pack integrado** en el propio jar |

El punto 3 es el que hace que, **sin este mod, cualquier variante de modelo de armadura no se
actualice hasta pulsar F3+T**. Aplica a todo pack CEM con variantes, no solo a este.

### El detalle que costó el día

`HumanoidRenderState` **no implementa** `EMFEntityRenderState`, así que el `instanceof` fallaba
en silencio. La conversión correcta la expone el propio EMF:

```java
EMFEntityRenderState emfState = EMFEntityRenderState.from(state);
if (emfState != null) emfRoot.doVariantCheck(emfState);
```

---

## 📥 Instalación

**Un solo archivo.** Copia [`mod/armor-cem-compat-1.1.0.jar`](mod/) a tu carpeta `mods/`.

Tu pack de armadura 3D se instala **tal cual, sin modificar**.

Requiere **EMF** y **ETF** (los mismos que ya necesita cualquier pack CEM).

El mod trae dentro un resource pack llamado *"Compat de armadura 3D con mods"* que se activa
solo y se coloca por encima de tu pack. **No hay que copiar ni mover nada.**

### ⚠️ Si la armadura de mods se sigue viendo mal

Comprueba en *Opciones → Paquetes de recursos* que **"Compat de armadura 3D con mods" esté por
encima** de tu pack de armadura, y **reinicia el juego** — recargar no basta, porque EMF no
reconstruye los modelos de armadura en caliente. Viene bien colocado por defecto.

### 🔌 Cuándo desactivar el pack integrado

Se puede desactivar y **el mod sigue funcionando** (la propiedad `armor_item` se mantiene).
Hazlo si:

- **Tu pack de armadura ya trae sus propias reglas** — las nuestras se las pisarían al ir por
  encima. Apágalo y quedan las suyas, que siguen necesitando el mod.
- **Tu pack ya usa variantes propias** (`<modelo>2.jem`) → habría colisión.
- **Quieres escribir tus propias reglas.**

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
  '!'  : niega el patrón — va DELANTE del slot
```

```properties
armor_item.1=head:minecraft:*_helmet head:minecraft:turtle_helmet
armor_item.1=!chest:minecraft:*_chestplate
```

**`minecraft:*_chestplate` excluye cualquier mod por namespace** — incluidos los que se
instalen en el futuro. No hay lista que mantener.

---

## 🔧 Código fuente

En [`codigo-fuente/`](codigo-fuente/), y con repo propio en
[maguet95/armor-cem-compat](https://github.com/maguet95/armor-cem-compat). MIT.
Son dos clases y dos mixins; se lee en cinco minutos y está comentado en español.

---

## 📢 Para compartir (copiar y pegar)

> **Fix: los packs de armadura 3D rompen la armadura de todos los mods**
>
> Si usas un resource pack de armadura 3D (CEM) con EMF y la armadura de cualquier mod se ve
> deformada, con colores raros y huecos, no es cosa tuya ni del mod de armadura.
>
> **Causa:** los modelos CEM se aplican a la capa de armadura de la **entidad**, no al item,
> así que capturan también las armaduras de otros mods, que usan otro mapa de textura. Y EMF
> **no ofrece ninguna condición** para filtrarlas: su propiedad `items` llega vacía en las
> capas de armadura (se demuestra en un renglón — `items=none` coincide con armadura puesta).
>
> **Solución:** un mod de 53 KB que añade la condición que falta (`armor_item`), arregla dos
> cachés que impedían actualizar el modelo al cambiarte de armadura, y trae las reglas ya
> hechas dentro. **Un archivo en `mods/`, cero configuración**, y el pack de armadura se
> instala sin modificar.
>
> Diagnóstico completo, mod y código fuente 👇
> https://github.com/maguet95/sangre-arcana-aportes/tree/main/fixes/just3darmors-modded-armor

---

## 📌 Resumen en una línea

> Los modelos CEM de armadura se aplican a **toda** armadura equipada y EMF no ofrece forma de
> filtrarlos por item (`items=` llega vacío). Se arregla con un mod que añade la propiedad
> `armor_item`, destraba los dos cachés que impedían actualizar el modelo en vivo, y trae las
> reglas integradas.
