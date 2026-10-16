# Investigación: herramientas y forma de armar el equipo de agentes (2026-10-15)

> Pedido de la dueña antes de HU-012: buscar herramientas, skills y prácticas nuevas que mejoren al equipo. El líder investigó sin agentes. Este archivo guarda las conclusiones; las decisiones van a `estado.md`.

## 1. Fuentes
- Catálogo oficial de plugins de Anthropic, descargado en la PC (`~/.claude/plugins/marketplaces/claude-plugins-official`, ~55 plugins).
- Docs oficiales de Claude Code: [subagentes](https://code.claude.com/docs/en/sub-agents), [skills](https://code.claude.com/docs/en/skills), [hooks](https://code.claude.com/docs/en/hooks), [buenas prácticas](https://code.claude.com/docs/en/best-practices).
- Anthropic: [context engineering para agentes](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents).
- [MCP de IntelliJ](https://www.jetbrains.com/help/idea/mcp-server.html), [Playwright CLI](https://playwright.dev/java/docs/getting-started-cli).
- Comunidad: BMAD Method y GitHub Spec Kit (desarrollo guiado por especificación). Los rankings de blogs se usaron solo como pista: son de marketing y casi no hablan de Java.

## 2. Cómo se arma bien un equipo de agentes (y cómo estamos)
| Práctica recomendada | Nosotros | Qué falta |
|---|---|---|
| Especificación escrita antes de programar (Spec Kit, BMAD) | HU + contrato primero + documento funcional | Nada: estamos alineados |
| Roles separados con contexto aislado (BMAD) | 6 agentes por rol, contexto por archivo | Nada |
| El subagente devuelve un resumen corto (1-2 mil tokens) | 150 palabras con formato fijo | Nada |
| Notas persistentes fuera del contexto | `estado.md`, épicas, registro de la HU | Nada |
| Tras 2 correcciones fallidas, empezar de cero | Máximo 2 vueltas; agente nuevo por vuelta | Nada |
| **Darle al agente una forma de verificarse** y que muestre la evidencia (el comando y su resultado), no que diga "pasa" | Regla escrita "verificar antes de entregar" | Pedir la **evidencia** en la respuesta (línea de resultado de Maven/Vitest) |
| **Revisión independiente** (escritor/revisor): otro agente, sin el historial, revisa el diff y solo marca errores reales | No hay: la dueña acepta sin poder leer código | `/code-review` (incluido) antes de la aceptación |
| **Hooks/permisos para lo que tiene que pasar siempre**; `CLAUDE.md` es solo consejo | Reglas críticas solo escritas (claves de `config/`, commit, `-m`) | Permisos en `.claude/settings.json` |
| **`CLAUDE.md` corto**: "si una regla larga se pierde, el archivo es muy largo"; los flujos que se usan a veces van en skills | ~13 mil caracteres; la mitad es "Modo líder", que los subagentes cargan sin usarla | Mover "Modo líder" y "Consumo de tokens" a la skill del líder (propuesta aparte) |
| Herramienta de código para lenguajes tipados (navegar símbolos, errores al editar) | Solo Grep | MCP de IntelliJ (ya instalado en la PC) |

## 3. Herramientas evaluadas
| Herramienta | Veredicto | Por qué |
|---|---|---|
| **Playwright CLI** (`@playwright/cli`) | ✅ Instalada (Node 24 LTS con winget + `npm i -g`) y probada con el prototipo de Configuración | Para prototipos y pantallas sin login: guarda capturas y snapshots en disco en vez de cargarlos al contexto (≈4 veces menos tokens según la doc de Playwright). `show --annotate` deja que la dueña marque la pantalla con comentarios. Chrome sigue para lo que necesita su sesión de Google |
| **MCP de IntelliJ 2026.1** | ✅ A activar (lo hace la dueña, 3 clics) | Renombrar en todo el proyecto, errores de un archivo, correr tests. Gratis, ya instalado. Si IntelliJ está cerrado, el agente sigue con Grep |
| `/code-review` (incluido) | ✅ Sumar al cierre | Revisor independiente; pedirle solo errores de corrección para no sobre-diseñar |
| `maxTurns` en los agentes | ✅ | Si un agente se alarga, entrega parcial y se puede retomar, en vez de cortarse y perder el trabajo |
| Permisos `deny`/`ask` | ✅ | Claves de `config/` bloqueadas de verdad; commit y push piden confirmación; `git commit -m` bloqueado |
| Ideas de `code-modernization` | ✅ Copiar, sin instalar | En refactors: "test que rompe a propósito" (un cambio de una línea tiene que poner algún test en rojo) y tests corridos de cero |
| Plugin `code-modernization` completo | ❌ | Muchos agentes en paralelo (caro), pide `sh`/`python3`, proceso propio que choca con el nuestro |
| LSP de Java (jdtls), Serena | ❌ | IntelliJ ya lo cubre sin instalar nada |
| Context7 | ❌ | Leer la doc oficial ya sale barato |
| `memory` por agente | ❌ | Duplica `estado.md` y se carga en cada llamada |
| `security-guidance` | ⏸️ | Usar `/security-review` antes de distribuir a otros comercios |
| `omitClaudeMd` en agentes | ⏸️ | Se vuelve innecesario si `CLAUDE.md` adelgaza |

## 4. Trampa encontrada
El modo automático de Claude Code no deja que el líder edite `.claude/settings.json` (lo frena como "auto-modificación"), ni siquiera con permiso por chat. Para cambiar la configuración del equipo: la dueña pasa a modo manual (`Shift+Tab`) y aprueba cada cambio en pantalla.
