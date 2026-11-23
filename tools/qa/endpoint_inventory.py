import os, re
root = r"src\main\java"
rows = []
for dirpath, _, files in os.walk(root):
    for fn in files:
        if not fn.endswith(".java"): continue
        p = os.path.join(dirpath, fn)
        text = open(p, encoding="utf-8").read()
        base = ""
        for m in re.finditer(r'@RequestMapping\("([^"]+)"\)', text):
            base = m.group(1)
        # per line to keep method context simple
        for line in text.splitlines():
            m = re.search(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\((.*?)\))?', line)
            if not m: continue
            verb = m.group(1).upper(); arg = m.group(2) or ""
            pm = re.search(r'"([^"]*)"', arg)
            path = pm.group(1) if pm else ""
            rows.append((base+path, verb, fn))
for path, verb, fn in sorted(rows):
    print(f"{verb:7} {path:60} {fn}")
print("TOTAL", len(rows))
