# CAR 주차기록 로고·차량 아이콘 제작 기록

- 제작 방식: 내장 imagegen 도구
- 영문명: CAR (가칭)
- 시그니처: 블랙·화이트
- 차량 참고 방향: 이미지2 맨 위의 간결한 측면 실루엣
- 로고와 차량 아이콘: 투명 배경 PNG. 벡터 파일이 아닌 래스터 시안.

## 로고 프롬프트

Use case: logo-brand.
Asset type: original app wordmark / primary logo for a minimalist black-and-white parking-record app.
Input image: the supplied "CARS" logo is TYPOGRAPHY AND CONCEPT REFERENCE ONLY, not a logo to copy. Use its bold condensed uppercase letterforms and the clever car negative-space idea as inspiration for a new original mark.
Text (verbatim): "CAR" on the main line; "주차기록" as a much smaller centered Korean descriptor underneath. The main word is exactly THREE uppercase letters C A R. Do not write CARS. CAR is the user's provisional working name.
Create a polished original CAR wordmark with very bold, tightly balanced, condensed geometric sans-serif letters. Refined curves, confidently cut negative spaces, optical kerning. Its silhouette should feel premium, restrained and automotive, but the three letters must remain immediately readable. Integrate a TINY recognizable abstract car shape into the white negative-space COUNTER of the letter R: simple windshield/roof and two compact light/bumper cues, no detailed illustration. The vehicle motif should feel born from the letter, not a separate clipart icon pasted on top. Preserve a clean strong C and a clear A; do not let the motif impair letter recognition. The R motif should be legible enough as an automobile without drawing a specific make or model.
Place the small Korean descriptor "주차기록" centered beneath CAR with ample separation, simple modern sans-serif typography and modest tracking. The main word dominates, the Korean text is quiet.
Palette: solid near-black #181818 only. Every empty area and letter counter should be genuinely transparent alpha. Transparent background; no white rectangle, no checkerboard baked in, no colored fills. Flat vector-like edges, no gradients, no shadows, no texture, no metallic material or embossed mockup.
Composition: ONE finished horizontal logo lockup only, centered with close comfortable margins, no icon grid, no multiple variations, no phone mockup, no presentation headings, no slogans or extra text.
Aim for an elegant usable app logo that is distinctive yet as simple as a premium automotive wordmark.

## 차량 아이콘 프롬프트

Use case: logo-brand.
Asset type: standalone vehicle icon for a premium minimalist black-and-white Android parking-record app.
Input image: the supplied five-car collage is STYLE REFERENCE ONLY, not an edit target. Use ONLY the TOPMOST car as the reference direction: the clean side-profile roof arc, simplified body sweep and two clearly recognizable wheels. Ignore the other four cars and ignore the purple selection rectangle and red mark.
Create ONE original refined, universal side-profile CAR ICON based on that direction. It must immediately read as a car with almost no specific vehicle details. Make it an understated generic modern passenger sedan, mildly arched roof, short hood and trunk, and two round wheels. Simple flat monochrome vector-like graphic: approximately four to six decisive smooth shapes/strokes. A continuous roof contour, one clean body/hood/trunk contour, one short window/beltline curve, and two matching circular wheel rings. Use robust, consistent strokes with smooth endings and graceful curves, readable at about 64-96 px wide. Show the whole car in perfectly horizontal side view, facing RIGHT. Balanced proportions, neither an exotic supercar nor a cartoon toy. Keep the body around 3.0 times as wide as it is tall.
Color: solid near-black #181818 ONLY. Empty spaces and wheel centers must be genuine alpha transparency. Transparent background, no white rectangular background, no checkerboard drawn into the image.
Composition: a SINGLE generously sized icon, centered, closely framed with about 10% breathing room. No text, no letters, no additional icon variants or presentation board.
Avoid specific make/model cues, brand emblems, headlight detailing, grilles, doors, handles, mirrors, spokes, perspective, 3D, shading, gradients, shadows, gray antialias-free decorations, ground line, map pins, red accents and thin hairline strokes. Aim for a premium automotive logo symbol rather than an illustration.

## 흰 배경 비교 시안 프롬프트

Use case: compositing.
Asset type: clean white-background preview of two completed black transparent app assets.
Input image 1: final original "CAR" wordmark with Korean subtitle "주차기록".
Input image 2: final original black minimalist side-profile vehicle icon.
Create a single elegant landscape white artboard showing these TWO SUPPLIED ASSETS clearly on PURE WHITE #FFFFFF. This is only a presentation preview: preserve the supplied logo and car shapes, proportions, typography, negative spaces, and #181818 black color exactly. Do not redesign or reinterpret either asset.
Arrange the logo on the LEFT and the vehicle icon on the RIGHT, centered in two equal-width spacious areas. The left CAR logo is large enough to read immediately, with its original R car-shaped counter and original small "주차기록" descriptor. The right side car is bold, large, clean and clearly visible. Put a small quiet gray label "앱 로고" above the left area and "차량 아이콘" above the right area, aligned consistently.
Everything black must be clearly visible against the white artboard; all transparent counters and empty car areas become white simply because of the white backdrop. No transparent background for this preview. Plenty of clean white space, no frames, no cards, no shadows, no phones, no color swatches, no extra pictograms or slogans, no watermark. Output just this white comparison board.




## 최종 앱 디자인 4화면 PNG 프롬프트

Use case: ui-mockup.
Asset type: final design reference PNG for a Korean Android parking-record app development handoff.
Input image 1 is the existing app mockup EDIT TARGET: preserve its restrained black-and-white visual language, accurate Korean typography, home hierarchy, floor wheel, familiar street map, compact camera-only and save action row, and welcome screen message.
Input image 2 is the FINAL APPROVED BRAND WORDMARK: "CAR" with a car negative-space counter in R and small Korean descriptor "주차기록". Insert THIS brand in the home header and welcome branding instead of the previous P logo.
Input image 3 is the FINAL APPROVED VEHICLE ASSET: minimalist black side-profile car with a smooth roofline and two circular wheels. Use THIS EXACT VEHICLE SYMBOL instead of the old front-view car wherever the car appears.

Create a polished high-resolution landscape design board with FOUR complete flat front-facing Android phones in one horizontal row, clearly readable and evenly spaced. Small top labels: "홈", "왼쪽 사이드바", "오른쪽 사이드바", "처음 시작". Use all four screens at the same scale. White artboard, generous gaps, slim black Android phone frames, no iPhone notch, no perspective distortion, no cropped content.

SCREEN 1 / HOME:
Top-left: compact CAR logo lockup from input 2, readable small "주차기록" descriptor. Top-right: Bluetooth symbol on pale neutral gray circle immediately beside settings gear. No visible Bluetooth status text.
Label "주차한 지", large elapsed time "2시간 13분".
Two cards: smaller black floor card with label "주차 층수" and big white "B2" on LEFT; pale gray vehicle card with label "내 차량" and exact side-profile car from input 3 on RIGHT.
Location heading "센트럴 주차장" with secondary "C구역 · 24번".
Large ordinary flat street-map preview fills the remaining lower screen, with black P marker, B2 badge and tiny "예시 지도" label. No "내 차 위치 보기" button and no "기록 수정" text. Map can retain very pale green parks as ordinary map content, app UI itself only black/white/gray.

SCREEN 2 / LEFT SIDEBAR:
Same home visible behind a restrained darkened backdrop. A WHITE LEFT-side drawer occupying about 50% of screen width, entirely within the phone.
At top of drawer, a small horizontal grip/drag handle icon above the heading to communicate it can be dragged to the other side. Compact heading "주차 층수", small close X. Recommendation "B2로 예상돼요".
Central vertical reel/wheel: 1F, B1, selected B2, B3, B4. Black selection band with white large B2. Neighbor items are smaller and faded. No camera among floor labels.
At lower part of drawer: ONE small square camera-icon-only button, with no photo text label, and a small black "저장" button to its right, on exactly one horizontal row. Both buttons COMPLETELY INSIDE the WHITE drawer, with equal height around 44 logical px, visible gutter, enough side padding. Do not extend either onto the dimmed map. This compact row is a firm requirement.

SCREEN 3 / RIGHT SIDEBAR:
Exact same floor-record drawer as screen 2, but docked on the RIGHT side of its phone, leaving dimmed home visible on the LEFT. Same grip handle, same heading, same recommendation and wheel, same camera-only plus "저장" compact pair completely inside the right white drawer. This view documents the user's ability to drag and drop the panel from left to right. No arrows, no instructional overlays, no text other than the same product labels.

SCREEN 4 / FIRST START:
Pure white simple welcome screen. Compact CAR logo lockup from input2 near upper middle, exact side-profile black car symbol from input3 below it, with small black parking P marker beside it.
Accurate two-line headline "주차 위치를" then "쉽게 기억하세요".
Support "차에서 내리면" then "주차 기록을 도와드려요".
Quiet setup text "차량 선택 · 권한 확인 · 연결 테스트".
Bottom black primary button "내 차량 연결하기"; secondary "나중에 설정". Preserve white space and simple refined hierarchy.

Overall: matte flat black #181818, white #FFFFFF and neutral gray only; no navy, no gradients, no colorful branding, no car-detail illustration. Every requested word must be accurate. No red annotations, no full-width drawer photo action, no old front-view car, no old P app brand in headers, no home bottom CTA, no explanatory callouts. All four screens must be complete and visually consistent.

