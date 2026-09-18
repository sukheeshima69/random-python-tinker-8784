#!/usr/bin/env python3
"""
Generate 2FA app icons: shield + keyhole mark in warm amber/cream/teal palette.
Outputs: icon25.png (25x25), icon29.png (29x29), icon32.png (32x32), icon512.png (512x512)
Also copies icon25.png to res/ for MIDlet packaging.
"""
import os
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow not found. Installing...")
    os.system(f"{sys.executable} -m pip install Pillow")
    from PIL import Image, ImageDraw

# Palette
BG       = (0x1A, 0x1A, 0x2E)   # dark navy background
AMBER    = (0xFF, 0xD1, 0x66)   # warm amber (shield fill)
CREAM    = (0xFA, 0xF0, 0xD7)   # cream (keyhole highlight)
TEAL     = (0x06, 0xD6, 0xA0)   # teal (shield outline / keyhole)

def draw_shield_keyhole(draw, size, x0, y0, x1, y1):
    """Draw a shield shape with a keyhole inside."""
    cx = (x0 + x1) / 2
    cy = (y0 + y1) / 2
    w = x1 - x0
    h = y1 - y0

    # Shield polygon points (rounded-top shield shape)
    # Top: wide flat, middle: slight curve, bottom: pointed
    shield_pts = [
        (x0 + w*0.05, y0 + h*0.05),      # top-left
        (x1 - w*0.05, y0 + h*0.05),      # top-right
        (x1 - w*0.05, y0 + h*0.55),      # right mid
        (cx,          y1 - h*0.05),       # bottom point
        (x0 + w*0.05, y0 + h*0.55),      # left mid
    ]

    # Draw shield outline (teal)
    outline_width = max(1, int(size * 0.08))
    draw.polygon(shield_pts, fill=AMBER, outline=TEAL)

    # Draw a slightly smaller inner shield for thickness effect
    inset = max(1, int(size * 0.06))
    inner_pts = [
        (x0 + w*0.05 + inset, y0 + h*0.05 + inset),
        (x1 - w*0.05 - inset, y0 + h*0.05 + inset),
        (x1 - w*0.05 - inset, y0 + h*0.55 - inset*0.5),
        (cx,                   y1 - h*0.05 - inset),
        (x0 + w*0.05 + inset, y0 + h*0.55 - inset*0.5),
    ]
    draw.polygon(inner_pts, fill=AMBER)

    # Keyhole: circle + trapezoid
    key_cx = cx
    key_cy = y0 + h * 0.35
    key_r = max(1, w * 0.15)

    # Circle (top of keyhole)
    draw.ellipse(
        [key_cx - key_r, key_cy - key_r,
         key_cx + key_r, key_cy + key_r],
        fill=CREAM
    )

    # Slot below circle (rectangular)
    slot_w = max(1, w * 0.08)
    slot_top = key_cy + key_r * 0.5
    slot_bot = key_cy + key_r * 2.5
    draw.rectangle(
        [key_cx - slot_w, slot_top,
         key_cx + slot_w, slot_bot],
        fill=CREAM
    )

    # Teal dot in the center of the keyhole circle (lock detail)
    dot_r = max(1, key_r * 0.4)
    draw.ellipse(
        [key_cx - dot_r, key_cy - dot_r,
         key_cx + dot_r, key_cy + dot_r],
        fill=TEAL
    )


def generate_icon(size, output_path):
    """Generate a single icon at the given size."""
    img = Image.new('RGB', (size, size), BG)
    draw = ImageDraw.Draw(img)

    margin = max(1, int(size * 0.08))
    draw_shield_keyhole(draw, size, margin, margin, size - margin, size - margin)

    img.save(output_path, 'PNG')
    print(f"  Created: {output_path} ({size}x{size})")


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    res_dir = os.path.join(base, 'res')

    # Ensure res dir exists
    os.makedirs(res_dir, exist_ok=True)

    sizes = [
        (25,  os.path.join(res_dir, 'icon25.png')),
        (29,  os.path.join(res_dir, 'icon29.png')),
        (32,  os.path.join(res_dir, 'icon32.png')),
        (512, os.path.join(base, 'icon512.png')),
    ]

    print("Generating 2FA icons...")
    for size, path in sizes:
        generate_icon(size, path)

    print()
    print("All icons generated!")
    print(f"  MIDlet icon (25px): {os.path.join(res_dir, 'icon25.png')}")
    print(f"  Master icon (512px): {os.path.join(base, 'icon512.png')}")


if __name__ == '__main__':
    main()
