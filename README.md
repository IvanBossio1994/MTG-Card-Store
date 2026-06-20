# MTG-Card-Store

Aplicacion local para administrar stock de cartas MTG con Google Sheets y precios de Card Kingdom.

## Setup en una PC nueva

1. Clonar el repositorio.

2. Configurar el OAuth client de Google para esta build con las variables `GOOGLE_OAUTH_CLIENT_ID` y `GOOGLE_OAUTH_CLIENT_SECRET`.

La carpeta `data` no se sube a GitHub porque contiene datos locales, cache y tokens de inicio de sesion.

3. Ejecutar la app y abrir:

```text
http://localhost:8080/
```

4. LEER el tutorial y completar Configuracion.
En Configuracion tambien se puede elegir la carpeta donde se guarda el cache descargado de Card Kingdom. Conviene usar un disco con espacio disponible.

5. Iniciar sesion con el Gmail que tiene acceso al Google Sheet, guardar configuracion y sincronizar inventario.

## Configuracion avanzada

Se puede cambiar la carpeta de datos con la variable de entorno `TCG_INVENTORY_DATA_DIR`.
