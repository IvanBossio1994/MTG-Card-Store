---
name: agente-lider
description: Menú del líder. Muestra las opciones del equipo de agentes (historia nueva, continuar historia, analizar defectos, consulta rápida) para elegir con un número y ejecuta la que se elija. Usar cuando se escribe /agente-lider o se pide ver qué se puede hacer.
---

# Menú del líder

Si junto al comando ya vino un pedido claro (por ejemplo, `/agente-lider continuar HU-002`), saltá el menú y aplicá directamente la opción que corresponda.

Si no, mostrá el menú con la herramienta de preguntas (`AskUserQuestion`), en una sola pregunta: *"¿Qué querés hacer?"*, con estas opciones y en este orden:

**Historia nueva**: convertir un pedido en una historia de usuario.
**Continuar historia**: retomar la historia en curso y hacer el siguiente paso.
**Analizar defectos**: revisar los bugs que reportó QA y priorizarlos.
**Consulta rápida**: una pregunta o un cambio chico, sin agentes.

Antes de mostrar el menú, leé `docs/agentes/historias/README.md` y `docs/agentes/epicas/README.md`. En la descripción de la opción 2, nombrá lo que sigue:
- si hay una historia que no esté `aceptada`, esa (ej.: "Sigue HU-001 · en QA");
- si no hay ninguna, la próxima `planificada` de la épica abierta (ej.: "Sigue HU-003 · planificada en EP-001").

Según lo que se elija:

| Opción | Qué hacer |
|---|---|
| 1 | Si no vino el pedido, preguntá en una línea qué se necesita. Después aplicá la skill `historia-nueva`. |
| 2 | Aplicá la skill `continuar-historia`. |
| 3 | Aplicá la skill `analizar-defectos`. |
| 4 | Preguntá cuál es la consulta y respondela directamente, sin llamar agentes. |
