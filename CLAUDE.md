# TCG Inventory (MTG-Card-Store)

App local para administrar stock y reservas de cartas MTG. Usa Google Sheets como base de datos y precios de Card Kingdom.
Stack: Java 21, Spring Boot 3.5 y Thymeleaf. También hay un launcher de escritorio para Windows (`desktop-shell/`, en C#).

## Compilar y testear
- Maven: `.tools\apache-maven-3.9.16\bin\mvn.cmd` (no hay wrapper).
- Requiere JDK 21.
- Compilar: `mvn.cmd -q compile` · Tests: `mvn.cmd -q test`

## Paquetes (`com.tcg.bot`)
- `controller`: `DashboardController`, controlador web único (~8000 líneas). Leerlo por rangos y ubicarse con búsquedas, nunca entero.
- `service`: integración con Google Sheets, OAuth y Card Kingdom, y configuración de precios y comercio.
- `model`: entidades de inventario, reservas, clientes, movimientos y caja.
- `dto`: respuestas de Card Kingdom y filtros/consultas de búsqueda.
- `config`: configuración de Google Sheets.
- Raíz: `TcgBotApplication` (Spring) y `DesktopLauncher` (abre la app en modo ventana).

## Reglas
- Responder siempre en español.
- No hacer commit ni push sin que se pida explícitamente.
- No tocar `data/`: tiene credenciales, el token OAuth y datos locales del comercio.
- En los tests, los nombres de cliente como "Codex Live" son datos de prueba, no referencias a la IA.
