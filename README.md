# MTG-Card-Store

Aplicacion local para administrar stock de cartas MTG con Google Sheets y precios de Card Kingdom.

## Setup en una PC nueva

1. Clonar el repositorio.
2. Copiar el archivo de credenciales de Google en:

```text
./data/google-credentials.json
```

La carpeta `data` no se sube a GitHub porque contiene datos locales, cache y credenciales.

3. Ejecutar la app y abrir:

```text
http://localhost:8080/
```

4. En `Configuracion`, pegar el enlace o ID del Google Sheet de esa tienda.
5. Compartir ese Sheet como `Editor` con:

```text
tcg-bot-service@tcg-inventory-bot.iam.gserviceaccount.com
```

6. Guardar configuracion y sincronizar inventario.

## Configuracion avanzada

Se puede cambiar la carpeta de datos con la variable de entorno `TCG_INVENTORY_DATA_DIR`.
Tambien se puede usar `GOOGLE_APPLICATION_CREDENTIALS` para apuntar a otro JSON de credenciales.
