# HU-010: Guía de estilo de código (back y front)

- **Estado:** aceptada
- **Épica:** EP-002
- **Tipo:** base técnica
- **Módulo:** compartido
- **Rama:** `v2/feature/HU-010-guia-estilo-codigo`
- **Funcional documentado:** no aplica (base técnica)
- **Flujos funcionales:** —

## Historia
**Como** dueña del producto
**quiero** una guía única con un ejemplo corto por tipo de archivo
**para** que cada agente escriba el código igual, más corto y moderno, sin que haya que repetirle las reglas.

## Descripción
Hoy las reglas de estilo están repartidas en `backend-java.md`, `frontend.md`, `estructura-carpetas.md` y `CLAUDE.md`, y el código de Configuración tiene los hallazgos de `docs/agentes/investigacion-estilo-codigo.md` (B1 a B5, F1 a F4, T1 a T5, R1 y R2).
Esta HU escribe `docs/agentes/estilo-codigo.md` con la **forma a la que se quiere llegar** (el código actual se adapta en HU-012 y HU-013) y hace que los agentes `backend-java` y `frontend` la lean en lugar de repetir reglas.
No toca código. Las herramientas que la hacen cumplir llegan en HU-011, que ajusta la guía si algo no se puede aplicar tal cual.

Contenido de la guía (cada sección: reglas en 3 a 6 líneas + un ejemplo corto de código real del proyecto):
- **Común:** nombres en español y descriptivos, javadoc/TSDoc con ejemplo, imports (Java: `*` desde 5 clases del mismo paquete y en estáticos de tests; TS: siempre con nombre), qué hacen las herramientas.
- **Back:** controller (lógica HTTP sola; documentación OpenAPI en la interfaz `<Modulo>Api`), service (inyección por constructor, mensajes en un `enum` por módulo, `Optional` solo como retorno), records (en su archivo, fábricas con nombre), repositorio, errores (`ProblemDetail`), test unitario (dobles en memoria, comparar records enteros) y test de API (`MockMvcTester`, JSON entero en text block, datos de prueba compartidos por módulo).
- **Front:** `<modulo>Api.ts`, datos del servidor con TanStack Query (`useQuery`/`useMutation`), componentes chicos (una fila o bloque por archivo), formularios (validar solo vacíos en el front; el resto, el back), textos y plurales desde `textos.md`, test de componente (`user-event` + MSW).
- **QA:** sección reservada, "pendiente del ejemplo de la persona a cargo".

## Criterios de aceptación
**CA1: La guía cubre cada tipo de archivo**
- **Given** existe el archivo `docs/agentes/estilo-codigo.md`
- **When** la persona a cargo lo abre
- **Then** visualiza una sección común, una de back y una de front, con un ejemplo de código por tipo de archivo
- **And** visualiza la sección de QA marcada como pendiente

**CA2: Las reglas son las actuales**
- **Given** la guía propone un patrón o una librería
- **When** se revisa contra la documentación oficial vigente (Java 21, Spring Boot 3.5, React 19, TypeScript 5, TanStack Query 5)
- **Then** el patrón no está deprecado
- **And** las decisiones de EP-002 (sin React Hook Form ni Zod; Spotless no arma los `*`) están reflejadas

**CA3: Los agentes apuntan a la guía**
- **Given** la guía está escrita
- **When** se abren `.claude/agents/backend-java.md` y `.claude/agents/frontend.md`
- **Then** cada uno indica leer `docs/agentes/estilo-codigo.md` antes de programar
- **And** ya no repite reglas de estilo que estén en la guía (lo que se saca se muestra antes en una tabla y la persona a cargo lo aprueba)

**CA4: Un solo lugar por regla**
- **Given** una regla de estilo está en la guía
- **When** se busca en `CLAUDE.md`, `estructura-carpetas.md` y los agentes
- **Then** no aparece con otra redacción que la contradiga

## Fuera de alcance
- Instalar herramientas o librerías (HU-011 y HU-013).
- Cambiar código de la app o de los tests (HU-012 y HU-013).
- Estilo de QA y cambios en los agentes `qa-*` o en `qa-guia.md`.

## Tareas (en orden)
| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | líder | Escribir `docs/agentes/estilo-codigo.md` a partir de la investigación, verificando lo que haga falta en la documentación oficial | CA1 y CA2 |
| 2 | líder | Mostrar en una tabla qué reglas se sacan de `backend-java.md` y `frontend.md` (y si algo de `CLAUDE.md` o `estructura-carpetas.md` choca); con el OK, editarlos | CA3 y CA4 |

## Consumo
| Agente | Llamada | Tokens | % ventana (inicio → fin) |
|---|---|---|---|
| líder | tareas 1 y 2 + regla de épicas | sin agentes | 3 → 8 |
| **Total** | | 0 de agentes | ~5 puntos de ventana, ~1 de semana |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-15 | Brief aprobado | Épica EP-002 con 4 HU; HU-010 aprobada. Estimación ~3-5 puntos de ventana |
| 2026-10-15 | Tarea 1 (líder) | `docs/agentes/estilo-codigo.md` escrito: común, back (2.1 a 2.7), front (3.1 a 3.5), herramientas y QA reservado. Ejemplos con la API real del proyecto. Anotado para HU-013: MSW con rutas relativas en Node |
| 2026-10-15 | Tarea 2 (líder) | Tabla aprobada y aplicada: `backend-java.md` y `frontend.md` apuntan a la guía (sin repetir estilo); `estructura-carpetas.md` (records sin `dto/`, `Optional` solo retorno, TanStack Query) y `roadmap-v2.md` (sin `dto/`). `CLAUDE.md` sin cambios de estilo |
| 2026-10-15 | Extra aprobado | Regla de épicas ampliada en `CLAUDE.md` y `historia-nueva`; el estilo de QA entra como HU-014 en EP-002. Pasa a cierre |
| 2026-10-15 | **Aceptada** | La dueña la acepta; commit, merge a `v2/dev` y push |
