# HU-000: Documento funcional base + credenciales de Google

- **Estado:** aceptada
- **Tipo:** mejora técnica
- **Módulo:** google
- **Rama:** `v2/feature/HU-000-documento-funcional-base`
- **QA elegido:** sin QA automático (el login de Google no se puede automatizar); prueba manual de la persona a cargo
- **Funcional documentado:** felices (un solo camino: entrar con el Gmail y aceptar el aviso de app de prueba). Se documenta después de que el login funcione.
- **Flujos funcionales:** GOO-F01, GOO-F02 (`vigente`)

## Historia
**Como** dueña del comercio
**quiero** que la app tenga sus credenciales de Google y que el Gmail del proyecto pueda iniciar sesión
**para** que la app lea y escriba el Sheet del comercio, y dejar documentado cómo abrirla después a cualquier Gmail

## Descripción
Hoy el login con Google no arranca porque faltan `GOOGLE_OAUTH_CLIENT_ID` y `GOOGLE_OAUTH_CLIENT_SECRET` (ver `login.html`).
Con el Gmail del proyecto (`TCGStorehelper@gmail.com`) se crea un proyecto en Google Cloud, se habilita la Sheets API, se configura la pantalla de consentimiento en modo Prueba con ese Gmail como usuario de prueba y se crea un cliente OAuth de escritorio.
Las claves van en `config/google-oauth.properties`, que está ignorado por git; la app lo lee al arrancar (`spring.config.import`). Hay un ejemplo versionado en `config/google-oauth.properties.example`.
La guía paso a paso (solo texto) queda en `docs/google/guia-credenciales-oauth.md`, con una sección para publicar la app para todos.
La contraseña del Gmail **no se guarda en ningún lado**: el login es manual, una vez, y el permiso queda guardado en `data/` (ignorado).

## Criterios de aceptación
**CA1: Login con el Gmail del proyecto**
- **Dado** que `config/google-oauth.properties` tiene el Client ID y el Secret
- **Cuando** se abre la app y se inicia sesión con `TCGStorehelper@gmail.com`
- **Entonces** Google pide los permisos, vuelve a la app y queda conectada al Sheet

**CA2: Faltan las claves**
- **Dado** que no existe `config/google-oauth.properties` ni las variables de entorno
- **Cuando** se abre la pantalla de login
- **Entonces** se ve el aviso de que falta configurar Google OAuth (comportamiento actual)

**CA3: Gmail que no es usuario de prueba**
- **Dado** que la app está en modo Prueba
- **Cuando** alguien entra con un Gmail que no está cargado como usuario de prueba
- **Entonces** Google bloquea el acceso ("acceso bloqueado")

**CA4: Nada sensible en git**
- **Dado** que las claves y el token están configurados
- **Cuando** se corre `git status`
- **Entonces** no aparecen `config/google-oauth.properties` ni nada dentro de `data/`

## Locators (`data-testid`)
No aplica (no hay cambios de pantalla).

## Contrato de API (si aplica)
No aplica.

## Fuera de alcance
- Pedir la verificación de la app a Google (solo se documentan los pasos).
- Decidir entre `spreadsheets` verificado y `drive.file` + Google Picker.
- Automatizar el login de Google en tests (Google lo bloquea).
- Guardar la contraseña del Gmail.

## Tareas por agente (en orden)
| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | líder | `.gitignore` + `spring.config.import` + archivo de ejemplo | Compila |
| 2 | google-integracion | Guía paso a paso en texto + sección "Publicar para todos" | Guía en `docs/google/` |
| 3 | persona a cargo | Seguir la guía en la consola y pegar las claves en el archivo | CA1 OK |
| 4 | analista-funcional | Flujos de Google (GOO) en el documento funcional | Flujos `propuesto · HU-000` |
| 5 | qa-* | Escenarios según el QA elegido | Veredicto |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-14 | Brief aprobado | Sin guardar la contraseña; guía solo en texto; misma HU-000 |
| 2026-10-14 | Credenciales | Cliente "App de escritorio" creado; claves en `config/google-oauth.properties` (ignorado). Guía en `docs/google/guia-credenciales-oauth.md` |
| 2026-10-14 | CA1 login | OK: la persona a cargo inició sesión con TCGStorehelper@gmail.com. Falta conectar un Sheet (la app intenta leer con ID vacío → 400) |
| 2026-10-14 | Cierre | Sheet conectado (en blanco con encabezados). Funcional v0.2.0 (GOO-F01/F02). Aceptada por la persona a cargo. El resto del documento funcional base de la v1 queda para otra HU |
