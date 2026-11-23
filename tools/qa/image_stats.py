from PIL import Image
import glob, os

for p in sorted(glob.glob(r"tools\qa\out\ui\*.png")):
    im = Image.open(p).convert("RGB")
    px = list(im.getdata())
    n = len(px)
    nonwhite = sum(1 for r, g, b in px if not (r > 245 and g > 245 and b > 245))
    colors = len(set(px))
    print(f"{os.path.basename(p):34} {im.size} colors={colors:5} nonwhite={nonwhite/n:.2%}")
