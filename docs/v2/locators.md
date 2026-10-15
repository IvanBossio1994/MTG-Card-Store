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

**Qué lleva locator:** solo botones, campos (inputs, selectores, checkboxes) y los textos que QA necesita validar (mensajes y datos visibles). Contenedores, bloques, títulos, íconos y decoración no llevan.

**Ejemplos:** `busqueda-campo-texto`, `inventario-fila-123`, `reservas-boton-entregar`, `reservas-modal-confirmar`, `app-mensaje-error`.

## Catálogo
| data-testid | Pantalla | Elemento | Desde (HU) |
|---|---|---|---|
| `configuracion-boton-reintentar` | configuracion | Reintentar la carga | HU-001 |
| `configuracion-campo-nombre-tienda` | configuracion | Nombre de la tienda | HU-001 |
| `configuracion-boton-subir-logo` | configuracion | Subir logo | HU-001 |
| `configuracion-boton-quitar-logo` | configuracion | Quitar logo | HU-001 |
| `configuracion-boton-guardar-tienda` | configuracion | Guardar bloque tienda | HU-001 |
| `configuracion-campo-sheet` | configuracion | Link o ID del Sheet | HU-001 |
| `configuracion-campo-pestana-inventario` | configuracion | Nombre de la pestaña del inventario | HU-001 |
| `configuracion-boton-guardar-sheet` | configuracion | Guardar bloque inventario | HU-001 |
| `configuracion-campo-cotizacion` | configuracion | Cotización ARS por USD | HU-001 |
| `configuracion-campo-redondeo` | configuracion | Múltiplo de redondeo | HU-001 |
| `configuracion-campo-bulk-common` | configuracion | Bulk common | HU-001 |
| `configuracion-campo-bulk-uncommon` | configuracion | Bulk uncommon | HU-001 |
| `configuracion-campo-bulk-rare` | configuracion | Bulk rare | HU-001 |
| `configuracion-campo-bulk-mythic` | configuracion | Bulk mythic | HU-001 |
| `configuracion-campo-bulk-tierra-basica` | configuracion | Bulk tierra básica | HU-001 |
| `configuracion-campo-carpeta-cache` | configuracion | Carpeta de cache de Card Kingdom | HU-001 |
| `configuracion-boton-guardar-precios` | configuracion | Guardar bloque precios | HU-001 |
| `configuracion-campo-pesos-por-punto` | configuracion | Pesos por punto | HU-001 |
| `configuracion-boton-guardar-puntos` | configuracion | Guardar bloque puntos | HU-001 |
| `configuracion-mensaje-exito-<bloque>` | configuracion | Éxito junto al bloque | HU-001 |
| `configuracion-mensaje-aviso-<bloque>` | configuracion | Aviso no bloqueante (pestaña renombrada o creada, falla de Card Kingdom) | HU-001 |
| `configuracion-mensaje-error-<bloque>` | configuracion | Error de todo el bloque (sin permiso, sesión vencida, 5xx) | HU-001 |
| `configuracion-mensaje-error-<campo>` | configuracion | Error junto a un campo (`nombre-tienda`, `logo`, `sheet`, `pestana-inventario`, `cotizacion`, `redondeo`, `bulk-<rareza>`, `carpeta-cache`, `pesos-por-punto`) | HU-001 |
| `configuracion-texto-gmail-conectado` | configuracion | "Conectado como ..." en el bloque Inventario | HU-001 |
| `configuracion-texto-ejemplo-puntos` | configuracion | Ejemplo en vivo: "Una compra de $25.000 suma N puntos." | HU-001 |
| `configuracion-boton-seguir-editando` | configuracion | Cerrar el modal y seguir editando | HU-001 |
| `configuracion-boton-salir-igual` | configuracion | Salir descartando los cambios | HU-001 |
| `configuracion-enlace-iniciar-sesion-google` | configuracion | Volver a iniciar sesión con Google (dentro del error de sesión vencida) | HU-001 |
| `app-enlace-inventario` | app | Menú lateral: Inventario | HU-001 |
| `app-enlace-reservas` | app | Menú lateral: Reservas | HU-001 |
| `app-enlace-caja` | app | Menú lateral: Caja | HU-001 |
| `app-enlace-movimientos` | app | Menú lateral: Movimientos | HU-001 |
| `app-enlace-configuracion` | app | Menú lateral: Configuración (activo, `aria-current="page"`) | HU-001 |
