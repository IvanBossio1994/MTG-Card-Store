# HU-XXX: <título corto>

- **Estado:** aprobada | en desarrollo | en QA (solo HU de QA) | en corrección | aceptada
- **Épica:** EP-XXX (o `—` si es una historia suelta)
- **Tipo:** diseño | backend | frontend | QA | base técnica | bugfix (DEF-XXX) — una HU, un tipo (ver `CLAUDE.md`, "Épicas y tipos de historia")
- **Cubre:** (solo en HU de QA) las HU que prueba, ej.: `HU-003, HU-004`
- **Módulo:** compartido | inventario | precios | reservas | caja | movimientos | importacion | configuracion | google
- **Rama:** `v2/feature/HU-XXX-<tema>` (igual al nombre de este archivo sin `.md`)
- **QA elegido:** (solo en HU de QA) feliz | error | ambos, y el alcance de ejecución
- **Funcional documentado:** felices | alternativos | ambos | no aplica | reusa los de HU-XXX
- **Flujos funcionales:** CFG-F01, CFG-A01… (los completa `analista-funcional` o el líder si se reusan)

## Historia
**Como** <rol: empleado del local / dueño del comercio>
**quiero** <acción>
**para** <beneficio de negocio>

## Descripción
<Contexto en 3 a 6 líneas: situación actual, qué cambia, pantallas o endpoints involucrados, archivos relevantes.>

## Criterios de aceptación
Escritos para que QA los pase directo a Gherkin: palabras clave en inglés, en tercera persona y con el texto visible entre comillas (ver "Cómo se escribe el Gherkin" en `docs/agentes/qa-guia.md`).
**HU de QA:** no copia los CA de las historias que cubre; los referencia (archivo y tag `@HU-XXX` de origen), y QA los lee de ahí para saber qué probar (en el `.feature` los puede reescribir si hace falta). Solo suma los CA propios, como el arnés o la limpieza de datos.

**CA1: <nombre>**
- **Given** <estado inicial, ej.: visualiza el módulo de "Configuración">
- **When** <acción, ej.: hace clic en "Guardar tienda">
- **Then** <resultado observable, ej.: visualiza el mensaje "Tienda guardada.">

**CA2: <nombre>** (sumar los casos de error relevantes)
- **Given** ...
- **When** ...
- **Then** ...

## Locators (`data-testid`) — HU de diseño o frontend
| data-testid | Elemento |
|---|---|
| `<pantalla>-<elemento>` | ... |

## Contrato de API — HU de backend
Resumen; el contrato completo va en `docs/v2/api/<modulo>.md`.

| Método | Ruta | Request | Respuesta OK | Errores |
|---|---|---|---|---|
| POST | `/api/v1/...` | `{ ... }` | 201 `{ ... }` | 400, 409 |

## Fuera de alcance
- ...

## Tareas (en orden)
Normalmente un solo agente, el del tipo de la HU. La HU de QA puede tener dos: `backend-java` (arnés, si falta) y `qa-*`.
**Backend:** 1) contrato y riesgos (lo revisa la persona a cargo) · 2) infraestructura nueva, si hace falta · 3) endpoints y tests (hasta 6 endpoints por tarea). Un agente nuevo por tarea (ver `CLAUDE.md`, ciclo, paso 3).

| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | <agente del tipo> | ... | Diseño: spec y prototipo · Backend: contrato, compila y pasan los tests · Frontend: build, Vitest y locators · QA: veredicto y defectos |

## Consumo
| Agente | Llamada | Tokens | % ventana (inicio → fin) |
|---|---|---|---|
| ... | ... | ... | ... → ... |
| **Total** | | | |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| AAAA-MM-DD | Brief aprobado | |
