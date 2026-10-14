# Catálogo de locators (`data-testid`)

Es un contrato entre UX, que los define, frontend, que los pone, y QA, que los usa. **Un `data-testid` existente no se renombra ni se borra sin avisar al líder.**

## Convención
`<pantalla>-<elemento>[-<id>]`, en minúsculas, con palabras en español separadas por guiones.

- **Pantallas:** `app`, `login`, `inventario`, `busqueda`, `reservas`, `clientes`, `movimientos`, `caja`, `importacion`, `configuracion`, `tutorial`.
- **Prefijos de elemento:**
  - `boton-`
  - `campo-` (inputs)
  - `selector-`
  - `tabla-` y `fila-<id>`
  - `modal-`
  - `mensaje-` (`mensaje-error`, `mensaje-exito`)
  - `contador-`
  - `enlace-`

**Ejemplos:** `busqueda-campo-texto`, `inventario-fila-123`, `reservas-boton-entregar`, `reservas-modal-confirmar`, `app-mensaje-error`.

## Catálogo
| data-testid | Pantalla | Elemento | Desde (HU) |
|---|---|---|---|
