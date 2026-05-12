#!/usr/bin/env python3
"""Restructure test directory to match the new layered architecture."""
import os
import shutil

TEST_BASE = "/Users/leexd/suno/src/test/java/com/recycle/mall"

# Package rename mapping - same as main source
PACKAGE_MAP = [
    ("com.recycle.mall.interfaces.http", "com.recycle.mall.controller"),
    ("com.recycle.mall.interfaces.scheduler", "com.recycle.mall.controller"),
    ("com.recycle.mall.interfaces.config", "com.recycle.mall.config"),
    ("com.recycle.mall.application.support", "com.recycle.mall.service.support"),
    ("com.recycle.mall.infrastructure.entity", "com.recycle.mall.entity"),
    ("com.recycle.mall.infrastructure.repository", "com.recycle.mall.dao"),
    ("com.recycle.mall.infrastructure.provider.audit", "com.recycle.mall.provider"),
    ("com.recycle.mall.infrastructure.provider.logistics", "com.recycle.mall.provider"),
    ("com.recycle.mall.infrastructure.provider", "com.recycle.mall.provider"),
    ("com.recycle.mall.infrastructure", "com.recycle.mall.entity"),
    ("com.recycle.mall.application", "com.recycle.mall.service"),
    ("com.recycle.mall.domain.service", "com.recycle.mall.service"),
    ("com.recycle.mall.domain.model", "com.recycle.mall.entity"),
    ("com.recycle.mall.domain", "com.recycle.mall.entity"),
]

# Step 1: Create target directories
os.makedirs(os.path.join(TEST_BASE, "controller"), exist_ok=True)
os.makedirs(os.path.join(TEST_BASE, "service"), exist_ok=True)

# Step 2: Move files
moves = []

# interfaces/http/* -> controller/
http_dir = os.path.join(TEST_BASE, "interfaces/http")
if os.path.exists(http_dir):
    for f in os.listdir(http_dir):
        if f.endswith(".java"):
            moves.append((f"interfaces/http/{f}", f"controller/{f}"))

# application/* -> service/
app_dir = os.path.join(TEST_BASE, "application")
if os.path.exists(app_dir):
    for f in os.listdir(app_dir):
        if f.endswith(".java"):
            moves.append((f"application/{f}", f"service/{f}"))

print("=== Moving test files ===")
for src, dst in moves:
    src_path = os.path.join(TEST_BASE, src)
    dst_path = os.path.join(TEST_BASE, dst)
    if os.path.exists(src_path):
        shutil.copy2(src_path, dst_path)
        print(f"  {src} -> {dst}")

# Step 3: Update package references in all test files
print("\n=== Updating package references ===")
count = 0
for root, dirs, files in os.walk(TEST_BASE):
    # Skip old directories
    rel_root = os.path.relpath(root, TEST_BASE)
    if rel_root.startswith("interfaces/") or rel_root.startswith("application/"):
        continue
    for f in files:
        if not f.endswith(".java"):
            continue
        filepath = os.path.join(root, f)
        try:
            with open(filepath, "r", encoding="utf-8") as fh:
                content = fh.read()
        except Exception as e:
            print(f"  ERROR reading {filepath}: {e}")
            continue

        original = content
        for old_pkg, new_pkg in PACKAGE_MAP:
            content = content.replace(f"package {old_pkg};", f"package {new_pkg};")
            content = content.replace(f"import {old_pkg}.", f"import {new_pkg}.")

        if content != original:
            with open(filepath, "w", encoding="utf-8") as fh:
                fh.write(content)
            rel = os.path.relpath(filepath, TEST_BASE)
            print(f"  Fixed: {rel}")
            count += 1

# Step 4: Delete old directories
print("\n=== Cleaning up old test directories ===")
for d in ["interfaces", "application"]:
    dp = os.path.join(TEST_BASE, d)
    if os.path.exists(dp):
        shutil.rmtree(dp)
        print(f"  Removed: {d}/")

print(f"\nDone! Fixed {count} files.")
