# MTG-Card-Store

Aplicacion local para administrar stock de cartas MTG con Google Sheets y precios de Card Kingdom.

## Setup en una PC nueva

1. Clonar el repositorio.

2. Conseguir el archivo privado `google-credentials.json`.
La app lo guarda automaticamente en:

```text
./data/google-credentials.json
```

La carpeta `data` no se sube a GitHub porque contiene datos locales, cache y credenciales.

3. Ejecutar la app y abrir:

```text
http://localhost:8080/
```

4. LEER el tutorial y completar Configuracion.
En Configuracion tambien se puede elegir la carpeta donde se guarda el cache descargado de Card Kingdom. Conviene usar un disco con espacio disponible.

5. Guardar configuracion y sincronizar inventario.

## Configuracion avanzada

Se puede cambiar la carpeta de datos con la variable de entorno `TCG_INVENTORY_DATA_DIR`.
Tambien se puede usar `GOOGLE_APPLICATION_CREDENTIALS` para apuntar a otro JSON de credenciales.

## Login con Google (opcional)

En vez de compartir el Sheet con la cuenta de servicio, cada tienda puede entrar con su cuenta de Google
y la app crea el Sheet del inventario en su Drive. Solo se piden los permisos `openid`, `email` y `drive.file`
(la app ve unicamente los archivos que crea ella).

1. En Google Cloud, crear un cliente OAuth de tipo **App de escritorio**.
2. Guardar sus claves en `./data/google-oauth.properties` (nunca en el repo):

```properties
google.oauth.client-id=...apps.googleusercontent.com
google.oauth.client-secret=...
```

Tambien se pueden pasar con las variables `TCG_GOOGLE_CLIENT_ID` y `TCG_GOOGLE_CLIENT_SECRET`.

3. En Configuracion, tocar **Conectar con Google** y despues **Crear mi Sheet de inventario**.

El permiso queda guardado en `./data/google-account.properties`. "Desconectar" lo revoca y borra ese archivo.
