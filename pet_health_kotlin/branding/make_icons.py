"""Generate correctly sized Pet Health brand assets + Android launcher icons."""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
SRC_2X = Path(
    r"C:\Users\Ahmad\.cursor\projects\c-Users-Ahmad-Downloads-PET-HEALTH-MONITORING"
    r"\assets\Alternate___paw_pulse_2x-e0a36437-3f04-4605-976f-d6818647eb47.png"
)
RES = ROOT.parent / "app" / "src" / "main" / "res"


def make_transparent(img: Image.Image) -> Image.Image:
    a = np.array(img.convert("RGBA"), dtype=np.float32)
    r, g, b = a[:, :, 0], a[:, :, 1], a[:, :, 2]
    whiteness = (r + g + b) / 3.0
    chroma = np.maximum(np.maximum(r, g), b) - np.minimum(np.minimum(r, g), b)
    greenish = (g > r + 8) & (g > b + 8) & (g < 200)
    soft_green = (g > r) & (g > b) & (chroma > 8) & (whiteness < 245)

    alpha = np.zeros_like(r)
    alpha = np.where(greenish, 255, alpha)
    alpha = np.where(
        soft_green,
        np.maximum(alpha, np.clip(255 - (whiteness - 180) * 3, 0, 255)),
        alpha,
    )
    a[:, :, 3] = alpha
    mask0 = alpha < 1
    a[mask0, 0] = 0
    a[mask0, 1] = 0
    a[mask0, 2] = 0
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def content_bbox(img: Image.Image, threshold: int = 16):
    arr = np.array(img)
    ys, xs = np.where(arr[:, :, 3] > threshold)
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def split_paw_and_wordmark(cropped: Image.Image):
    mask = np.array(cropped)[:, :, 3] > 16
    row = mask.sum(axis=1)
    h = len(row)
    best = None
    y = int(h * 0.35)
    while y < int(h * 0.85):
        if row[y] < 12:
            start = y
            while y < h and row[y] < 12:
                y += 1
            end = y
            if start > 40 and end < h - 20 and row[:start].max() > 80 and row[end:].max() > 40:
                best = (start, end)
                break
        y += 1
    if best is None:
        paw_end = int(h * 0.58)
        text_start = paw_end + 8
    else:
        paw_end, text_start = best[0] - 2, best[1]
    paw = cropped.crop((0, 0, cropped.width, max(1, paw_end))).crop(
        content_bbox(cropped.crop((0, 0, cropped.width, max(1, paw_end))))
    )
    word = cropped.crop((0, min(cropped.height, text_start), cropped.width, cropped.height))
    if np.array(word)[:, :, 3].max() > 0:
        word = word.crop(content_bbox(word))
    return paw, word


def fit_square(src: Image.Image, size: int, fill: float, bg=(0, 0, 0, 0)) -> Image.Image:
    """Center src in a square canvas. fill = fraction of canvas the longest side should occupy."""
    canvas = Image.new("RGBA", (size, size), bg)
    max_side = max(1, int(size * fill))
    ratio = min(max_side / src.width, max_side / src.height)
    nw, nh = max(1, int(src.width * ratio)), max(1, int(src.height * ratio))
    resized = src.resize((nw, nh), Image.Resampling.LANCZOS)
    x = (size - nw) // 2
    y = (size - nh) // 2
    canvas.paste(resized, (x, y), resized)
    return canvas


def scale_to_height(src: Image.Image, height: int) -> Image.Image:
    ratio = height / src.height
    return src.resize((max(1, int(src.width * ratio)), height), Image.Resampling.LANCZOS)


def write_assets(paw: Image.Image, full: Image.Image):
    drawable = RES / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)

    # In-app: keep NATURAL aspect (no forced square) so Compose alignment is exact
    logo_h = 320
    logo = scale_to_height(full, logo_h)
    logo.save(drawable / "pet_health_logo.png")

    mark_h = 256
    mark = scale_to_height(paw, mark_h)
    mark.save(drawable / "pet_health_mark.png")

    # Also density-aware logo for crisp phones
    for folder, h in [
        ("drawable-hdpi", 160),
        ("drawable-xhdpi", 213),
        ("drawable-xxhdpi", 320),
        ("drawable-xxxhdpi", 426),
    ]:
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        scale_to_height(full, h).save(d / "pet_health_logo.png")
        scale_to_height(paw, int(h * 0.72)).save(d / "pet_health_mark.png")

    # Legacy launcher icons (opaque white bg — reliable on all launchers)
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        out = RES / folder
        out.mkdir(parents=True, exist_ok=True)
        # ~78% fill so mask/round icons don't clip toes or ECG
        icon = fit_square(paw, size, fill=0.78, bg=(255, 255, 255, 255))
        icon.convert("RGBA").save(out / "ic_launcher.png")
        icon.convert("RGBA").save(out / "ic_launcher_round.png")

    # Adaptive foreground: Android safe zone is inner 66%; use ~72% of full canvas
    # so the paw looks balanced inside circular/squircle masks (not tiny, not clipped).
    for folder, size in [
        ("drawable-mdpi", 108),
        ("drawable-hdpi", 162),
        ("drawable-xhdpi", 216),
        ("drawable-xxhdpi", 324),
        ("drawable-xxxhdpi", 432),
    ]:
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        fit_square(paw, size, fill=0.72, bg=(0, 0, 0, 0)).save(d / "ic_launcher_foreground.png")

    print("logo", logo.size, "mark", mark.size)


def main():
    ROOT.mkdir(exist_ok=True)
    full = make_transparent(Image.open(SRC_2X))
    cropped = full.crop(content_bbox(full))
    cropped.save(ROOT / "pet_health_logo_cropped.png")
    paw, word = split_paw_and_wordmark(cropped)
    paw.save(ROOT / "pet_health_paw_transparent.png")
    word.save(ROOT / "pet_health_wordmark_transparent.png")
    print("source full", cropped.size, "paw", paw.size, "word", word.size)
    write_assets(paw, cropped)
    print("done ->", RES)


if __name__ == "__main__":
    main()
