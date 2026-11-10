#!/usr/bin/env node
/**
 * 前端模板体检脚本
 * ---------------------------------------------------------------
 * 作用：在浏览器之前就发现 index.html 里的模板问题。
 *
 * 检查项：
 *   1. 属性里的单引号是否成对（写文件时容易掉引号）
 *   2. Vue 模板能否编译
 *   3. 生成的 render function 能否通过 JS 语法检查
 *
 * 用法：
 *   node check-template.mjs <index.html 路径> [browser_tpl.html 路径]
 */
import fs from 'fs';
import path from 'path';
import { createRequire } from 'module';
import { pathToFileURL } from 'url';

// 依赖装在共享 node workspace 里；ESM 不认 NODE_PATH，这里手动兜底找一下
const require = createRequire(import.meta.url);
const WORKSPACE = 'C:/Users/登锋造极/.workbuddy/binaries/node/workspace';

let compile, parse;
try {
    ({ compile } = await import('@vue/compiler-dom'));
    ({ parse } = await import('acorn'));
} catch (e) {
    const nm = path.join(WORKSPACE, 'node_modules');
    ({ compile } = await import(pathToFileURL(path.join(nm, '@vue/compiler-dom', 'dist/compiler-dom.esm-bundler.js')).href));
    ({ parse } = await import(pathToFileURL(path.join(nm, 'acorn', 'dist/acorn.mjs')).href));
}

const target = process.argv[2] || '../../src/main/resources/static/index.html';
const html = fs.readFileSync(target, 'utf8');

let failed = false;

/* ---- 1. 属性引号配对 ---- */
let unbalanced = 0;
for (const m of html.matchAll(/([:@\w-]+)="([^"]*)"/g)) {
    const [, name, val] = m;
    if ((val.match(/'/g) || []).length % 2 !== 0) {
        const line = html.slice(0, m.index).split('\n').length;
        console.error(`[FAIL] 第 ${line} 行属性 ${name} 的单引号不配对: ${val}`);
        unbalanced++;
    }
}
if (unbalanced === 0) console.log('[OK] 属性引号全部配对');
else failed = true;

/* ---- 2/3. 模板编译 + 生成代码语法 ---- */
const start = html.indexOf('<div id="app"');
const bodyStart = html.indexOf('>', start) + 1;
const end = html.lastIndexOf('</div>');
const tpl = html.slice(bodyStart, end);

try {
    const { code } = compile(tpl, { mode: 'function', onError(e) { throw e; } });
    // Vue 运行时用 new Function('Vue', code) 执行，等价于把它当函数体
    try {
        parse('function __w(){\n' + code + '\n}', { ecmaVersion: 2020 });
        console.log('[OK] 模板编译通过，生成的 render function 语法有效');
    } catch (e) {
        console.error('[FAIL] 生成的 render function 语法错误: ' + e.message);
        const pos = e.pos - 'function __w(){\n'.length;
        console.error('       附近代码: ' + JSON.stringify(code.slice(Math.max(0, pos - 160), pos + 40)));
        failed = true;
    }
} catch (e) {
    console.error('[FAIL] 模板编译失败: ' + e.message);
    if (e.loc) {
        const lines = tpl.split('\n');
        const ln = e.loc.start.line;
        for (let i = Math.max(0, ln - 4); i < Math.min(lines.length, ln + 2); i++) {
            console.error((i + 1 === ln ? '  >>> ' : '      ') + (i + 1) + ': ' + lines[i]);
        }
    }
    failed = true;
}

process.exit(failed ? 1 : 0);
