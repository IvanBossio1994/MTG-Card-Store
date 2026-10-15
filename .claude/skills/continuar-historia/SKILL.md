---
name: continuar-historia
description: Retoma una historia de usuario (o la próxima planificada de la épica abierta) desde su estado guardado y ejecuta el siguiente paso del ciclo (desarrollo → QA → corrección → cierre) con el agente que corresponda. Usar para avanzar o retomar una HU.
user-invocable: false
---

# Continuar una historia

1. **Ubicar la historia:** la indicada en el argumento, o la que no esté `aceptada` en `docs/agentes/historias/README.md`. Si hay varias, preguntá cuál.
   - **Si no hay ninguna abierta**, tomá la próxima `planificada` de la épica abierta (`docs/agentes/epicas/`).
   - **Si la historia está `planificada` en una épica pero todavía no tiene archivo**, aplicá la skill `historia-nueva` con lo que dice la épica (brief, OK y armado de la HU). Con el OK, seguí acá desde `aprobada`.
2. **Leer solo su archivo** (o la fila de la épica, si recién se arma), el estado y el registro. Si la HU toca el Sheet o Google, leé también `docs/agentes/estado.md`, sección Google. No vuelvas a analizar el proyecto.
3. **Ejecutar el siguiente paso** según el estado:

| Estado | Siguiente paso |
|---|---|
| `aprobada` | Crear desde `v2/dev` la rama `v2/feature/HU-XXX-<tema>`, con el mismo nombre que el archivo de la historia sin `.md`, usando el Git que indica `CLAUDE.local.md`. Llamar al agente del tipo de la HU: diseño → `ux-disenador`; backend o base técnica → `backend-java`; frontend → `frontend`; QA → `backend-java` si falta el arnés, y si no, directo al paso `en QA`. Pasar a `en desarrollo`. |
| `en desarrollo` | Llamar al siguiente agente pendiente de la tabla de tareas. Cuando todos entregaron, el líder verifica lo que corresponda (prototipo en Chrome, app levantada o pedidos HTTP). Si es la **HU de QA**, pasar a `en QA`; si no, ir al cierre. |
| `en QA` | **Preguntar:** ¿camino feliz, casos de error o ambos? Y también: ¿ejecutar los escenarios de las HU que cubre (por defecto, `@HU-003 or @HU-004`, por ejemplo), esos más el smoke (`@critico`), o la regresión completa (`@regresion`)? Llamar solo a `qa-camino-feliz` y/o `qa-casos-error`, indicando el alcance en el prompt. Si hay `DEF` abiertos, pasar a `en corrección`; si no, ir al cierre. |
| `en corrección` | Llamar al agente del área de cada `DEF`, que arregla solo eso. Después, retest acotado con el mismo QA (lo que falló más `@critico`). Como máximo **2 vueltas**; si sigue fallando, frenar y consultar a la persona a cargo. |
| Cierre | Mostrar la tabla `Hecho | Cómo probarlo | Pendiente`, y después la tabla de **consumo de tokens** de la sección `Consumo` de la historia (`Agente | Llamadas | Tokens`, con fila de total) y 1 o 2 líneas sobre dónde se gastó más y qué se puede mejorar. Pedir la **aceptación final**. Con el OK:
- estado `aceptada`;
- actualizar el índice;
- si la HU pertenece a una épica, actualizar en la épica su estado y el progreso (`N de M aceptadas`). Si todas quedaron aceptadas, la épica pasa a `cerrada`;
- en `docs/funcional/documento-funcional.md`, pasar a `vigente` los flujos que esta HU deja usables (`propuesto · HU-XXX` o `diseñado · … → HU-XXX`) y subir la versión (MENOR si es funcionalidad nueva, PARCHE si es un bugfix), con una fila en el historial. Si la HU no cambia flujos (base técnica, QA), no se toca la versión;
- actualizar `docs/agentes/estado.md`.

Si durante el desarrollo cambió el alcance o QA encontró reglas nuevas, llamá antes a `analista-funcional` para ajustar los flujos. No hacer commit salvo que lo pida. |

**Cómo llamar a cada agente:**
- Un prompt corto: la ruta de la historia, qué tarea de la tabla le toca y, si es una corrección, qué `DEF-XXX`.
- No copies la historia en el prompt; el agente la lee.
- Para pedir cambios sobre algo ya entregado, lanzá un agente **nuevo** que lea los archivos entregados, en lugar de retomar el anterior con `SendMessage`, que arrastra todo su historial. Juntá todos los cambios en una sola llamada. Si el cambio es chico (un par de líneas en un archivo), hacelo vos.

**Después de cada paso:**
- Anotar una línea en el **Registro** de la historia.
- Sumar los tokens del agente a la sección **Consumo** de la historia. El dato sale del aviso de fin del agente (`subagent_tokens`) y se anota por llamada, porque retomar un agente que ya trabajó vuelve a cargar todo lo que hizo antes y cada llamada cuesta más que la anterior. El consumo del líder no se puede medir con exactitud, así que no se anota.
- Contarle a la persona a cargo, en 1 o 2 líneas, qué se hizo y cuál es el próximo paso.
- Preguntar si sigue. Se avanza **un paso por confirmación**, salvo que la persona a cargo diga "seguí hasta el cierre" o algo similar.
