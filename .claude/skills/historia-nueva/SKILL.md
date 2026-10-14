---
name: historia-nueva
description: Convierte un pedido en una historia de usuario. Primero muestra un brief en tabla y espera aprobación; después escribe la HU y la deja lista para ejecutar. Usar cuando se pide una funcionalidad, un cambio o un arreglo nuevo.
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
- Si el pedido es grande, proponé partirlo en varias HU y mostralas en la tabla.
- Cerrá con: **"¿Apruebo y armo la historia?"** y **esperá la respuesta**.

## 2. Historia (solo con el OK)
- Número: el siguiente al último de `docs/agentes/historias/README.md`.
- Creá `docs/agentes/historias/HU-XXX-<tema>.md` desde `docs/agentes/plantilla-historia.md`, con estado `aprobada`.
- Criterios de aceptación en Dado / Cuando / Entonces: los del camino feliz y los de error que importan.
- Incluí los locators esperados y el contrato de API si aplica.
- Agregá la fila al índice `historias/README.md`.
- **Documento funcional:** preguntá *¿documentar caminos felices, alternativos o ambos?* y llamá una vez a `analista-funcional`, con la ruta de la HU y el modo. Deja los flujos `propuesto · HU-XXX` y anota sus IDs en la historia.
- Mostrale a la persona a cargo un resumen de 3 líneas y la ruta del archivo.
- Preguntá si arrancar la ejecución ahora. Si dice que sí, seguí con `/continuar-historia HU-XXX`.
