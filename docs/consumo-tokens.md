# Registro de consumo de tokens

Fuente: `/usage` (ventana de 5 h y semana). Una fila por checkpoint.

## Calibración (actualizar cuando haya datos)
- Ratio semana/ventana: cuántos puntos de la semana consume 1 punto de la ventana. Pendiente: medir Δsemana ÷ Δventana en el primer bloque de trabajo después del reinicio del 15/10 a las 16:09.
- Estimación previa (gruesa, repartiendo el 58% semanal según los tokens por día de `/stats`): el 14/10 consumió ~6% de la semana y el 15/10 ~51%. Si el 15/10 fue una sola ventana (11:09–16:09), una ventana completa a ese ritmo equivale a ~50% de la semana.
- Ritmo del 15/10: la ventana llegó a 99% en ~4,5 h (~22 puntos por hora), con 98% de los tokens en Opus, sesiones con muchos subagentes y contexto de más de 150k la mayor parte del tiempo.

## Costo por tipo de agente
Se mide **una vez por tipo**: `/usage` al lanzar la primera tarea de ese agente y al terminarla (incluye lo que hace el líder alrededor). Sirve para estimar las tareas siguientes del mismo tipo. Cuando un tipo ya tiene dato, se sigue anotando en "Mediciones" solo en los checkpoints normales.

| Agente | Tarea medida | Tokens del agente | Puntos de ventana | Puntos de semana |
|---|---|---|---|---|
| `ux-disenador` | HU-005, vuelta "Traer cartas de" (chica) | 69k | 7 | 1 |
| `backend-java` | HU-006, tarea 1 (contrato y riesgos) | 78k | 6 | 1 |
| `backend-java` | HU-006, tarea 2 (programar, 3 endpoints + textos + tests) | 226k | 17 | 2 |
| `frontend` | HU-007, tarea 1 (textos de toda la pantalla + bloque del Sheet + 3 endpoints + Vitest) | 232k | 16 | 2 |
| líder | HU-007, verificación en Chrome con la app levantada (+ 1 arreglo chico y `mvn test`) | — | 3 | 1 |
| `analista-funcional` | próxima llamada | pendiente | pendiente | pendiente |
| `backend-java` | HU-008, tarea 1 (arnés E2E desde cero + smoke) | 157k | 14 | 2 |
| `qa-camino-feliz` | HU-008, tarea 2 (11 escenarios + ajuste por el límite de Google) | 161k (Sonnet) | 7 | 1 |
| `qa-casos-error` | próxima llamada | pendiente | pendiente | pendiente |

## Mediciones

| Fecha y hora | Ventana % | Semana % | Tarea / momento | Notas |
|---|---|---|---|---|
| 15/10 15:43 | 99 | 58 | Fin de ventana (historia en curso) | Sesión IDE: $38.71, ~100M tokens de ~493M del día |
| 15/10 16:32 | 0 | 58 | Reinicio de ventana | Punto de partida |
| 15/10 (antes de HU-005 "Traer cartas de") | 5 | 59 | Inicio: ux-disenador | Presupuesto del día: 41% ÷ ~4,5 días ≈ 9 puntos de la semana |
| 15/10 (fin de HU-005 "Traer cartas de") | 12 | 60 | Fin: ux-disenador (69k) + CFG-F02 por el líder + revisión en Chrome | **Tarea de diseño chica ≈ 7 puntos de ventana, 1 de semana** (primera medición de ratio: ~0,15 semana por punto de ventana) |
| 15/10 (antes de HU-006) | 13 | 60 | Inicio de sesión: brief de HU-006 | Punto de partida para medir `backend-java` |
| 15/10 (fin de HU-006 tarea 1) | 19 | 61 | Fin: backend-java contrato (78k) + armado de la HU | **Contrato de backend ≈ 6 puntos de ventana, 1 de semana**. Inicio de la tarea 2 |
| 15/10 (fin de HU-006 tarea 2) | 36 | 63 | Fin: backend-java programación (226k) + test del líder | **Programar backend (3 endpoints) ≈ 17 puntos de ventana, 2 de semana** |
| 15/10 (antes de HU-007) | 39 | 63 | Inicio de sesión: brief de HU-007 | Punto de partida para medir `frontend` |
| 15/10 (fin de HU-007 tarea 1) | 55 | 65 | Fin: frontend (232k) + armado de la HU | **Programar frontend (1 bloque grande + textos) ≈ 16 puntos de ventana, 2 de semana**. Inicio de la verificación del líder |
| 15/10 (fin de la verificación de HU-007) | 58 | 66 | Fin: líder en Chrome con la app levantada | **Verificación del líder en Chrome ≈ 3 puntos de ventana, 1 de semana**. Día: 58 → 66 = 8 puntos (presupuesto del día alcanzado) |
| 15/10 (antes de HU-008) | 60 | 66 | Inicio de sesión: brief de HU-008 (QA E2E) | Punto de partida para medir el arnés (`backend-java`) |
| 15/10 (fin de HU-008 tarea 1) | 74 | 68 | Fin: backend-java arnés E2E (157k) + smoke del líder | **Arnés E2E desde cero ≈ 14 puntos de ventana, 2 de semana**. Inicio de `qa-camino-feliz` |
| 15/10 (fin de HU-008 tarea 2) | 81 | 69 | Fin: qa-camino-feliz (161k, Sonnet) + ajuste de `data-testid` y verificación del líder | **QA camino feliz (11 escenarios) ≈ 7 puntos de ventana, 1 de semana**. Se cierra HU-008; casos de error para otra ventana |
| 15/10 (fin de la revisión de Superpowers) | 83 | 69 | Fin: líder sin agentes (1 lectura web + edición de reglas de agentes) | **Investigar un plugin y adaptar reglas sin agentes ≈ 2 puntos de ventana, 0 de semana** |
