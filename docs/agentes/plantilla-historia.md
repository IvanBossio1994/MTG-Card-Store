# HU-XXX: <título corto>

- **Estado:** borrador | aprobada | en desarrollo | en QA | en corrección | aceptada
- **Tipo:** funcionalidad | bugfix (DEF-XXX) | mejora técnica
- **Módulo:** inventario | precios | reservas | caja | movimientos | importacion | configuracion | google
- **Rama:** `feature/HU-XXX-<tema>`
- **QA elegido:** feliz | error | ambos (lo completa el líder cuando llega a QA)
- **Funcional documentado:** felices | alternativos | ambos
- **Flujos funcionales:** INV-F0X, INV-A0X… (los completa `analista-funcional`)

## Historia
**Como** <rol: empleado del local / dueño del comercio>
**quiero** <acción>
**para** <beneficio de negocio>

## Descripción
<Contexto en 3 a 6 líneas: situación actual, qué cambia, pantallas o endpoints involucrados, archivos relevantes.>

## Criterios de aceptación
Escritos para que QA los pase directo a Gherkin.

**CA1: <nombre>**
- **Dado** <estado inicial>
- **Cuando** <acción>
- **Entonces** <resultado observable>

**CA2: <nombre>** (sumar los casos de error relevantes)
- **Dado** ...
- **Cuando** ...
- **Entonces** ...

## Locators (`data-testid`)
| data-testid | Elemento |
|---|---|
| `<pantalla>-<elemento>` | ... |

## Contrato de API (si aplica)
| Método | Ruta | Request | Respuesta OK | Errores |
|---|---|---|---|---|
| POST | `/api/v1/...` | `{ ... }` | 201 `{ ... }` | 400, 409 |

## Fuera de alcance
- ...

## Tareas por agente (en orden)
| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | ux-disenador | ... (solo si hay cambios visibles) | Spec en `docs/v2/ux/` |
| 2 | backend-java | ... | Compila y pasan los tests unitarios y de API |
| 3 | frontend | ... | Build y tests OK, locators puestos |
| 4 | qa-* | Escenarios según el QA elegido | Veredicto y defectos reportados |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| AAAA-MM-DD | Brief aprobado | |
