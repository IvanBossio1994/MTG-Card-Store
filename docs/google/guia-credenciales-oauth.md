# Guía: credenciales de Google para TCG Inventory

Esta guía explica, paso a paso y sin programar, cómo darle a la app sus "claves" de Google para que pueda leer y escribir el Google Sheet del comercio.

**Datos que usa la app (para que coincida con lo que vas a ver en Google):**
- **Permiso que pide:** ver, editar, crear y eliminar tus hojas de cálculo de Google (el permiso técnico se llama `https://www.googleapis.com/auth/spreadsheets`). No pide acceso a Gmail ni a Drive.
- **Tipo de cliente:** "App de escritorio" (en inglés, *Desktop app*).
- **Dirección de vuelta:** después de iniciar sesión, Google vuelve a `http://127.0.0.1:8080/configuracion/google/oauth/callback`. Con el tipo "App de escritorio" esta dirección **no hay que cargarla en ningún lado**: Google la acepta sola porque es la propia PC.
- **Dónde quedan las claves:** en el archivo `config/google-oauth.properties`, que nunca se sube a git.

**Reglas de seguridad:**
- La contraseña del Gmail **no se escribe en ningún archivo ni se le pasa a nadie**. Solo la escribís vos, en la página de Google, al iniciar sesión.
- El "Client secret" (la clave secreta) **no se pega en ningún chat** ni en ningún archivo que no sea `config/google-oauth.properties`.

> Nota sobre los nombres: la consola de Google puede aparecer en español o en inglés según el idioma de tu cuenta. En cada paso va el nombre en español y, entre paréntesis, en inglés. Google cambia los nombres de vez en cuando; estos están revisados en octubre de 2026.

---

## 1. Crear el proyecto en Google Cloud

1. Abrí el navegador en una ventana nueva y entrá a https://console.cloud.google.com
2. Si te pide iniciar sesión, entrá con **TCGStorehelper@gmail.com**. Si ya hay otro Gmail abierto, hacé clic en tu foto (arriba a la derecha) y cambiá a TCGStorehelper@gmail.com.
3. Si es la primera vez, Google muestra los términos del servicio: elegí el país, marcá la casilla de aceptación y hacé clic en **Aceptar y continuar** (*Agree and continue*).
4. Arriba a la izquierda, al lado del logo de Google Cloud, hacé clic en el **selector de proyectos** (dice "Selecciona un proyecto" o el nombre de algún proyecto).
5. En la ventana que se abre, hacé clic en **Proyecto nuevo** (*New project*), arriba a la derecha.
6. En **Nombre del proyecto** (*Project name*) escribí: `TCG Inventory`.
7. En **Ubicación** (*Location*) dejá "Sin organización" (*No organization*).
8. Hacé clic en **Crear** (*Create*) y esperá unos segundos.
9. Volvé a abrir el selector de proyectos y elegí **TCG Inventory**, para que todo lo que sigue se haga dentro de ese proyecto. Confirmá que arriba diga "TCG Inventory".

## 2. Habilitar la Google Sheets API

La "API" es la puerta por la que la app habla con Google Sheets. Hay que abrirla una vez.

1. Hacé clic en el **menú** (el ícono de tres rayitas, arriba a la izquierda).
2. Entrá a **APIs y servicios** (*APIs & Services*) y después a **Biblioteca** (*Library*).
3. En el buscador escribí `Google Sheets API` y apretá Enter.
4. Hacé clic en el resultado **Google Sheets API**.
5. Hacé clic en **Habilitar** (*Enable*). Cuando termina, el botón cambia a "Administrar" (*Manage*).

**No hace falta habilitar la Google Drive API**: la app hoy solo usa Sheets.

## 3. Pantalla de consentimiento (lo que ve la gente al iniciar sesión)

Esto define el nombre de la app que muestra Google y quién puede entrar.

### 3.1 Configuración inicial

1. Abrí el **menú** (tres rayitas) y entrá a **Google Auth Platform**. Si no lo ves, escribí "Google Auth Platform" en el buscador de arriba.
2. Si aparece el mensaje "Google Auth Platform aún no está configurado" (*not configured yet*), hacé clic en **Comenzar** (*Get started*).
3. **Información de la app** (*App information*):
   - En **Nombre de la app** (*App name*) escribí: `TCG Inventory`.
   - En **Correo electrónico de asistencia del usuario** (*User support email*) elegí `TCGStorehelper@gmail.com`.
   - Hacé clic en **Siguiente** (*Next*).
4. **Público** (*Audience*): elegí **Externo** (*External*) y hacé clic en **Siguiente**. (La opción "Interno" no está disponible para Gmail comunes; es normal.)
5. **Información de contacto** (*Contact information*): escribí `TCGStorehelper@gmail.com` y hacé clic en **Siguiente**.
6. **Finalizar** (*Finish*): marcá la casilla **Acepto la Política de Datos del Usuario de los Servicios de las APIs de Google** (*I agree to the Google API Services: User Data Policy*) y hacé clic en **Continuar** (*Continue*).
7. Hacé clic en **Crear** (*Create*).

La app queda en **modo Prueba** (*Testing*). Así tiene que quedar por ahora: no toques el botón "Publicar app".

### 3.2 Agregar a TCGStorehelper@gmail.com como usuario de prueba

En modo Prueba, **solo pueden entrar los Gmail que estén en la lista de usuarios de prueba**. Aunque seas la dueña del proyecto, agregate igual.

1. En el menú de la izquierda de Google Auth Platform, hacé clic en **Público** (*Audience*).
2. Bajá hasta la sección **Usuarios de prueba** (*Test users*).
3. Hacé clic en **Agregar usuarios** (*Add users*).
4. Escribí `TCGStorehelper@gmail.com`.
5. Hacé clic en **Guardar** (*Save*). El correo tiene que aparecer en la lista.

**Para agregar otro Gmail más adelante** (por ejemplo, el de otro comercio): repetí los pasos 1 a 5 con ese Gmail. Se pueden agregar hasta **100** Gmail. Para sacar uno, buscalo en la lista y hacé clic en el ícono de tacho de basura que está a su derecha.

### 3.3 Declarar el permiso de Sheets (recomendado)

En modo Prueba la app funciona aunque saltees esto, pero deja todo listo para cuando haya que publicarla.

1. En el menú de la izquierda hacé clic en **Acceso a los datos** (*Data access*).
2. Hacé clic en **Agregar o quitar permisos** (*Add or remove scopes*).
3. En el buscador del panel escribí `spreadsheets`.
4. Marcá la fila que dice `.../auth/spreadsheets` (Google Sheets API, "Ver, editar, crear y eliminar todas tus hojas de cálculo de Hojas de cálculo de Google"). **No** marques la que termina en `.readonly`.
5. Hacé clic en **Actualizar** (*Update*) y después en **Guardar** (*Save*), abajo de todo.

## 4. Crear el cliente OAuth "App de escritorio" y copiar las claves

El "cliente" es la ficha de identidad de la app ante Google. Tiene dos datos: el **Client ID** (como un nombre de usuario, no es secreto) y el **Client secret** (como una contraseña de la app, es secreto).

> **Importante:** Google muestra el Client secret completo **una sola vez**, al crearlo. Tené abierto el archivo del paso 5 antes de empezar, para pegarlo ahí directamente.

1. En el menú de la izquierda de Google Auth Platform, hacé clic en **Clientes** (*Clients*).
2. Hacé clic en **Crear cliente** (*Create client*).
3. En **Tipo de aplicación** (*Application type*) elegí **App de escritorio** (*Desktop app*). No elijas "Aplicación web": con ese tipo el inicio de sesión falla.
4. En **Nombre** (*Name*) escribí: `TCG Inventory escritorio`. (Este nombre solo lo ves vos.)
5. Si aparece la casilla "Usar este cliente para un agente con IA" (*Use this client for an AI-powered agent*), **dejala sin marcar**.
6. Hacé clic en **Crear** (*Create*).
7. Se abre una ventana con el **ID de cliente** (*Client ID*, termina en `.apps.googleusercontent.com`) y el **Secreto del cliente** (*Client secret*, suele empezar con `GOCSPX-`).
8. Al lado de cada uno hay un ícono de copiar (dos hojitas). Usalo para copiar y pegar cada dato en el archivo del paso 5. No los copies a mano.
9. Recién cuando los dos estén pegados y guardados en el archivo, hacé clic en **Aceptar** (*OK*) para cerrar la ventana.

Si la ventana muestra un botón **Descargar JSON** (*Download JSON*), no hace falta usarlo. Si lo descargaste, no lo guardes dentro de la carpeta del proyecto: borralo cuando termines.

**Si perdiste el secreto** (cerraste la ventana sin copiarlo): entrá a **Clientes**, abrí el cliente `TCG Inventory escritorio`, hacé clic en **Agregar secreto** (*Add secret*), copiá el nuevo y pegalo en el archivo. Después deshabilitá y borrá el secreto viejo desde esa misma pantalla.

## 5. Pegar las claves en `config/google-oauth.properties`

1. Abrí el Explorador de archivos y entrá a la carpeta de la app (la que contiene las carpetas `config` y `data`).
2. Entrá a la carpeta `config`.
3. Buscá el archivo `google-oauth.properties.example`. Hacé clic derecho, **Copiar**, y después clic derecho en un lugar vacío, **Pegar**.
4. A la copia cambiale el nombre (clic derecho, **Cambiar nombre**) a exactamente: `google-oauth.properties`
   - Ojo: si Windows oculta las extensiones, podría quedar como `google-oauth.properties.txt` y la app no lo vería. Para chequearlo, en el Explorador entrá a **Vista** > **Mostrar** > marcá **Extensiones de nombre de archivo**.
5. Hacé clic derecho en `google-oauth.properties` > **Abrir con** > **Bloc de notas**.
6. Vas a ver dos líneas. Reemplazá solo lo que está después del signo `=`:
   - En la línea `google.oauth.client-id=`, borrá `PEGAR_CLIENT_ID.apps.googleusercontent.com` y pegá tu **Client ID**.
   - En la línea `google.oauth.client-secret=`, borrá `PEGAR_CLIENT_SECRET` y pegá tu **Client secret**.
   - No dejes comillas. Las líneas que empiezan con `#` son comentarios: podés dejarlas.
7. Guardá con **Archivo** > **Guardar** (o Ctrl + G / Ctrl + S) y cerrá el Bloc de notas.
8. Si la app estaba abierta, **cerrala y volvé a abrirla**: lee el archivo solo al arrancar.

Datos útiles:
- Este archivo **está excluido de git** (no se sube nunca). No lo mandes por chat ni por mail. Si alguna vez se filtra, creá un secreto nuevo (paso 4, "Si perdiste el secreto") y borrá el viejo.
- Si en la PC existen las variables de entorno `GOOGLE_OAUTH_CLIENT_ID` y `GOOGLE_OAUTH_CLIENT_SECRET`, la app las sigue aceptando y **tienen prioridad** sobre el archivo. Si cambiaste las claves en el archivo y la app sigue usando las viejas, revisá que no haya variables de entorno con valores anteriores.

## 6. Primer inicio de sesión y qué esperar

1. Abrí la app. En la pantalla de login, el botón **Iniciar sesion con Google** ahora tiene que estar activo (ya no aparece el aviso "Esta build no tiene configurado Google OAuth").
2. Hacé clic en **Iniciar sesion con Google**. Se abre la página de Google.
3. Elegí o escribí **TCGStorehelper@gmail.com** y su contraseña (solo en la página de Google).
4. Vas a ver el aviso **"Google no verificó esta app"** (*Google hasn't verified this app*). Es normal en modo Prueba: la app es tuya y todavía no pasó la revisión de Google.
   - Hacé clic en **Configuración avanzada** (*Advanced*), abajo a la izquierda.
   - Hacé clic en **Ir a TCG Inventory (no seguro)** (*Go to TCG Inventory (unsafe)*).
5. Google muestra qué permiso pide la app: **ver, editar, crear y eliminar tus hojas de cálculo de Google**. Si aparece una casilla al lado de ese permiso, **marcala**: sin ella la app no puede leer el Sheet.
6. Hacé clic en **Continuar** (*Continue*).
7. Google vuelve sola a la app y aparece el mensaje **"Google conectado. Ya podes usar la app."**

### El permiso vence a los 7 días (solo en modo Prueba)

Mientras la app esté en modo Prueba, Google **corta el permiso 7 días después** de cada inicio de sesión. No es una falla de la app: es una regla de Google para las apps en prueba.

Cómo se nota: la app deja de leer o guardar en el Sheet y muestra un error de sincronización (puede mencionar `invalid_grant`).

Cómo reconectar:
1. En el menú de la app, hacé clic en **Cerrar sesion**.
2. En la pantalla de login, hacé clic en **Iniciar sesion con Google** y repetí los pasos 3 a 7 de arriba.

Esto se repite cada semana hasta que la app se publique (sección 8).

## 7. Problemas comunes

**"Acceso bloqueado: TCG Inventory no completó el proceso de verificación de Google"** o **Error 403: access_denied**
- Causa: el Gmail con el que intentaste entrar no está en la lista de usuarios de prueba.
- Solución: agregalo como en la sección 3.2 (o entrá con un Gmail que ya esté). Esperá un minuto y probá de nuevo.

**Error 400: redirect_uri_mismatch**
- Causa más común: el cliente se creó como "Aplicación web" en lugar de "App de escritorio".
- Solución: creá un cliente nuevo de tipo **App de escritorio** (sección 4) y pegá sus claves en el archivo (sección 5).
- Otra causa: abriste la app con una dirección que no es la de la propia PC (por ejemplo, la IP de la PC en la red del local). La app tiene que abrirse en `http://127.0.0.1:8080` o `http://localhost:8080`.

**invalid_grant** (o "token expired or revoked")
- Causa: venció el permiso de 7 días del modo Prueba, cambiaste la contraseña del Gmail, o quitaste el acceso de la app desde tu cuenta de Google.
- Solución: **Cerrar sesion** en la app y volver a **Iniciar sesion con Google** (sección 6).

**Error 401: invalid_client** o **deleted_client**
- Causa: el Client ID o el Client secret quedaron mal copiados (un espacio de más, falta un carácter), o el cliente se borró en Google Cloud.
- Solución: revisá el archivo `config/google-oauth.properties`. Si no estás segura del secreto, creá uno nuevo (sección 4, "Si perdiste el secreto").

**"Google Sheets API has not been used in project... or it is disabled"**
- Causa: no se habilitó la API, o se habilitó en otro proyecto.
- Solución: repetí la sección 2, chequeando que arriba diga "TCG Inventory". Esperá unos minutos y probá de nuevo.

**El botón "Iniciar sesion con Google" sigue gris**
- Causa: la app no encuentra el archivo o le falta una de las dos claves.
- Solución: revisá el nombre exacto del archivo (sin `.txt`), que esté en la carpeta `config` y que las dos líneas tengan valor después del `=`. Cerrá y abrí la app.

## 8. "Publicar para todos" (DECISIÓN PENDIENTE)

> **Estado: decisión pendiente.** Esta sección solo explica las opciones. No hay que hacer nada de esto todavía.

Hoy la app está en **modo Prueba**: solo entran los Gmail cargados a mano (hasta 100) y el permiso vence cada 7 días. Para llevarla a otros comercios sin esas limitaciones hay tres caminos.

**En todos los casos, TCGStorehelper@gmail.com sigue siendo la dueña del proyecto en Google Cloud** y conviene dejarla siempre en la lista de usuarios de prueba, para poder volver a modo Prueba sin quedarse afuera.

### Opción A: publicar sin verificar (paso intermedio)

1. Entrá a **Google Auth Platform** > **Público** (*Audience*).
2. Hacé clic en **Publicar app** (*Publish app*) y confirmá.

Qué cambia:
- Ya no hace falta cargar cada Gmail a mano y el permiso **deja de vencer a los 7 días**.
- Pero todos van a seguir viendo el aviso "Google no verificó esta app".
- Google pone un tope de **100 usuarios nuevos en total** (para siempre, no por mes) mientras la app no esté verificada. Al llegar a 100, nadie más puede entrar.

### Opción B: verificar la app con Google (mantener el permiso actual de Sheets)

El permiso que usa la app (`spreadsheets`, acceso a todas las hojas de cálculo) es lo que Google llama un **permiso sensible**. Para usarlo con cualquier Gmail, sin aviso y sin tope, Google tiene que revisar la app. Se pide desde **Google Auth Platform** > **Centro de verificación** (*Verification center*). Google pide:

1. **Página de inicio** pública de la app, que explique qué hace (no puede ser solo una pantalla de login).
2. **Política de privacidad** pública, enlazada desde la página de inicio, que diga qué datos de Google usa la app y para qué.
3. **Dominio propio verificado** (por ejemplo, `tcginventory.com.ar`), comprobado con Google Search Console. La página de inicio y la política tienen que estar en ese dominio. Tiene costo anual.
4. **Video de demostración** (por ejemplo, en YouTube, como "no listado") que muestre el inicio de sesión completo, la pantalla de permisos y cómo la app usa el Sheet.
5. **Justificación por escrito** de por qué la app necesita acceso a las hojas de cálculo y por qué un permiso más chico no alcanza.
6. Logo y datos de la app en **Desarrollo de la marca** (*Branding*) coincidentes con la página de inicio.

Tiempos: Google no promete un plazo fijo. La revisión de marca suele tardar unos días hábiles y la del permiso sensible puede llevar de algunos días a varias semanas, con idas y vueltas por mail si piden correcciones.

### Opción C: cambiar a `drive.file` + Google Picker (sin revisión de permiso sensible)

`drive.file` es un permiso **no sensible**: la app solo puede tocar los archivos que ella misma creó o que el usuario eligió explícitamente en una ventanita de Google (el "Google Picker", el selector de archivos de Google Drive).

- Ventaja: no requiere la revisión de permiso sensible (sin video ni justificación). Igual conviene completar marca, página de inicio y política de privacidad.
- Desventaja: **requiere cambios en la app**: agregar el selector para que cada comercio elija su Sheet, habilitar la Google Drive API y la Google Picker API, y crear una clave de API adicional. La app no podría abrir un Sheet que el usuario no eligió con el selector.

### Resumen para decidir

| | A: publicar sin verificar | B: verificar `spreadsheets` | C: `drive.file` + Picker |
|---|---|---|---|
| Aviso "no verificada" | Sí | No | No |
| Tope de usuarios | 100 en total | Sin tope | Sin tope |
| Vence a los 7 días | No | No | No |
| Necesita dominio, web y política | No | Sí | Recomendado |
| Necesita video y justificación | No | Sí | No |
| Cambios en la app | No | No | Sí |
