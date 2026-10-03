"""네이버 지도 스타일 에디터 ‘내 라이브러리’용 아이콘(SVG, 80×80px 이하).

앱의 차분한 흑백 디자인에 맞춘 단색 장소 아이콘. 지도의 주차장 아이콘은 회색 테두리 P로 두어
사용자가 저장한 검정 P 핀(앱이 직접 그림)과 겹쳐 보이지 않게 한다.

사용: python tools/map_icons.py  → map-style/icons/*.svg, map-style/icons/preview.html
"""
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "map-style", "icons")
os.makedirs(OUT, exist_ok=True)

SLATE = "#5F666D"   # 일반 장소 바탕
GREEN = "#6E8F5E"   # 공원·자연(지도 공원색과 어울리는 저채도)
RED = "#A0525C"     # 병원·약국(버건디 계열, 채도 낮춤)
WHITE = "#FFFFFF"

# 각 글리프는 80×80 좌표, 가운데 약 40×40 안에 그린다
GLYPHS = {
    "cafe": f'''<path d="M26 32h24v10a12 12 0 0 1-12 12h0a12 12 0 0 1-12-12z" fill="{WHITE}"/>
<path d="M50 35h3a5 5 0 0 1 0 10h-3" fill="none" stroke="{WHITE}" stroke-width="3.5"/>
<rect x="24" y="56" width="30" height="3.5" rx="1.75" fill="{WHITE}"/>''',
    "restaurant": f'''<path d="M30 24v13a4 4 0 0 0 3 3.9V57h4V40.9a4 4 0 0 0 3-3.9V24h-3v11h-1.5V24h-3v11H31V24z" fill="{WHITE}"/>
<path d="M46 57V24c5 2 8 8 8 16v4h-4v13z" fill="{WHITE}"/>''',
    "convenience": f'''<path d="M22 26h36l2 10a6 6 0 0 1-12 0 6 6 0 0 1-8 0 6 6 0 0 1-8 0 6 6 0 0 1-12 0z" fill="{WHITE}"/>
<path d="M26 44v13h28V44" fill="none" stroke="{WHITE}" stroke-width="3.5" stroke-linejoin="round"/>
<rect x="36" y="48" width="8" height="9" fill="{WHITE}"/>''',
    "hospital": f'''<rect x="35" y="23" width="10" height="34" rx="2" fill="{WHITE}"/>
<rect x="23" y="35" width="34" height="10" rx="2" fill="{WHITE}"/>''',
    "pharmacy": f'''<rect x="22" y="33" width="36" height="14" rx="7" transform="rotate(-40 40 40)" fill="none" stroke="{WHITE}" stroke-width="3.5"/>
<path d="M40 40l-9.2 7.7a7 7 0 0 1 -9-10.7l9.2-7.7z" transform="rotate(0 40 40)" fill="{WHITE}"/>''',
    "toilet": f'''<circle cx="31" cy="27" r="4" fill="{WHITE}"/><circle cx="49" cy="27" r="4" fill="{WHITE}"/>
<path d="M26 34h10v12h-2v11h-6V46h-2z" fill="{WHITE}"/>
<path d="M45 34h8l4 14h-4v9h-8v-9h-4z" fill="{WHITE}"/>''',
    "park": f'''<path d="M40 20l13 19h-6l9 12H24l9-12h-6z" fill="{WHITE}"/>
<rect x="37.5" y="50" width="5" height="9" fill="{WHITE}"/>''',
    "public": f'''<path d="M22 33l18-10 18 10z" fill="{WHITE}"/>
<rect x="25" y="35" width="4" height="15" fill="{WHITE}"/><rect x="33" y="35" width="4" height="15" fill="{WHITE}"/>
<rect x="43" y="35" width="4" height="15" fill="{WHITE}"/><rect x="51" y="35" width="4" height="15" fill="{WHITE}"/>
<rect x="22" y="52" width="36" height="5" fill="{WHITE}"/>''',
    "shopping": f'''<path d="M33 33v-4a7 7 0 0 1 14 0v4" fill="none" stroke="{WHITE}" stroke-width="3.5"/>
<path d="M25 33h30l-2 24H27z" fill="{WHITE}"/>''',
    "gas": f'''<rect x="25" y="25" width="20" height="32" rx="2.5" fill="{WHITE}"/>
<rect x="29" y="29" width="12" height="9" rx="1" fill="{SLATE}"/>
<path d="M45 36h4a3 3 0 0 1 3 3v12a2.5 2.5 0 0 0 5 0V31l-5-5" fill="none" stroke="{WHITE}" stroke-width="3.5" stroke-linejoin="round"/>''',
    "ev": f'''<rect x="25" y="25" width="20" height="32" rx="2.5" fill="{WHITE}"/>
<path d="M37 29l-6 11h5l-2 9 7-12h-5z" fill="{SLATE}"/>
<path d="M45 38h4a3 3 0 0 1 3 3v9a2.5 2.5 0 0 0 5 0V36" fill="none" stroke="{WHITE}" stroke-width="3.5" stroke-linejoin="round"/>
<rect x="53" y="28" width="6" height="8" rx="1.5" fill="{WHITE}"/>''',
    "hotel": f'''<rect x="23" y="44" width="34" height="8" rx="2" fill="{WHITE}"/>
<rect x="23" y="30" width="4" height="27" fill="{WHITE}"/><rect x="53" y="44" width="4" height="13" fill="{WHITE}"/>
<circle cx="34" cy="39" r="4" fill="{WHITE}"/><path d="M40 35h11a6 6 0 0 1 6 6v3H40z" fill="{WHITE}"/>''',
}

NAMES = {
    "cafe": ("카페", SLATE), "restaurant": ("음식점", SLATE), "convenience": ("편의점", SLATE),
    "hospital": ("병원", RED), "pharmacy": ("약국", RED), "toilet": ("화장실", SLATE),
    "park": ("공원", GREEN), "public": ("관공서", SLATE), "shopping": ("쇼핑", SLATE),
    "gas": ("주유소", SLATE), "ev": ("전기차 충전소", SLATE), "hotel": ("숙박", SLATE),
}


def badge(color, glyph):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">'
            f'<circle cx="40" cy="40" r="36" fill="{color}" stroke="{WHITE}" stroke-width="4"/>{glyph}</svg>\n')


# 지도 위 주차장: 흰 바탕 + 회색 테두리 P (사용자 저장 핀은 앱이 검정 P로 따로 그린다)
PARKING = (f'<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">'
           f'<rect x="8" y="8" width="64" height="64" rx="14" fill="{WHITE}" stroke="{SLATE}" stroke-width="5"/>'
           f'<path d="M30 22h12.5a12.5 12.5 0 0 1 0 25H37.5v11H30z M37.5 29v11h5a5.5 5.5 0 0 0 0-11z" fill="{SLATE}" fill-rule="evenodd"/></svg>\n')

files = {"parking": ("주차장", PARKING)}
for key, glyph in GLYPHS.items():
    label, color = NAMES[key]
    files[key] = (label, badge(color, glyph))

cards = []
for key, (label, svg) in files.items():
    name = f"car-{key}.svg"
    with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
        f.write(svg)
    cards.append(f'<figure><div class="big">{svg}</div><div class="small">{svg}</div><figcaption>{label}<br><code>{name}</code></figcaption></figure>')

html = """<!doctype html><meta charset="utf-8"><title>지도 아이콘 미리보기</title>
<style>body{font-family:'Noto Sans KR',sans-serif;background:#F7F8F1;margin:24px}
.grid{display:grid;grid-template-columns:repeat(5,170px);gap:16px}
figure{margin:0;background:#fff;border:1px solid #E4E4E4;border-radius:14px;padding:12px;text-align:center}
.big svg{width:80px;height:80px}.small svg{width:24px;height:24px}
figcaption{font-size:13px;color:#333}code{font-size:11px;color:#777}</style>
<h3>내 라이브러리 업로드용 아이콘 (SVG, 80×80px) · 아래 작은 것은 지도 표시 크기(약 24px)</h3>
<div class="grid">""" + "".join(cards) + "</div>"
with open(os.path.join(OUT, "preview.html"), "w", encoding="utf-8", newline="\n") as f:
    f.write(html)
print(len(files), "icons")
