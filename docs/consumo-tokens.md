# Registro de consumo de tokens

Fuente: `/usage` (ventana de 5 h y semana). Una fila por checkpoint.

## Calibración (actualizar cuando haya datos)
- Ratio semana/ventana: cuántos puntos de la semana consume 1 punto de la ventana. Pendiente: medir Δsemana ÷ Δventana en el primer bloque de trabajo después del reinicio del 15/10 a las 16:09.
- Estimación previa (gruesa, repartiendo el 58% semanal según los tokens por día de `/stats`): el 14/10 consumió ~6% de la semana y el 15/10 ~51%. Si el 15/10 fue una sola ventana (11:09–16:09), una ventana completa a ese ritmo equivale a ~50% de la semana.
- Ritmo del 15/10: la ventana llegó a 99% en ~4,5 h (~22 puntos por hora), con 98% de los tokens en Opus, sesiones con muchos subagentes y contexto de más de 150k la mayor parte del tiempo.

## Mediciones

| Fecha y hora | Ventana % | Semana % | Tarea / momento | Notas |
|---|---|---|---|---|
| 15/10 15:43 | 99 | 58 | Fin de ventana (historia en curso) | Sesión IDE: $38.71, ~100M tokens de ~493M del día |
| 15/10 16:32 | 0 | 58 | Reinicio de ventana | Punto de partida |
| 15/10 (antes de HU-005 "Traer cartas de") | 5 | 59 | Inicio: ux-disenador | Presupuesto del día: 41% ÷ ~4,5 días ≈ 9 puntos de la semana |
| 15/10 (fin de HU-005 "Traer cartas de") | 12 | 60 | Fin: ux-disenador (69k) + CFG-F02 por el líder + revisión en Chrome | **Tarea de diseño chica ≈ 7 puntos de ventana, 1 de semana** (primera medición de ratio: ~0,15 semana por punto de ventana) |
