#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
后端改动体检脚本
- 校验所有 Mapper XML 格式良好（MyBatis 在启动时解析，格式错 = 整个应用起不来）
- 提示 MySQL 保留字（rank/result 等）在 SQL 里的裸用
用法：python check-mappers.py <项目 src/main/resources 目录>
"""
import os
import re
import sys
import xml.dom.minidom

RES = sys.argv[1] if len(sys.argv) > 1 else '.'
MAPPER_DIR = os.path.join(RES, 'mappers')

# MySQL 8 保留字：这些词在 SQL 里当别名/标识符必须加反引号
RESERVED = {'rank', 'result', 'groups', 'order', 'system', 'key', 'index', 'rows',
             'select', 'from', 'where', 'table', 'desc', 'asc', 'read', 'default'}

failed = 0
checked = 0

for name in sorted(os.listdir(MAPPER_DIR)):
    if not name.endswith('.xml'):
        continue
    path = os.path.join(MAPPER_DIR, name)
    checked += 1
    try:
        xml.dom.minidom.parse(path)
    except Exception as e:
        print('[FAIL] %s XML 格式错误: %s' % (name, e))
        failed += 1
        continue

    src = open(path, encoding='utf-8').read()
    # 找 SQL 片段里的裸保留字别名
    for m in re.finditer(r'\bAS\s+([a-zA-Z_][a-zA-Z0-9_]*)\b', src):
        word = m.group(1).lower()
        if word in RESERVED:
            line = src[:m.start()].count('\n') + 1
            print('[FAIL] %s 第 %d 行: AS %s 是 MySQL 保留字，必须写成 AS `%s`'
                  % (name, line, m.group(1), m.group(1)))
            failed += 1

    # 找 SQL 里裸用的小于号（XML 里必须转义成 &lt;）
    for m in re.finditer(r'&lt;', src):
        pass
    for m in re.finditer(r'<(?![a-zA-Z/!?])', src):
        line = src[:m.start()].count('\n') + 1
        ctx = src[max(0, m.start() - 40):m.start() + 20].replace('\n', ' ')
        if 'net_time' in ctx or '<' in ctx:
            print('[WARN] %s 第 %d 行疑似未转义的 <: ...%s...' % (name, line, ctx))

if failed == 0:
    print('[OK] %d 个 Mapper XML 格式与保留字检查通过' % checked)
else:
    print('[FAIL] 共 %d 处问题' % failed)

sys.exit(1 if failed else 0)
