"""One-off tool to (re)generate the KSP app's launcher icon assets and its flat 200x200 listing
icon (for KPay's app-store submission requirement) from the two source files in
store-assets/source/. Not part of the Gradle build -- run manually (after `pip install Pillow`)
only when the logo changes.

Source files:
  - source/logo.png             flat "KS" mark on a plain white square, no rounding -- used for
                                 the app's 200x200 listing icon (a plain square is safe against
                                 whatever corner-masking KPay's submission platform applies).
  - source/transparent-logo.png the same mark with real alpha transparency -- used for the
                                 Android adaptive-icon foreground layer, which Android composites
                                 over its own background/shape.
"""

import os

from PIL import Image

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res")
SOURCE_DIR = os.path.dirname(os.path.abspath(__file__)) + os.sep + "source"

# density bucket -> scale factor relative to mdpi (Android's standard buckets)
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

ADAPTIVE_CANVAS_DP = 108
# Android's adaptive-icon safe zone: content should stay within a 66dp circle centered in the
# 108dp canvas so it isn't clipped by circle/squircle/rounded-square OEM launcher masks.
SAFE_ZONE_RATIO = 66 / 108

LEGACY_CANVAS_DP = 48
# Legacy (pre-adaptive-icon-aware) surfaces don't mask the icon, so more of the canvas is usable.
LEGACY_FILL_RATIO = 0.80

WHITE = (255, 255, 255, 255)


def load_trimmed_mark(path: str) -> Image.Image:
    """Loads an RGBA source and crops to the tight bounding box of its non-transparent content."""
    img = Image.open(path).convert("RGBA")
    bbox = img.getbbox()
    return img.crop(bbox) if bbox else img


def fit_onto_transparent_canvas(mark: Image.Image, canvas_px: int, fill_ratio: float) -> Image.Image:
    target = int(round(canvas_px * fill_ratio))
    scaled = mark.copy()
    scaled.thumbnail((target, target), Image.LANCZOS)
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    offset = ((canvas_px - scaled.width) // 2, (canvas_px - scaled.height) // 2)
    canvas.alpha_composite(scaled, offset)
    return canvas


def to_monochrome(layer: Image.Image) -> Image.Image:
    """Android 13+ themed-icon layer: same alpha shape, solid white RGB."""
    alpha = layer.getchannel("A")
    mono = Image.new("RGBA", layer.size, (255, 255, 255, 0))
    mono.putalpha(alpha)
    return mono


def composite_on_white(mark: Image.Image, canvas_px: int, fill_ratio: float) -> Image.Image:
    transparent = fit_onto_transparent_canvas(mark, canvas_px, fill_ratio)
    background = Image.new("RGBA", (canvas_px, canvas_px), WHITE)
    background.alpha_composite(transparent)
    return background.convert("RGB")


def circular_crop(square_rgb: Image.Image) -> Image.Image:
    size = square_rgb.size
    mask = Image.new("L", size, 0)
    from PIL import ImageDraw

    ImageDraw.Draw(mask).ellipse((0, 0, size[0] - 1, size[1] - 1), fill=255)
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    out.paste(square_rgb, (0, 0), mask)
    return out


def main() -> None:
    mark = load_trimmed_mark(os.path.join(SOURCE_DIR, "transparent-logo.png"))

    for density, scale in DENSITIES.items():
        mipmap_dir = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(mipmap_dir, exist_ok=True)

        adaptive_px = int(round(ADAPTIVE_CANVAS_DP * scale))
        foreground = fit_onto_transparent_canvas(mark, adaptive_px, SAFE_ZONE_RATIO)
        foreground.save(os.path.join(mipmap_dir, "ic_launcher_foreground.png"))
        to_monochrome(foreground).save(os.path.join(mipmap_dir, "ic_launcher_monochrome.png"))

        legacy_px = int(round(LEGACY_CANVAS_DP * scale))
        legacy = composite_on_white(mark, legacy_px, LEGACY_FILL_RATIO)
        legacy.save(os.path.join(mipmap_dir, "ic_launcher.png"))
        circular_crop(legacy).save(os.path.join(mipmap_dir, "ic_launcher_round.png"))

        # Drop the stock template's .webp files now superseded by the .png files above.
        for stale in ("ic_launcher.webp", "ic_launcher_round.webp"):
            stale_path = os.path.join(mipmap_dir, stale)
            if os.path.exists(stale_path):
                os.remove(stale_path)

        print(f"{density}: adaptive={adaptive_px}px legacy={legacy_px}px")

    listing_icon = Image.open(os.path.join(SOURCE_DIR, "logo.png")).convert("RGB")
    listing_icon = listing_icon.resize((200, 200), Image.LANCZOS)
    listing_icon.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), "ksp-app-icon-200x200.png"))
    print("KSP app listing icon: 200x200 saved")


if __name__ == "__main__":
    main()
