---
name: continuar-historia
description: Retoma una historia de usuario desde su estado guardado y ejecuta el siguiente paso del ciclo (UX → backend → frontend → QA → corrección → cierre) con los agentes que correspondan. Usar para avanzar o retomar una HU.
---

# Continuar una historia

1. **Ubicar la historia:** la indicada en el argumento, o la que no esté `aceptada` en `docs/agentes/historias/README.md`. Si hay varias, preguntá cuál.
2. **Leer solo su archivo**, el estado y el registro. No vuelvas a analizar el proyecto.
3. **Ejecutar el siguiente paso** según el estado:

| Estado | Siguiente paso |
|---|---|
| `aprobada` | Crear la rama `feature/HU-XXX-<tema>` (`E:\Git\cmd\git.exe`). Llamar a `ux-disenador` si hay cambios visibles; si no, a `backend-java`. Pasar a `en desarrollo`. |
| `en desarrollo` | Llamar al siguiente agente pendiente de la tabla de tareas (backend → frontend). Cuando todos entregaron, pasar a `en QA`. |
| `en QA` | **Preguntar:** ¿camino feliz, casos de error o ambos? Y también: ¿ejecutar solo esta historia (`@HU-XXX`, por defecto), esta historia más el smoke (`@critico`), o la regresión completa (`@regresion`)? Llamar solo a `qa-camino-feliz` y/o `qa-casos-error`, indicando el alcance en el prompt. Si hay `DEF` abiertos, pasar a `en corrección`; si no, ir al cierre. |
| `en corrección` | Llamar al agente del área de cada `DEF`, que arregla solo eso. Después, retest acotado con el mismo QA (lo que falló más `@critico`). Como máximo **2 vueltas**; si sigue fallando, frenar y consultar a la persona a cargo. |
| Cierre | Mostrar la tabla `Hecho | Cómo probarlo | Pendiente` y pedir la **aceptación final**. Con el OK:
- estado `aceptada`;
- actualizar el índice;
- en `docs/funcional/documento-funcional.md`, pasar los flujos de la HU de `propuesto · HU-XXX` a `vigente` y subir la versión (MENOR si es funcionalidad nueva, PARCHE si es un bugfix), con una fila en el historial;
- actualizar `docs/agentes/estado.md`.

Si durante el desarrollo cambió el alcance o QA encontró reglas nuevas, llamá antes a `analista-funcional` para ajustar los flujos. No hacer commit salvo que lo pida. |

**Cómo llamar a cada agente:**
- Un prompt corto: la ruta de la historia, qué tarea de la tabla le toca y, si es una corrección, qué `DEF-XXX`.
- No copies la historia en el prompt; el agente la lee.

**Después de cada paso:**
- Anotar una línea en el **Registro** de la historia.
- Contarle a la persona a cargo, en 1 o 2 líneas, qué se hizo y cuál es el próximo paso.
- Preguntar si sigue. Se avanza **un paso por confirmación**, salvo que la persona a cargo diga "seguí hasta QA" o algo similar.
