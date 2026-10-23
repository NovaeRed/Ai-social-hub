import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = await readFile(new URL('../src/lib/presentation.ts', import.meta.url), 'utf8');
const js = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText;
export const presentationUrl = 'data:text/javascript;base64,' + Buffer.from(js).toString('base64');
export const presentation = await import(presentationUrl);
