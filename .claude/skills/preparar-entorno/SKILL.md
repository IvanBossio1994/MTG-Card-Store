---
name: preparar-entorno
description: Primera vez en una PC. Verifica e instala lo que necesita el equipo de agentes TCG (JDK 21, Git, Maven, Node y Playwright CLI, claves de config/, Chrome, MCP de IntelliJ) y deja CLAUDE.local.md con las rutas de esa PC. Usar cuando no existe CLAUDE.local.md, cuando falla una herramienta básica o cuando se pide preparar la PC.
---

# Preparar el entorno (primera vez)

La aplica `agente-lider` sola si no existe `CLAUDE.local.md`. Hablale a la persona en simple: no programa.

## 1. Revisar (sin instalar nada todavía)
Corré cada chequeo y armá una tabla `Qué | Estado | Qué hacer`:

| Qué | Cómo se revisa | Si falta |
|---|---|---|
| **JDK 21** | `java -version` o `$env:JAVA_HOME` | `winget install EclipseAdoptium.Temurin.21.JDK`, y después definir `JAVA_HOME` como variable de usuario |
| **Git** | `git --version`; si no está en el PATH, buscar `C:\Program Files\Git\cmd\git.exe` u otra unidad | `winget install Git.Git`, o pedirle la ruta a la persona |
| **Maven 3.9.16** | que exista `.tools\apache-maven-3.9.16\bin\mvn.cmd` (git no lo versiona) | Descargarlo de `https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip` y descomprimirlo en `.tools\` |
| **Node 24 LTS** | `node --version` (20 o más). El build no lo necesita (Maven baja su propio Node), pero sí `playwright-cli` | `winget install --id OpenJS.NodeJS.LTS --exact --source winget` (pide permiso de administrador una vez). Después, abrir una terminal nueva |
| **Playwright CLI** | `playwright-cli --version` | `npm install -g @playwright/cli@latest`. La skill ya viene en el repo (`.claude/skills/playwright-cli/`). Sirve para que el líder revise prototipos y pantallas |
| **Claves de Google** | que existan `config/google-oauth.properties` y `config/google-service-account.json`, **con la herramienta Glob** (patrón `config/*`). Nunca se abre su contenido: `.claude/settings.json` bloquea leerlas y nombrarlas en PowerShell | Pedírselas a la persona a cargo del proyecto (ver punto 3) |
| **Barra de uso del plan** | que existan `~\.claude\scripts\barra-estado.ps1` y `avisar-uso.ps1`, y que `~\.claude\settings.json` tenga `statusLine` y el hook `UserPromptSubmit` que los llaman | Copiar los dos scripts desde `.claude\scripts\` del repo a `~\.claude\scripts\` y sumar a `~\.claude\settings.json` (sin pisar lo que ya tenga): `statusLine` con el comando `powershell -NoProfile -ExecutionPolicy Bypass -File "<ruta>\barra-estado.ps1"` y un hook `UserPromptSubmit` igual con `avisar-uso.ps1`. Sin esto el líder no ve el % de uso |
| **Chrome + Claude in Chrome** | `tabs_context_mcp` responde | Opcional: solo para ver la app con el login de Google. Los prototipos se revisan con `playwright-cli` |
| **MCP de IntelliJ** (opcional) | `claude mcp list` muestra el servidor de IntelliJ | En IntelliJ: Settings → Tools → MCP Server → Enable MCP Server → Auto-Configure (Claude Code) y reiniciar Claude Code. Sin esto, los agentes usan Grep |

## 2. Instalar (solo con el OK)
- Mostrá la tabla y preguntá: *"¿Instalo lo que falta?"*. Cada descarga o instalación necesita el OK de la persona, con nombre, origen y tamaño aproximado.
- Instalá de a una y verificá cada una al terminar.
- Al final, `mvn.cmd -q test-compile` tiene que pasar. Es la prueba de que todo quedó bien.

## 3. Claves (nunca por el chat)
Se usan los **mismos usuarios de Google** del proyecto, así que la persona a cargo le pasa al nuevo usuario **2 archivos**, por un canal privado (pendrive, gestor de contraseñas o mensaje privado que después se borra; nunca por un chat abierto, un mail compartido ni el repo):
1. `config/google-oauth.properties`: el cliente OAuth, para el login con Gmail.
2. `config/google-service-account.json`: la clave de la cuenta de servicio, para leer y escribir el Sheet.

No se pasa la carpeta `data/`: tiene el token del login, que es personal de cada PC. Cada usuario inicia sesión con Gmail **una vez**, a mano, en la app.

## 4. Dejar CLAUDE.local.md
Creá `CLAUDE.local.md` (git lo ignora) con las rutas reales de esa PC:

```markdown
# Configuración de esta PC
- Git: `<ruta a git.exe, o "git" si está en el PATH>`
- JDK 21: `<ruta>`; `JAVA_HOME` apunta ahí.
- Preparada con `preparar-entorno` el AAAA-MM-DD.
```

## 5. Contar el resultado
Tabla final `Qué | Listo`, y qué le queda hacer a la persona (pasar las claves o iniciar sesión con Gmail en la app). Después seguí con lo que había pedido.
