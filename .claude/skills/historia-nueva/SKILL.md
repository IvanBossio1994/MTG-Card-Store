---
name: historia-nueva
description: Convierte un pedido en una historia de usuario. Primero muestra un brief en tabla y espera aprobación; después escribe la HU y la deja lista para ejecutar. Usar cuando se pide una funcionalidad, un cambio o un arreglo nuevo.
user-invocable: false
---

# Crear una historia nueva

El pedido llega como argumento o en el mensaje. Si falta información de negocio, hacé como máximo 2 preguntas, con opciones, antes del brief.

## 1. Brief (sin tocar nada todavía)
Analizá lo justo:
- leé `docs/agentes/estado.md`;
- en `docs/funcional/documento-funcional.md`, leé **solo la sección del módulo** (ubicala con Grep);
- buscá en el código solo lo que el documento no responde. No lances agentes en este paso. Después mostrá:

| Qué entendí | Qué conviene | Qué se quita o no se hace | Orden y agente | Riesgo |
|---|---|---|---|---|
| ... | ... | ... | ... | ... |

- Una fila por tema, frases cortas, sin jerga.
- **Revisá las épicas abiertas** (`docs/agentes/epicas/README.md`): si el pedido sirve al mismo objetivo que una, la HU se suma a esa épica (regla "Épica" de `CLAUDE.md`).
- Si el pedido es grande, proponé partirlo en varias HU y mostralas en la tabla. Esas HU forman una **épica**. Si es una HU suelta que ahora necesita otra relacionada, proponé convertirla en épica.
- **Una HU = un tipo de trabajo** (diseño, backend, frontend, QA, base técnica o bugfix), en el orden de la tabla "Épicas y tipos de historia" de `CLAUDE.md`. Nunca se mezclan. La HU de QA es una sola por épica, al final, con el campo **Cubre** (ej.: `HU-003, HU-004`).
- Si el pedido es una HU que ya está planificada en una épica abierta (`docs/agentes/epicas/`), partí de lo que dice la épica.
- Cerrá con: **"¿Apruebo y armo la historia?"** y **esperá la respuesta**.

## 2. Historia (solo con el OK)
- **Si el pedido se partió en varias HU, primero creá la épica.**
  - Archivo `docs/agentes/epicas/EP-XXX-<tema>.md`, desde `docs/agentes/plantilla-epica.md`, con el objetivo, el caso de uso, el alcance y la tabla de historias. La que se arma ahora va `aprobada`; las demás, `planificada`.
  - Sumá la fila al índice `epicas/README.md`.
  - Si es una sola HU suelta y no se relaciona con ninguna épica abierta, no hace falta épica (campo Épica: `—`).
- Número: el siguiente al último de `docs/agentes/historias/README.md`.
- En el campo **Épica** de la HU poné `EP-XXX` y, en la épica, actualizá el estado de esa HU.
- Creá `docs/agentes/historias/HU-XXX-<tema>.md` desde `docs/agentes/plantilla-historia.md`, con estado `aprobada`. En el campo **Rama** poné `v2/feature/` + el nombre del archivo sin `.md`.
- Criterios de aceptación en Given / When / Then / And, en tercera persona, como en "Cómo se escribe el Gherkin" de `docs/agentes/qa-guia.md`: los del camino feliz y los de error que importan.
- Incluí los locators esperados (HU de diseño o frontend) y el contrato de API (HU de backend), si aplica.
- Agregá la fila al índice `historias/README.md`.
- **Documento funcional:** solo si es la primera HU de la épica que define comportamiento (ver `CLAUDE.md`, ciclo, paso 2). Preguntá *¿documentar caminos felices, alternativos o ambos?* y llamá una vez a `analista-funcional`, con la ruta de la HU y el modo. Deja los flujos `propuesto · HU-XXX` y anota sus IDs en la historia. Si los flujos ya existen en la épica, copiá sus IDs en la HU sin llamarlo.
- Mostrale a la persona a cargo un resumen de 3 líneas y la ruta del archivo.
- Preguntá si arrancar la ejecución ahora. Si dice que sí, seguí con la skill `continuar-historia` para HU-XXX.
