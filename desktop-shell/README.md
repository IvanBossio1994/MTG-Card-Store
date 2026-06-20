# TCG Inventory Desktop Shell

Prototipo minimo para abrir la app como ventana de escritorio.

## Uso local

Desde la raiz del proyecto:

```powershell
.\.tools\apache-maven-3.9.16\bin\mvn.cmd package
dotnet build .\desktop-shell\TCGInventoryShell.csproj -c Release
```

Despues abrir:

```text
desktop-shell\bin\Release\net5.0-windows\TCG Inventory.exe
```

El launcher inicia el JAR en un puerto libre y abre Microsoft Edge en modo app. Al cerrar la ventana, apaga el proceso Java.
