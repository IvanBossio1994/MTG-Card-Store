# DEF-001: Con un link del Sheet con `/u/0/` no se carga "Traer cartas de"
- **HU de QA:** — (lo encontró el líder en la investigación de estilo, F3 de `docs/agentes/investigacion-estilo-codigo.md`) · **Funcionalidad de:** HU-007 · **Escenario:** [Configuración] conectar el Sheet con "Traer cartas de" · **Tipo:** error
- **Severidad:** alta · **Área:** frontend
- **Estado:** abierto (se arregla en HU-013, EP-002)
## Pasos para reproducir
1. Abrir Configuración en `/app`.
2. En el bloque del Sheet, pegar un link del tipo `https://docs.google.com/spreadsheets/u/0/d/<id>/edit` (es el que da Google con varias cuentas de Gmail abiertas).
3. Salir del campo.
## Resultado esperado
Se carga la lista "Traer cartas de" con las pestañas de ese Sheet, igual que con `…/spreadsheets/d/<id>/edit`.
## Resultado obtenido
La lista no se carga. Guardar sí funciona, porque el back acepta ese formato. Con el link sin `/u/0/` o con el ID solo, anda bien.
## Evidencia
- Causa: el front y el back validan el link con patrones distintos.
  - Front: `frontend/src/modulos/configuracion/componentes/BloqueSheet.tsx`, `FORMATO_LINK`, usado en `alSalirDelLink`. No acepta `/u/<n>/` y acepta IDs cortos.
  - Back: `ConfiguracionService`, patrón del link. Acepta `/u/<n>/` y exige un ID de 20 caracteres o más.
- Prueba de los dos patrones (líder, 2026-10-15): `/u/0/d/<id>` → front no, back sí; `/d/abc` → front sí, back no.
- Arreglo propuesto: que el front valide solo que no esté vacío y deje decidir al back (un solo patrón). Test de Vitest con un link `/u/0/` que carga la lista.
