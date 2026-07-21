# Operaciones y flujo tecnico

Este documento resume como funciona la aplicacion que arranca desde
`TcgBotApplication`. El objetivo no es repetir cada endpoint, sino dejar claro
que operaciones soporta el sistema y como viajan los datos entre pantalla,
servicios externos y Google Sheets.

## Punto de entrada

`src/main/java/com/tcg/bot/TcgBotApplication.java` es el arranque Spring Boot:

- `@SpringBootApplication` carga controladores, servicios, filtros, plantillas y
  configuracion.
- `@EnableScheduling` habilita tareas programadas, hoy usada para sincronizar
  inventario automaticamente.
- `main` delega el arranque a `SpringApplication.run`.

En modo desktop, `DesktopLauncher` envuelve ese mismo arranque: elige un puerto
local libre, define `app.storage-dir`, levanta Spring y abre el navegador en modo
app cuando puede.

## Proposito funcional

La aplicacion administra una tienda de cartas MTG usando Google Sheets como
base operativa y Card Kingdom como fuente de precios/productos.

Desde la interfaz local se puede:

- Configurar tienda, Google Sheet, pestana de inventario, logo, cache de Card
  Kingdom y WhatsApp.
- Conectar Google via OAuth para leer y escribir el spreadsheet.
- Buscar cartas en la pricelist de Card Kingdom.
- Consultar stock local cruzando productos CK con filas del Sheet.
- Agregar cartas al inventario o modificar cantidades.
- Sincronizar precios locales desde CK aplicando la regla de conversion.
- Importar listas de cartas y convertirlas en altas o incrementos de stock.
- Registrar movimientos de entrada/salida y ventas de caja.
- Crear, entregar, liberar, reprogramar y eliminar reservas.
- Recibir pedidos por WhatsApp cuando el canal esta configurado.

## Datos persistidos

La aplicacion no usa una base SQL. Usa dos lugares de persistencia:

- Carpeta local `app.storage-dir`: configuracion local, logo, cache CK y tokens
  OAuth de Google. Por defecto es `./data` en desarrollo o `%APPDATA%/TCG
  Inventory/data` en la app de Windows.
- Google Sheets: fuente principal de negocio. La app crea o normaliza pestanas
  segun haga falta:
  - `Inventario` o la pestana configurada.
  - `Movimientos`.
  - `Caja`.
  - `Reservas`.
  - `Clientes`.

## Flujo funcional principal

1. El usuario abre la app local.
2. Si no hay token OAuth de Google, el dashboard redirige a `/login`.
3. En Configuracion se carga el Sheet, pestana de inventario y credenciales
   operativas.
4. La app descarga o lee del cache la pricelist de Card Kingdom.
5. El dashboard permite buscar cartas y ver stock local, reservado y disponible.
6. Al modificar stock, la app actualiza Google Sheets y, si el modulo esta
   habilitado, registra movimiento y venta.
7. Al crear reservas, la app valida contacto/retiro, guarda filas en `Reservas`
   y mantiene clientes en `Clientes`.
8. La sincronizacion manual o programada recalcula precios y acciones del
   inventario contra CK.

## Componentes principales

| Componente | Responsabilidad |
| --- | --- |
| `TcgBotApplication` | Arranque Spring Boot y scheduling. |
| `DesktopLauncher` | Arranque desktop local, puerto dinamico y apertura del navegador. |
| `DashboardController` | Interfaz web: dashboard, configuracion, inventario, reservas, caja, importacion y sincronizacion. |
| `WhatsappWebhookController` | Verificacion y recepcion del webhook de WhatsApp. |
| `WhatsappNotificationController` | Notificaciones cortas para avisar pedidos entrantes. |
| `PublicTunnelGuardFilter` | Si el host es publico, solo deja pasar `/webhooks/whatsapp`. |
| `StoreSettingsService` | Configuracion local de tienda, Sheet, cache, logo, tutorial y WhatsApp. |
| `GoogleOAuthService` | OAuth y cliente Google Sheets autenticado. |
| `GoogleSheetsService` | Lectura/escritura real de pestanas del spreadsheet. |
| `InventoryService` | Fachada simple sobre Google Sheets para inventario, movimientos, caja, reservas y clientes. |
| `CardKingdomApiService` | Descarga, cache y busqueda de la pricelist CK. |
| `PriceComparisonService` | Conversion CK USD a precio local con redondeo. |
| `WhatsappOrderService` | Conversacion WhatsApp, consulta de stock y armado de reservas. |
| `WhatsappClient` | Envio de mensajes mediante Meta Graph API. |

## Operaciones de inventario

El inventario vive en la pestana configurada. Cada fila representa una carta con
cantidad, nombre, set, numero, condicion, printing, idioma, precio local, precio
CK y accion.

Operaciones relevantes:

- Buscar: consulta CK y cruza contra las filas locales.
- Agregar carta: crea una fila nueva desde un producto CK y calcula precio local.
- Cambiar cantidad: actualiza cantidad y accion en el Sheet.
- Borrar fila: solo se permite al confirmar cuando la carta ya esta en cero.
- Sincronizar: busca coincidencia exacta en CK, actualiza precio local/precio CK
  y recalcula estado.
- Importar lista: parsea lineas, propone coincidencias CK y agrega o incrementa
  stock seleccionado.

Cuando movimientos esta habilitado, las salidas registran tambien una venta en
`Caja`.

## Operaciones de reservas

Reservas usa la pestana `Reservas` y opcionalmente `Clientes`.

Estados funcionales:

- `EN_STOCK`: pedido/reserva con stock identificable.
- `RESERVADA`: carta separada para un cliente.
- `WANTED`: pedido pendiente sin stock exacto disponible.

Flujos principales:

- Crear reserva desde la pagina de reservas o desde una busqueda.
- Separar stock para una reserva pendiente.
- Entregar una reserva individual o un grupo de cliente.
- Liberar reservas vencidas o reprogramar fecha de retiro.
- Eliminar reservas o grupos completos.
- Mantener clientes por nombre, telefono y DNI.

La disponibilidad no es solo la cantidad de inventario: se calcula descontando
reservas activas para la misma carta/edicion/printing/condicion.

## Operaciones de WhatsApp

WhatsApp es opcional y depende de configuracion local:

- Phone Number ID.
- Access Token.
- Verify Token.
- Horario de atencion o modo siempre activo.

Meta llama a `/webhooks/whatsapp`. La app:

1. Verifica el webhook con el token configurado.
2. Procesa mensajes de texto en segundo plano.
3. Deduplica mensajes por ID.
4. Mantiene una conversacion en memoria por telefono.
5. Permite comandos como `stock Sol Ring`, `pedido`, `confirmar` y `cancelar`.
6. Consulta stock disponible contra inventario menos reservas.
7. Al confirmar, guarda reservas y actualiza/crea el cliente.
8. Responde al cliente por Meta Graph API.

Nota operativa: las conversaciones de WhatsApp son en memoria. Si la app se
reinicia, los pedidos no confirmados se pierden.

## Seguridad operativa

- El servidor escucha por defecto en `127.0.0.1`.
- Si se expone mediante tunel publico, `PublicTunnelGuardFilter` bloquea todo
  excepto `/webhooks/whatsapp`.
- El acceso a Google Sheets requiere OAuth y scope `SPREADSHEETS`.
- La configuracion local contiene tokens y no debe subirse al repositorio.
- El logo se valida por tipo y limite de 2 MB.
- Las reservas validan datos minimos de contacto y fecha de retiro.

## Diagrama de flujo tecnico

```mermaid
flowchart TD
    A[Usuario abre app] --> B{Modo de arranque}
    B -->|Desarrollo| C[TcgBotApplication]
    B -->|Windows desktop| D[DesktopLauncher]
    D --> E[Define puerto local y app.storage-dir]
    E --> C

    C --> F[Spring Boot carga beans]
    F --> G[DashboardController]
    F --> H[Servicios]
    F --> I[PublicTunnelGuardFilter]
    F --> J[Scheduler habilitado]

    G --> K{Hay token Google?}
    K -->|No| L[/login y Configuracion/]
    L --> M[GoogleOAuthService]
    M --> N[OAuth Google]
    N --> O[Token local en app.storage-dir]
    O --> K

    K -->|Si| P[Dashboard]
    P --> Q[Buscar carta]
    Q --> R[CardKingdomApiService]
    R --> S{Cache CK valido?}
    S -->|Si| T[Lee ck-pricelist.json]
    S -->|No| U[Descarga API Card Kingdom]
    U --> T
    T --> V[Productos CK]

    P --> W[Leer inventario]
    W --> X[InventoryService]
    X --> Y[GoogleSheetsService]
    Y --> Z[Google Sheets]
    V --> AA[Cruce producto CK + stock local]
    Z --> AA
    AA --> AB[Resultados en Thymeleaf]

    P --> AC[Agregar o modificar stock]
    AC --> X
    X --> Y
    Y --> Z
    AC --> AD{Movimientos habilitado?}
    AD -->|Si| AE[Append Movimientos y Caja]
    AE --> Y
    AD -->|No| AB

    P --> AF[Crear o entregar reserva]
    AF --> X
    X --> AG[Reservas y Clientes]
    AG --> Y
    Y --> Z

    J --> AH[Sincronizacion cada 1h2m]
    AH --> AI{Sheet configurado y Google conectado?}
    AI -->|No| AJ[Omitir]
    AI -->|Si| R
    R --> AK[Recalcular precios/acciones]
    AK --> X
    X --> Y
    Y --> Z

    AL[Meta WhatsApp] --> AM[/webhooks/whatsapp]
    AM --> I
    I --> AN{Path publico permitido?}
    AN -->|No| AO[404]
    AN -->|Si| AP[WhatsappWebhookController]
    AP --> AQ[WhatsappOrderService]
    AQ --> X
    AQ --> AR[WhatsappClient]
    AR --> AS[Meta Graph API]
    AQ --> AT[WhatsappNotificationService]
```

## Lectura rapida para mantenimiento

- Si falla la busqueda o sincronizacion, mirar primero cache/conectividad de CK.
- Si falla lectura/escritura de inventario, mirar OAuth, ID del Sheet, permisos
  del usuario y nombre de pestana configurada.
- Si las cantidades disponibles no coinciden con la cantidad del Sheet, revisar
  reservas activas: el disponible descuenta reservas.
- Si WhatsApp responde que no esta configurado, revisar `store.properties` desde
  Configuracion y que el webhook use el verify token correcto.
- Si la app esta publicada por tunel y el dashboard no abre, es esperado: por
  host publico solo se permite el webhook.
