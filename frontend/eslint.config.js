// Reglas del front (docs/agentes/estilo-codigo.md, sección 4). Corre en el build: `npm run lint`.
// El formato no se revisa acá: lo corrige Prettier (eslint-config-prettier apaga las reglas que chocan).
import js from '@eslint/js';
import comentarios from '@eslint-community/eslint-plugin-eslint-comments/configs';
import prettier from 'eslint-config-prettier/flat';
import reactHooks from 'eslint-plugin-react-hooks';
import { defineConfig, globalIgnores } from 'eslint/config';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default defineConfig([
  // Tipos generados desde OpenAPI (`npm run generar-tipos-api`): no se tocan a mano.
  globalIgnores(['node/', 'node_modules/', 'src/compartido/api/tiposApi.ts']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommendedTypeChecked,
      reactHooks.configs.flat.recommended,
      comentarios.recommended,
    ],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    linterOptions: {
      // Una excepción que ya no hace falta hace fallar el build.
      reportUnusedDisableDirectives: 'error',
    },
    rules: {
      // Las funciones `async` sin `await` (respuestas falsas de los tests) no son un error: las promesas
      // que se olvidan las marca no-floating-promises.
      '@typescript-eslint/require-await': 'off',
      // Excepciones solo por línea y con motivo: `// eslint-disable-next-line <regla> -- HU-013: <motivo>`.
      // Nunca por archivo ni global: solo se permite `eslint-disable-next-line`.
      '@eslint-community/eslint-comments/no-use': [
        'error',
        { allow: ['eslint-disable-next-line'] },
      ],
      '@eslint-community/eslint-comments/require-description': 'error',
      '@eslint-community/eslint-comments/no-unlimited-disable': 'error',
    },
  },
  prettier,
]);
