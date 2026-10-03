# CAR · 주차기록 앱 제작 프로젝트

버전 1.2 · 정리일 2026-10-03 · 대상 Android · 영문명 CAR는 가칭입니다.

이 폴더는 지금까지 합의한 디자인, 기능 요구사항, 개발 순서, 실제 에셋과 실행 가능한 UI 프로토타입을 묶은 **개발 착수 패키지**입니다. 실제 Android 앱이나 APK는 아직 포함되어 있지 않습니다. ZIP을 해제한 폴더를 개발 프로젝트로 열면 기존 대화를 다시 설명하지 않고 작업을 시작할 수 있습니다.

## 바로 시작하기

1. 최신 `design/app-design-map-v1.2.png`를 확인합니다. 홈, 층수 패널, 테마 설정, 첫 시작 화면과 새로운 지도 스타일을 포함합니다. 기존 PNG는 이전 레이아웃 참고이며 오른쪽 패널의 배치는 클래식 시안을 함께 봅니다.
2. `prototype/index.html`을 브라우저로 엽니다. 설치와 네트워크 연결 없이 UI 동작을 확인할 수 있습니다.
3. `docs/APP_DESIGN.md`와 `docs/DEVELOPMENT_PLAN.md`를 읽습니다.
4. 개발 도구 또는 AI 에이전트에 이 폴더를 전달하고 `IMPLEMENTATION_PROMPT.md`의 내용을 첫 요청으로 사용합니다.
5. 실제 Android 구현은 자동 화면 표시의 실기기 검증부터 시작합니다.

## 포함 파일

| 경로 | 역할 |
|---|---|
| `IMPLEMENTATION_PROMPT.md` | 복사해서 바로 사용할 개발 시작 요청 |
| `AGENTS.md` | 이 프로젝트에서 지켜야 할 사용자 요구사항 |
| `docs/APP_DESIGN.md` | 앱 디자인 상세 명세 |
| `docs/THEME_DESIGN.md` | 클래식·스틸 포인트 테마 선택, 저장, 시각 규칙 |
| `docs/MAP_DESIGN.md` | 참조 지도 스타일·현재 위치·네이버 SDK/GPS 연결 명세 |
| `docs/MAP_IMAGEGEN_PROMPT.md` | 최신 지도 시안 생성 프롬프트 |
| `docs/FINISH_OPTIONS.md` | 은은한 광택·색상 3가지 비교안, 미확정 |
| `design/gloss-palette-options.png` | 그래파이트·포레스트·에스프레소 홈 비교 PNG |
| `docs/DEVELOPMENT_PLAN.md` | 단계별 제작 계획과 완료 조건 |
| `docs/REQUIREMENTS.md` | 기능·UX 요구사항과 우선순위 |
| `docs/TECHNICAL_SPEC.md` | Android 구조, 상태 전이, 데이터와 권한 |
| `docs/ACCEPTANCE_TESTS.md` | 디자인·실기기 검증 체크리스트 |
| `docs/DECISIONS.md` | 최신 결정과 이전 시안에서 삭제한 내용 |
| `docs/ASSET_GUIDE.md` | 에셋 사용법과 출처 |
| `docs/SOURCES.md` | 공식 Android·지도 문서 링크 |
| `docs/IMAGEGEN_PROMPTS.md` | 내장 imagegen으로 제작한 시안·에셋의 프롬프트 |
| `docs/STEEL_POINT_PROMPT.md` | 최신 블랙·화이트 + 스틸 포인트 샘플 생성 프롬프트 |
| `design/tokens.json` | 색상, 간격, 폰트 크기, 버튼 크기 기준 |
| `design/app-design-map-v1.2.png` | 최신 지도 반영 통합 4화면 PNG |
| `design/app-design-final.png` | 이전 클래식 레이아웃 참고 PNG |
| `design/app-design-steel-sample.png` | 블랙·화이트 + 스틸 포인트 4화면 샘플 PNG |
| `design/branding-preview.png` | 로고·차량 아이콘 흰 배경 비교 PNG |
| `assets/logo/car-logo.png` | 투명 배경 CAR · 주차기록 로고 |
| `assets/vehicle/car-side.png` | 클래식 투명 배경 측면 차량 아이콘 |
| `assets/vehicle/car-steel.svg` | 스틸 포인트 각진 블랙 측면 차량 벡터 |
| `assets/map/map-demo.svg` | 프로토타입 전용 예시 지도 벡터 |
| `references/` | 사용자 제공 레이아웃·스타일·지도 참고 이미지 |
| `prototype/` | HTML/CSS/JavaScript UI 프로토타입 |
| `MANIFEST.json` | 패키지 파일 목록과 SHA-256 해시 |
| `VERIFICATION.md` | 이번 패키지의 실제 검증 결과 |

## 프로토타입에서 확인할 것

- 홈의 B2 카드를 눌러 층수 선택 패널 열기
- 릴을 위아래로 스크롤하거나 층수를 눌러 선택하기
- 패널 상단 손잡이를 길게 누른 뒤 반대쪽 가장자리로 드래그해서 위치 변경하기
- 설정의 왼쪽/오른쪽 선택으로 동일한 위치 변경하기
- 카메라 아이콘으로 사진 선택, 작은 저장 버튼으로 확정하기
- 외부 데모 도구의 ‘주차 이벤트 테스트’로 자동 패널 표시 흐름 확인하기
- 설정 → 디자인 테마에서 클래식·스틸 포인트 미리보기 카드를 선택해 즉시 적용하기
- 예시 지도를 드래그/방향키로 이동하고 위치 버튼으로 현재 위치 예시를 중앙에 맞추기
- 설정에서 자동 기록 상태 전환, 첫 시작 안내 확인하기

프로토타입은 실제 Bluetooth, GPS, 기압 센서, Android 권한, 잠금 화면을 사용하지 않습니다. 데모 지도는 실제 지리정보가 아닙니다. 주차 이벤트 버튼과 설정 완료 표시는 UI 시뮬레이션입니다. 선택한 사진은 현재 브라우저 세션에서만 유지하고, 테마·층수·패널 위치·데모 설정은 가능한 환경에서 로컬 저장합니다.

## 변하지 않는 핵심

기본 클래식(화이트·차콜 블랙)과 선택형 스틸 포인트(얇은 실버 디테일)의 간결한 디자인. 설정에서 테마를 고르고 선택을 유지. 설정 옆 Bluetooth 아이콘. 홈 하단의 ‘내 차 위치 보기’와 ‘기록 수정’ 제거. 사이드바의 **카메라 아이콘만 있는 버튼 + 작은 저장 버튼을 2열·1행으로 배치**. 층수 릴 스크롤. 사이드바 좌우 드래그 이동과 위치 저장. 사전 설정을 마친 지원 Android 기기에서 **차량 연결 해제 후 앱 화면이 자동으로 표시**되어야 합니다.

새 기획으로 되돌아가지 말고 이 요구사항부터 구현합니다. PNG와 문서가 다를 때는 최신 사용자 결정이 정리된 `docs/REQUIREMENTS.md`와 `docs/APP_DESIGN.md`를 기준으로 합니다.

지도 스타일은 두 테마에 공통 적용합니다. 실제 지도/GPS는 Android 개발 단계에서 연결하며 도보 경로·예상 시간·실내 길찾기는 현재 MVP에 포함하지 않습니다.

## Android 앱 (버전 0.1.0)

`android/`에 Kotlin · Jetpack Compose 네이티브 앱을 추가했습니다. 빌드 방법·선택 버전·지도 키 설정은 [`docs/BUILD.md`](docs/BUILD.md), 실제 검증 결과와 아직 실기기에서 확인하지 않은 항목은 [`VERIFICATION.md`](VERIFICATION.md)에 있습니다.

```bash
cd android
./gradlew assembleGithubDebug
```

Google Play 등록 자료(설명 문구·이미지·데이터 보안·체크리스트)는 [`store/`](store/README.md), 점검 결과는 [`docs/APP_AUDIT.md`](docs/APP_AUDIT.md).

설정에 **상태바 층 표시** 켜기/끄기를 추가했습니다(CARwhere v5.9 상태바 표지판 코드 이식). 확정한 층수를 상태바 아이콘·상시 알림으로 보여주고, Android 16에서는 Live Updates 승격을 요청합니다.
