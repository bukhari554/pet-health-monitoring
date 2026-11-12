"""Extract logo variants from PDF + rebuild Android icons with correct alignment."""
from pathlib import Path

import numpy as np
import pymupdf
from PIL import Image

ROOT = Path(__file__).resolve().parent
PDF = Path(
    r"C:\Users\Ahmad\.cursor\projects\c-Users-Ahmad-Downloads-PET-HEALTH-MONITORING"
    r"\attachments\aa00f820-5d0b-42a7-bd0c-2dbe46f19a1c\Alternate___paw_pulse.pdf"
)
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
    greenish = (g > r + 8) & (g > b + 8) & (g < 210)
    soft_green = (g > r) & (g > b) & (chroma > 8) & (whiteness < 245)
    alpha = np.zeros_like(r)
    alpha = np.where(greenish, 255, alpha)
    alpha = np.where(
        soft_green,
        np.maximum(alpha, np.clip(255 - (whiteness - 180) * 3, 0, 255)),
        alpha,
    )
    # Also treat near-black page as transparent if PDF rendered on black
    near_black = (whiteness < 18) & (chroma < 12)
    alpha = np.where(near_black, 0, alpha)
    near_white = (r > 248) & (g > 248) & (b > 248)
    alpha = np.where(near_white, 0, alpha)
    a[:, :, 3] = alpha
    mask0 = alpha < 1
    a[mask0, 0] = 0
    a[mask0, 1] = 0
    a[mask0, 2] = 0
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def content_bbox(img: Image.Image, threshold: int = 16):
    arr = np.array(img)
    ys, xs = np.where(arr[:, :, 3] > threshold)
    if len(xs) == 0:
        return 0, 0, img.width, img.height
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def split_paw_and_wordmark(cropped: Image.Image):
    mask = np.array(cropped)[:, :, 3] > 16
    row = mask.sum(axis=1)
    h = len(row)
    best = None
    y = int(h * 0.30)
    while y < int(h * 0.88):
        if row[y] < 12:
            start = y
            while y < h and row[y] < 12:
                y += 1
            end = y
            if start > 40 and end < h - 15 and row[:start].max() > 80 and row[end:].max() > 40:
                best = (start, end)
                break
        y += 1
    if best is None:
        paw_end = int(h * 0.58)
        text_start = paw_end + 8
    else:
        paw_end, text_start = best[0] - 2, best[1]
    paw = cropped.crop((0, 0, cropped.width, max(1, paw_end)))
    paw = paw.crop(content_bbox(paw))
    word = cropped.crop((0, min(cropped.height, text_start), cropped.width, cropped.height))
    if np.array(word)[:, :, 3].max() > 0:
        word = word.crop(content_bbox(word))
    return paw, word


def fit_square(src: Image.Image, size: int, fill: float, bg=(0, 0, 0, 0)) -> Image.Image:
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


def extract_from_pdf() -> Image.Image | None:
    if not PDF.exists():
        print("PDF missing:", PDF)
        return None
    doc = pymupdf.open(PDF)
    ROOT.mkdir(exist_ok=True)
    best = None
    best_area = 0
    for i, page in enumerate(doc):
        # High-res raster of whole page
        pix = page.get_pixmap(matrix=pymupdf.Matrix(4, 4), alpha=True)
        img = Image.frombytes("RGBA", (pix.width, pix.height), pix.samples)
        img.save(ROOT / f"pdf_page_{i}.png")
        # Embedded images
        for j, info in enumerate(page.get_images(full=True)):
            xref = info[0]
            try:
                extracted = doc.extract_image(xref)
                path = ROOT / f"pdf_embed_{i}_{j}.{extracted['ext']}"
                path.write_bytes(extracted["image"])
                emb = Image.open(path).convert("RGBA")
                emb.save(ROOT / f"pdf_embed_{i}_{j}.png")
                area = emb.width * emb.height
                if area > best_area:
                    best_area = area
                    best = emb
            except Exception as e:
                print("embed fail", e)
        # Prefer page render if larger content after transparency
        t = make_transparent(img)
        bb = content_bbox(t)
        crop = t.crop(bb)
        area = crop.width * crop.height
        if area > best_area and crop.width > 100:
            best_area = area
            best = crop
            crop.save(ROOT / f"pdf_page_{i}_crop.png")
    doc.close()
    return best


def write_assets(paw: Image.Image, full: Image.Image):
    drawable = RES / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)

    # In-app full logo — natural aspect, no square padding
    logo = scale_to_height(full, 320)
    logo.save(drawable / "pet_health_logo.png")
    # Aspect ratio file for Compose reference
    (ROOT / "logo_aspect.txt").write_text(f"{full.width}:{full.height}\n")

    mark = scale_to_height(paw, 256)
    mark.save(drawable / "pet_health_mark.png")

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

    # Launcher legacy: white bg, paw centered ~76% (balanced for round masks)
    for folder, size in {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }.items():
        out = RES / folder
        out.mkdir(parents=True, exist_ok=True)
        icon = fit_square(paw, size, fill=0.76, bg=(255, 255, 255, 255))
        icon.save(out / "ic_launcher.png")
        icon.save(out / "ic_launcher_round.png")

    # Adaptive foreground: transparent, 72% fill inside 108dp canvas
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

    print("logo", logo.size, "mark", mark.size, "aspect", f"{full.width}/{full.height}")


def main():
    ROOT.mkdir(exist_ok=True)
    pdf_img = extract_from_pdf()
    if pdf_img is not None:
        full_src = make_transparent(pdf_img)
        full_src = full_src.crop(content_bbox(full_src))
        print("using PDF extract", full_src.size)
    else:
        full_src = make_transparent(Image.open(SRC_2X)).crop(
            content_bbox(make_transparent(Image.open(SRC_2X)))
        )
        print("using PNG fallback", full_src.size)

    full_src.save(ROOT / "pet_health_logo_cropped.png")
    paw, word = split_paw_and_wordmark(full_src)
    paw.save(ROOT / "pet_health_paw_transparent.png")
    word.save(ROOT / "pet_health_wordmark_transparent.png")
    print("paw", paw.size, "word", word.size)
    write_assets(paw, full_src)
    # cleanup temp
    for p in ROOT.glob("_tmp_*"):
        p.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
