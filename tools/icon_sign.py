"""주차 표지판 앱 아이콘. 흰 바탕 + 두꺼운 검정 테두리 + '주차' + 차량.

A: 기존 측면 차량 에셋(assets/vehicle/car-side.png)
B: 참조 이미지처럼 정면 차량
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

REPO = r"C:\Users\dydy7\Desktop\CAR2"
RES = os.path.join(REPO, r"android\app\src\main\res")
FONT = r"C:\Windows\Fonts\NotoSansKR-VF.ttf"
INK = (24, 24, 24, 255)
CLEAR = (0, 0, 0, 0)
S = 4 * 432
U = S / 108.0  # 1dp


def sign(draw):
    w, h = 45 * U, 57 * U
    x0, y0 = (S - w) / 2, (S - h) / 2
    draw.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=6 * U, outline=INK, width=int(4.2 * U))
    font = ImageFont.truetype(FONT, int(16.5 * U))
    font.set_variation_by_axes([900])
    bb = draw.textbbox((0, 0), "주차", font=font)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    draw.text(((S - tw) / 2 - bb[0], y0 + 7.5 * U - bb[1]), "주차", font=font, fill=INK)
    return y0 + 7.5 * U + th, y0 + h - 6 * U  # 차량 영역 위·아래


def car_existing(img, top, bottom):
    car = Image.open(os.path.join(REPO, r"assets\vehicle\car-side.png")).convert("RGBA")
    car = car.crop(car.getbbox())
    width = int(33 * U)
    height = int(car.height * width / car.width)
    car = car.resize((width, height), Image.LANCZOS)
    # 검정으로 통일
    r, g, b, a = car.split()
    solid = Image.new("RGBA", car.size, INK)
    solid.putalpha(a)
    y = int((top + bottom) / 2 - height / 2 + 1 * U)
    img.alpha_composite(solid, ((S - width) // 2, y))


def car_front(draw, top, bottom):
    cx = S / 2
    y0 = top + 3.5 * U
    p = lambda x, y: (cx + x * U, y0 + y * U)
    # 지붕(유리 포함)
    draw.polygon([p(-10, 0), p(10, 0), p(13.5, 8.5), p(-13.5, 8.5)], fill=INK)
    draw.polygon([p(-8, 2), p(8, 2), p(10.5, 7.5), p(-10.5, 7.5)], fill=CLEAR)
    # 차체
    draw.rounded_rectangle([p(-16, 7.5), p(16, 17.5)], radius=2.6 * U, fill=INK)
    # 사이드 미러
    draw.rounded_rectangle([p(-18.5, 6.5), p(-15, 9)], radius=1 * U, fill=INK)
    draw.rounded_rectangle([p(15, 6.5), p(18.5, 9)], radius=1 * U, fill=INK)
    # 전조등·그릴
    draw.ellipse([p(-13, 10.5), p(-8, 14)], fill=CLEAR)
    draw.ellipse([p(8, 10.5), p(13, 14)], fill=CLEAR)
    draw.rounded_rectangle([p(-5, 13.5), p(5, 15)], radius=0.7 * U, fill=CLEAR)
    # 바퀴
    draw.rounded_rectangle([p(-14, 17), p(-8.5, 21.5)], radius=1.2 * U, fill=INK)
    draw.rounded_rectangle([p(8.5, 17), p(14, 21.5)], radius=1.2 * U, fill=INK)


def render(variant):
    img = Image.new("RGBA", (S, S), CLEAR)
    d = ImageDraw.Draw(img)
    top, bottom = sign(d)
    if variant == "A":
        car_existing(img, top, bottom)
    else:
        car_front(d, top, bottom)
    return img


def preview(fg, label):
    bg = Image.new("RGBA", (432, 432), (255, 255, 255, 255))
    full = Image.alpha_composite(bg, fg.resize((432, 432), Image.LANCZOS))
    mask = Image.new("L", (432, 432), 0)
    ImageDraw.Draw(mask).ellipse([18, 18, 414, 414], fill=255)
    out = Image.new("RGBA", (432, 432), (60, 64, 72, 255))
    out.paste(full, (0, 0), mask)
    small = out.resize((96, 96), Image.LANCZOS)
    tile = Image.new("RGBA", (560, 470), (60, 64, 72, 255))
    tile.paste(out, (0, 0))
    tile.paste(small, (450, 170))
    ImageDraw.Draw(tile).text((10, 440), label, fill=(255, 255, 255, 255), font=ImageFont.truetype(FONT, 22))
    return tile


if __name__ == "__main__":
    mode = sys.argv[1]
    if mode == "preview":
        a, b = preview(render("A"), "A: 기존 차량 아이콘"), preview(render("B"), "B: 정면 차량")
        sheet = Image.new("RGBA", (1140, 470), (60, 64, 72, 255))
        sheet.paste(a, (0, 0))
        sheet.paste(b, (580, 0))
        sheet.save(os.path.join(os.environ["TMP"], "claude", "shots", "icon-sign.png"))
    else:
        fg = render(mode)
        for name, px in [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]:
            fg.resize((px, px), Image.LANCZOS).save(os.path.join(RES, f"mipmap-{name}", "ic_launcher_foreground.png"), optimize=True)
    print("ok")
