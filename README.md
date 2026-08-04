# 🩸 Sangre Arcana — Aportes

Recursos abiertos para la comunidad de **Minecraft**: arreglos de compatibilidad entre
mods, traducciones al español y datapacks.

Todo lo que hay aquí sale de mantener un servidor **Forge 1.20.1** con más de 200 mods
(base **Perfection**). Cuando algo se rompe, lo diagnosticamos hasta la causa real —
leyendo los `.jar` y el bytecode, no adivinando — y publicamos el arreglo con la evidencia
para que nadie más tenga que repetir el trabajo.

**Usa lo que necesites, sin pedir permiso.** Si te sirve, compártelo.

---

## 🔧 Fixes de compatibilidad

| Fix | Problema que resuelve | Mods implicados |
|---|---|---|
| [**Hogskin inobtenible**](fixes/hogskin-butchersdelight/) | Matas hoglins y nunca cae el `Hogskin` → progresión de magia bloqueada (Gold/Diamond Spell Book, Lesser Spell Slot Upgrade) | Iron's Spells 'n Spellbooks + Butcher's Delight |

## 🌎 Traducciones

Traducciones al español de mods que no la traen. *(Aún nada publicado — en camino.)*

## 📦 Datapacks

Datapacks sueltos, no ligados a un fix concreto. *(Aún nada publicado — en camino.)*

---

## ❓ Cómo se instala un datapack

Casi todo lo de aquí son datapacks. Tres formas, elige una:

**Servidor** → copia la carpeta o el `.zip` a `world/datapacks/` y corre `/reload`.
En multijugador **solo hace falta en el servidor**: las recetas se sincronizan solas a
todos los clientes.

**Un jugador solo** → copia a `.minecraft/saves/<tu mundo>/datapacks/`, sal y entra al
mundo (o `/reload`).

**Con OpenLoader** *(si tu pack lo trae, como Perfection)* → copia el `.zip` a
`.minecraft/openloader/data/` y reinicia. Aplica a **todos** tus mundos, presentes y
futuros, sin tocar ningún save.

> ⚠️ Si armas el `.zip` tú mismo en Windows, **no uses `Compress-Archive`**: guarda las
> rutas con `\` y Java no las reconoce como carpetas, así que el datapack carga vacío y
> parece que no funciona. Usa 7-Zip, el clic derecho → "Enviar a → carpeta comprimida", o
> descarga el `.zip` que ya viene aquí.

---

## 🤝 Aportar

¿Encontraste otro choque entre mods, o tienes una traducción? Abre un **issue** o un
**pull request**. Lo único que se pide: **que venga con la causa verificada**, no solo el
parche. Un fix que nadie entiende se rompe en la siguiente actualización.

## 📄 Licencia

[MIT](LICENSE) — úsalo, modifícalo y redistribúyelo libremente, incluso en tu propio
modpack. No hace falta pedir permiso ni dar crédito (aunque se agradece).

Los mods mencionados pertenecen a sus respectivos autores; aquí no se redistribuye
ningún mod, solo datapacks y configuraciones propias.

---

<sub>Mantenido por **Sangre Arcana** · servidor RPG Forge 1.20.1</sub>
