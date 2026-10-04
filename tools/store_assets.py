"""Google Play 등록용 이미지. 실제 에뮬레이터·휴대폰 캡처 + 앱 아이콘으로 구성한다(가짜 화면을 그리지 않음).

사용:
  python tools/store_assets.py icon infographic   # 캡처 없이 다시 만들 수 있는 것만
  python tools/store_assets.py feature screens    # SHOTS 폴더에 store-*.png 캡처가 있어야 함
  python tools/store_assets.py all                # 전부

Play 규격: 아이콘 512×512 32비트 PNG(알파 포함, 1MB 이하), 그래픽 이미지 1024×500 24비트 PNG(알파 없음),
스크린샷 9:16 1080×1920. 마지막에 check()가 규격을 확인한다."""
import os
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(__file__))
import icon_sign  # noqa: E402

SHOTS = os.path.join(os.environ["TMP"], "claude", "shots")
OUT = r"C:\Users\dydy7\Desktop\CAR2\store"
FONT = r"C:\Windows\Fonts\NotoSansKR-VF.ttf"
os.makedirs(os.path.join(OUT, "screenshots"), exist_ok=True)

GRAPHITE = (36, 38, 41)
PEARL = (250, 250, 247)
SILVER = (185, 190, 195)
INK = (30, 31, 33)
SUB = (101, 103, 106)
GREEN = (30, 122, 76)
BURGUNDY = (142, 42, 59)


def font(size, weight=700):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_axes([weight])
    return f


def text_center(d, cx, y, text, f, fill):
    bb = d.textbbox((0, 0), text, font=f)
    d.text((cx - (bb[2] - bb[0]) / 2 - bb[0], y - bb[1]), text, font=f, fill=fill)
    return y + (bb[3] - bb[1])


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius=radius, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


# 캡처별로 지울 상태바 구간. 저장 전에 찍은 패널 캡처에는 B2 표지판이 아직 없어 시계 뒤를 모두 지운다
CLEAN = {"store-drawer-forest.png": [(150, 404)]}


def declutter(shot, name=None):
    """에뮬레이터 상태바에서 다른 앱·시스템 알림 아이콘을 지운다. 시계와 이 앱의 B2 표지판만 남긴다.
    0.3.12 캡처(1080×2400) 기준: 시계 x 82~163, B2 표지판 181~219, 그 뒤(안전 센터·개발자·다른 앱) 245~393.
    캡처를 바꾸면 위치를 다시 재서 고친다"""
    shot = shot.copy()
    d = ImageDraw.Draw(shot)
    bg = shot.getpixel((520, 68))
    for x0, x1 in CLEAN.get(name, [(236, 404)]):
        d.rectangle([x0, 40, x1, 98], fill=bg)
    return shot


def phone(shot_path, width):
    """캡처를 둥근 기기 틀에 넣는다"""
    shot = declutter(Image.open(shot_path).convert("RGBA"), os.path.basename(shot_path))
    h = int(shot.height * width / shot.width)
    shot = rounded(shot.resize((width, h), Image.LANCZOS), int(width * 0.07))
    pad = int(width * 0.028)
    frame = Image.new("RGBA", (width + pad * 2, h + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle([0, 0, frame.width - 1, frame.height - 1], radius=int(width * 0.09), fill=(18, 18, 20, 255))
    frame.alpha_composite(shot, (pad, pad))
    shadow = Image.new("RGBA", (frame.width + 80, frame.height + 80), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([40, 50, frame.width + 40, frame.height + 50], radius=int(width * 0.09), fill=(0, 0, 0, 70))
    shadow = shadow.filter(ImageFilter.GaussianBlur(24))
    shadow.alpha_composite(frame, (40, 40))
    return shadow


def icon(size, bg=(255, 255, 255, 255)):
    fg = icon_sign.render("B").resize((size, size), Image.LANCZOS)
    base = Image.new("RGBA", (size, size), bg)
    base.alpha_composite(fg)
    return base


def screenshot_card(name, shot, title, sub, bg=PEARL, crop_top=None):
    W, H = 1080, 1920
    card = Image.new("RGBA", (W, H), bg + (255,))
    d = ImageDraw.Draw(card)
    y = text_center(d, W / 2, 120, title, font(64, 800), INK)
    text_center(d, W / 2, y + 36, sub, font(36, 500), SUB)
    if crop_top:
        # 상태바 확대: 캡처 상단을 크게 보여준다
        src = declutter(Image.open(os.path.join(SHOTS, shot)).convert("RGBA"), shot)
        strip = src.crop((0, 0, src.width, crop_top)).resize((W - 120, int(crop_top * (W - 120) / src.width)), Image.LANCZOS)
        strip = rounded(strip, 28)
        card.alpha_composite(strip, (60, 360))
        ph = phone(os.path.join(SHOTS, shot), 600)
        card.alpha_composite(ph, ((W - ph.width) // 2, 360 + strip.height + 40))
    else:
        ph = phone(os.path.join(SHOTS, shot), 720)
        card.alpha_composite(ph, ((W - ph.width) // 2, 330))
    card.convert("RGB").save(os.path.join(OUT, "screenshots", name), optimize=True)


def feature_graphic():
    W, H = 1024, 500
    g = Image.new("RGBA", (W, H), GRAPHITE + (255,))
    d = ImageDraw.Draw(g)
    ic = rounded(icon(190), 44)
    g.alpha_composite(ic, (56, (H - 190) // 2))
    d.text((280, 132), "주차기록", font=font(72, 900), fill=PEARL)
    d.text((282, 236), "차에서 내리면, 층수가 기록돼요", font=font(31, 600), fill=PEARL)
    d.text((282, 290), "자동 표시 · 층수 릴 · 상태바 · 위젯", font=font(24, 500), fill=SILVER)
    d.line([(282, 340), (500, 340)], fill=SILVER, width=3)
    ph = phone(os.path.join(SHOTS, "store-home-forest.png"), 210)
    g.alpha_composite(ph.crop((0, 0, ph.width, H - 30)), (W - ph.width + 10, 26))
    g.convert("RGB").save(os.path.join(OUT, "feature-graphic-1024x500.png"), optimize=True)


def feature_icon(kind, size, color):
    """기능 카드용 단순 기호"""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    s = size
    w = max(4, s // 14)
    if kind == "bt":
        pts = [(s * .3, s * .3), (s * .7, s * .68), (s * .5, s * .86), (s * .5, s * .14), (s * .7, s * .32), (s * .3, s * .7)]
        d.line(pts, fill=color, width=w, joint="curve")
    elif kind == "reel":
        for i, (yy, ww) in enumerate([(.2, .5), (.42, .8), (.64, .5)]):
            box = [s * (1 - ww) / 2, s * yy, s * (1 + ww) / 2, s * (yy + .17)]
            if i == 1:
                d.rounded_rectangle(box, radius=s * .05, fill=color)
            else:
                d.rounded_rectangle(box, radius=s * .05, outline=color, width=w // 2 + 1)
    elif kind == "bar":
        d.rounded_rectangle([s * .12, s * .3, s * .88, s * .7], radius=s * .1, outline=color, width=w)
        d.rounded_rectangle([s * .2, s * .38, s * .46, s * .62], radius=s * .05, fill=color)
    elif kind == "pin":
        d.ellipse([s * .28, s * .12, s * .72, s * .56], fill=color)
        d.polygon([(s * .3, s * .42), (s * .7, s * .42), (s * .5, s * .88)], fill=color)
        d.ellipse([s * .42, s * .26, s * .58, s * .42], fill=(0, 0, 0, 0))
    elif kind == "camera":
        d.rounded_rectangle([s * .12, s * .3, s * .88, s * .8], radius=s * .1, fill=color)
        d.rectangle([s * .36, s * .2, s * .64, s * .32], fill=color)
        d.ellipse([s * .34, s * .4, s * .66, s * .72], fill=(0, 0, 0, 0))
        d.ellipse([s * .41, s * .47, s * .59, s * .65], fill=color)
    elif kind == "clock":
        d.ellipse([s * .14, s * .14, s * .86, s * .86], outline=color, width=w)
        d.line([(s * .5, s * .5), (s * .5, s * .28)], fill=color, width=w)
        d.line([(s * .5, s * .5), (s * .66, s * .6)], fill=color, width=w)
    elif kind == "widget":
        for (x, y) in [(.14, .14), (.54, .14), (.14, .54)]:
            d.rounded_rectangle([s * x, s * y, s * (x + .32), s * (y + .32)], radius=s * .06, fill=color)
        d.rounded_rectangle([s * .54, s * .54, s * .86, s * .86], radius=s * .06, outline=color, width=w // 2 + 1)
    elif kind == "palette":
        # 다섯 가지 디자인: 위 3개 · 아래 2개 원
        cols = palette_icon_colors()
        r = s * .155
        spots = [(.18, .32), (.5, .32), (.82, .32), (.34, .7), (.66, .7)]
        for (x, y), c in zip(spots, cols):
            d.ellipse([s * x - r, s * y - r, s * x + r, s * y + r], fill=c + (255,), outline=(250, 250, 247, 255), width=2)
    return im


def palette_icon_colors():
    """디자인 카드 아이콘: 앱의 다섯 테마 대표색(그래파이트·포레스트·에스프레소·실버·UHD 네온 핑크)"""
    return [(36, 38, 41), (32, 63, 54), (59, 48, 43), (185, 190, 195), (255, 46, 136)]


def infographic():
    """핵심 기능 소개 카드 (1080×1920, Play 스크린샷 첫 장으로도 사용 가능)"""
    W, H = 1080, 1920
    g = Image.new("RGBA", (W, H), PEARL + (255,))
    d = ImageDraw.Draw(g)
    ic = rounded(icon(180), 40)
    g.alpha_composite(ic, ((W - 180) // 2, 90))
    y = text_center(d, W / 2, 310, "주차기록", font(76, 900), INK)
    text_center(d, W / 2, y + 30, "차에서 내리면 층수가 기록돼요", font(38, 600), SUB)
    cards = [
        ("bt", "자동 표시", "등록 차량 Bluetooth가 끊기면\n알림 없이 앱이 바로 떠요"),
        ("reel", "층수 릴", "세로 릴을 돌려 B2·1F를\n한 번에 저장"),
        ("bar", "상태바 층수", "저장한 층을 상태바에 표시\n집 근처는 기본 켜짐"),
        ("clock", "주차 경과 시간", "2시간부터 초록,\n3시간부터 버건디로 알려줘요"),
        ("camera", "주차 사진", "기둥·구역 번호를 찍어두고\n크게 확인"),
        ("pin", "위치 저장", "아이콘 한 번으로\n지금 위치를 주차 위치로"),
        ("widget", "홈 화면 위젯", "1×1 · 2칸 위젯으로\n층수와 시간을 바로"),
        ("palette", "5가지 디자인", "그래파이트 · 포레스트 · 실버\n에스프레소 · 다크 UHD"),
    ]
    cw, ch, gap, top = 470, 300, 30, 470
    x0 = (W - cw * 2 - gap) // 2
    for i, (kind, title, body) in enumerate(cards):
        cx = x0 + (i % 2) * (cw + gap)
        cy = top + (i // 2) * (ch + gap)
        d.rounded_rectangle([cx, cy, cx + cw, cy + ch], radius=28, fill=(255, 255, 255, 255), outline=(227, 227, 223, 255), width=2)
        badge = Image.new("RGBA", (84, 84), (0, 0, 0, 0))
        ImageDraw.Draw(badge).rounded_rectangle([0, 0, 83, 83], radius=22, fill=GRAPHITE + (255,))
        badge.alpha_composite(feature_icon(kind, 64, PEARL + (255,)), (10, 10))
        g.alpha_composite(badge, (cx + 34, cy + 34))
        d.text((cx + 34, cy + 140), title, font=font(40, 800), fill=INK)
        d.multiline_text((cx + 34, cy + 200), body, font=font(27, 500), fill=SUB, spacing=8)
    text_center(d, W / 2, H - 120, "계정 없이 · 기록은 이 휴대폰에만 저장", font(30, 600), SUB)
    rgb = g.convert("RGB")
    rgb.save(os.path.join(OUT, "infographic-features-1080x1920.png"), optimize=True)
    # 스크린샷 첫 장으로도 같은 이미지를 쓴다
    rgb.save(os.path.join(OUT, "screenshots", "01-features.png"), optimize=True)
    return g


def save_icon():
    """Play 아이콘은 32비트 PNG(RGBA). 바탕은 불투명 흰색 정사각형(모서리 마스크는 Play가 적용)"""
    icon(512).save(os.path.join(OUT, "icon-512.png"), optimize=True)


def screens():
    """실제 캡처(SHOTS/store-*.png)로 02~06을 다시 만든다. 캡처가 없으면 기존 파일을 지우지 않고 멈춘다"""
    need = ["store-home-forest.png", "store-drawer-forest.png", "store-themes.png", "store-settings-top.png"]
    missing = [n for n in need if not os.path.exists(os.path.join(SHOTS, n))]
    if missing:
        sys.exit(f"캡처 없음: {', '.join(missing)} ({SHOTS}). 실제 화면을 먼저 캡처한다")
    screenshot_card("02-home.png", "store-home-forest.png", "층수와 시간을 한눈에", "2시간부터 초록, 3시간부터 버건디로 알려줘요")
    screenshot_card("03-floor-reel.png", "store-drawer-forest.png", "릴을 돌려 층수 저장", "상태바 표시도 기록할 때 함께 정해요")
    screenshot_card("04-status-bar.png", "store-home-forest.png", "상태바에 층수가 떠요", "앱을 열지 않아도 B2를 바로 확인", crop_top=150)
    screenshot_card("05-themes.png", "store-themes.png", "5가지 디자인", "그래파이트 · 포레스트 · 에스프레소 · 실버 · UHD")
    screenshot_card("06-auto-setup.png", "store-settings-top.png", "자동 기록 준비를 한 번에", "필요한 권한과 설정을 실제 상태로 확인")


def check():
    """Play 그래픽 규격 확인"""
    ok = True

    def bad(msg):
        nonlocal ok
        ok = False
        print("  ✗", msg)

    ic = Image.open(os.path.join(OUT, "icon-512.png"))
    print(f"icon-512.png {ic.size} {ic.mode}")
    if ic.size != (512, 512) or ic.mode != "RGBA" or os.path.getsize(os.path.join(OUT, "icon-512.png")) > 1024 * 1024:
        bad("아이콘은 512×512, 32비트(RGBA) PNG, 1MB 이하")
    fg = Image.open(os.path.join(OUT, "feature-graphic-1024x500.png"))
    print(f"feature-graphic-1024x500.png {fg.size} {fg.mode}")
    if fg.size != (1024, 500) or fg.mode != "RGB":
        bad("그래픽 이미지는 1024×500, 알파 없는 24비트 PNG 또는 JPEG")
    shots = sorted(f for f in os.listdir(os.path.join(OUT, "screenshots")) if f.endswith(".png"))
    if not 2 <= len(shots) <= 8:
        bad(f"휴대전화 스크린샷은 2~8장 (현재 {len(shots)})")
    for f in shots:
        im = Image.open(os.path.join(OUT, "screenshots", f))
        w, h = im.size
        print(f"screenshots/{f} {im.size} {im.mode}")
        if min(w, h) < 320 or max(w, h) > 3840 or max(w, h) > 2 * min(w, h):
            bad(f"{f}: 한 변 320~3840px, 긴 변이 짧은 변의 2배 이하")
        if (w * 16 != h * 9) and (w * 9 != h * 16):
            bad(f"{f}: 9:16 또는 16:9 권장")
    print("규격 확인 통과" if ok else "규격 확인 실패")
    return ok


if __name__ == "__main__":
    # Windows 콘솔(cp949)에서도 한글·기호 출력이 깨지거나 멈추지 않게 한다
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    jobs = set(sys.argv[1:]) or {"check"}
    if "all" in jobs:
        jobs = {"icon", "feature", "infographic", "screens"}
    if "icon" in jobs:
        save_icon()
    if "feature" in jobs:
        feature_graphic()
    if "infographic" in jobs:
        infographic()
    if "screens" in jobs:
        screens()
    check()
