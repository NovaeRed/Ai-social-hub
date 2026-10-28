import js from '@eslint/js';
import vue from 'eslint-plugin-vue';
import ts from 'typescript-eslint';
import prettier from 'eslint-config-prettier';
export default ts.config(
  { ignores: ['dist/**', 'node_modules/**', 'artifacts/**', '.local/**'] },
  js.configs.recommended,
  ...ts.configs.recommended,
  ...vue.configs['flat/recommended'],
  prettier,
  {
    files: ['src/**/*.{ts,vue}'],
    languageOptions: {
      parserOptions: { parser: ts.parser },
      globals: {
        window: 'readonly',
        document: 'readonly',
        console: 'readonly',
        fetch: 'readonly',
        sessionStorage: 'readonly',
        localStorage: 'readonly',
        URL: 'readonly',
        URLSearchParams: 'readonly',
        AbortController: 'readonly',
        AbortSignal: 'readonly',
        File: 'readonly',
        FormData: 'readonly',
        Blob: 'readonly',
        Event: 'readonly',
        HTMLElement: 'readonly',
        HTMLInputElement: 'readonly',
        HTMLTextAreaElement: 'readonly',
        HTMLButtonElement: 'readonly',
        KeyboardEvent: 'readonly',
        RequestInit: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
      },
    },
    rules: {
      'no-undef': 'off',
      'no-constant-condition': ['error', { checkLoops: 'allExceptWhileTrue' }],
      'no-console': 'error',
      'no-debugger': 'error',
      'vue/multi-word-component-names': ['error', { ignores: ['App'] }],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      'vue/component-name-in-template-casing': ['error', 'PascalCase'],
      'vue/attribute-hyphenation': ['error', 'always'],
    },
  },
);
