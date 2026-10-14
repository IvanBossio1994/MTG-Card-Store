---
name: analizar-defectos
description: Lee los defectos reportados por QA, los agrupa y prioriza, y propone qué arreglar en una tabla. Con aprobación, crea la historia de bugfix. Usar cuando se pide revisar, analizar o priorizar bugs.
---

# Analizar defectos

1. Leé el índice `docs/agentes/defectos/README.md`. Abrí solo los `DEF` en estado `abierto`, o los que pida la persona a cargo. No lances agentes.
2. Agrupá los que tienen la misma causa probable, por área o pantalla.
3. Mostrá:

| DEF | Qué pasa (simple) | Severidad | Causa probable | Propuesta | Agente |
|---|---|---|---|---|---|

   - **Orden:** primero lo que pierde o corrompe datos del Sheet, después según la severidad.
   - **Propuesta:** arreglar ahora / juntar con otro / postergar / descartar (con el motivo).
4. Preguntá qué arreglar. Con el OK:
   - creá una HU de tipo `bugfix` con `/historia-nueva`, pasándole los `DEF` elegidos; los criterios de aceptación son el resultado esperado de cada `DEF`;
   - marcá esos `DEF` como `en corrección` en el índice.
5. Los descartados se marcan `descartado`, con el motivo en su archivo.
