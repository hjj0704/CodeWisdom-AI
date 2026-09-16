#!/usr/bin/env node
/**
 * 用 **Mermaid 官方解析器**校验 stdin 传入的图，语法通过退出码 0，否则退出码 1。
 *
 * 为什么要这个脚本：`docs/acceptance.md` 阶段 4 的要求是「能被 Mermaid 官方解析器成功渲染，
 * 不是只生成字符串」。自己写的正则或括号配平检查**不算**——它只能发现「我觉得像错的」写法，
 * 发现不了自己没预料到的语法错误。这里跑的是 mermaid 包里的 jison 语法，
 * 与前端渲染用的是同一份解析器。
 *
 * 用法：
 *   echo 'flowchart TD
 *     A --> B' | node verify.mjs
 *
 * 依赖：mermaid（图语法解析）、jsdom（mermaid 的解析过程需要 DOM，
 * 无 DOM 时会在解析**之后**的消毒环节抛 `DOMPurify.addHook is not a function`——
 * 那是环境缺 DOM，不是语法错误，不能当成校验通过）。
 */
import { JSDOM } from 'jsdom';

const dom = new JSDOM('<!DOCTYPE html><body></body>', { pretendToBeVisual: true });
globalThis.window = dom.window;
globalThis.document = dom.window.document;
// Node 24 起 navigator 是只读全局，直接赋值会抛 TypeError，只能用 defineProperty 覆盖
Object.defineProperty(globalThis, 'navigator', { value: dom.window.navigator, configurable: true });

const mermaid = (await import('mermaid')).default;
mermaid.initialize({ startOnLoad: false, securityLevel: 'loose' });

const text = await new Promise((resolve, reject) => {
  let buffer = '';
  process.stdin.setEncoding('utf8');
  process.stdin.on('data', (chunk) => (buffer += chunk));
  process.stdin.on('end', () => resolve(buffer));
  process.stdin.on('error', reject);
});

try {
  await mermaid.parse(text);
  process.stdout.write('PARSE_OK\n');
  process.exit(0);
} catch (error) {
  const firstLine = String(error?.message ?? error).split('\n')[0];
  process.stdout.write(`PARSE_FAIL ${firstLine}\n`);
  process.exit(1);
}
