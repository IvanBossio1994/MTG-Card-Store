# MTG-Card-Store

Aplicacion local para administrar stock de cartas MTG con Google Sheets y precios de Card Kingdom.

## Setup en una PC nueva

1. Clonar el repositorio.

2. Configurar el OAuth client de Google para esta build con las variables `GOOGLE_OAUTH_CLIENT_ID` y `GOOGLE_OAUTH_CLIENT_SECRET`.

La carpeta `data` no se sube a GitHub porque contiene datos locales, cache y tokens de inicio de sesion.

3. Para desarrollo, ejecutar la app y abrir:

```text
http://localhost:8080/
```

4. LEER el tutorial y completar Configuracion.
En Configuracion tambien se puede elegir la carpeta donde se guarda el cache descargado de Card Kingdom. Conviene usar un disco con espacio disponible.

5. Iniciar sesion con el Gmail que tiene acceso al Google Sheet, guardar configuracion y sincronizar inventario.

## Configuracion avanzada

Se puede cambiar la carpeta de datos con la variable de entorno `TCG_INVENTORY_DATA_DIR`.

## App de Windows

Para generar la app con doble click:

```powershell
.\scripts\build-windows-app.ps1
```

El ejecutable queda en:

```text
dist\windows-app\TCG Inventory\TCG Inventory.exe
```

La app instalada guarda configuracion y cache en `%APPDATA%\TCG Inventory\data`, asi cada usuario arranca con Configuracion vacia y carga su propio Google Sheet.
Al abrir, usa el primer navegador disponible entre Edge, Chrome y Firefox. Edge y Chrome se abren en modo app; Firefox se abre en una ventana normal con perfil propio.

Para generar un instalador `.exe` o `.msi` con `jpackage`, primero hay que instalar WiX Toolset y despues ejecutar:

```powershell
.\scripts\build-windows-app.ps1 -Type exe
```

Si WiX 3.14 esta extraido en `.tools\wix314`, el script lo detecta sin instalarlo globalmente.

Microsoft Store permite dos caminos para Win32: subir un MSIX, o listar un instalador `.exe/.msi` offline firmado y hospedado por nosotros. Para MSIX hace falta Windows SDK (`makeappx`/`signtool`) y la identidad de Partner Center.
