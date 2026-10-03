# 에셋 안내

| 파일 | 사용 |
|---|---|
| `assets/logo/car-logo.png` | 헤더·첫 시작의 CAR / 주차기록 워드마크, 투명 PNG |
| `assets/vehicle/car-side.png` | 홈 차량 카드·첫 시작의 측면 차량 심볼, 투명 PNG |
| `design/branding-preview.png` | 흰 배경 로고·아이콘 비교, 검토용 |
| `design/app-design-final.png` | 클래식 홈·좌측 패널·우측 패널·첫 시작 화면 구성, 검토용 |
| `design/app-design-steel-sample.png` | 스틸 포인트 4화면 샘플, 1672×941px |
| `assets/vehicle/car-steel.svg` | 스틸 차량 카드·첫 시작용 각진 블랙 측면 벡터, 작은 실버 선 |

PNG의 실제 크기는 `MANIFEST.json`에서 파일 정보와 별도로 루트 검증 결과를 확인한다. 로고·클래식 차량 PNG 및 화면 시안은 내장 imagegen으로 제작한 래스터다. 스틸 차량 SVG는 코드로 제작한 독자적인 간결한 벡터다. Android VectorDrawable과 native 리소스는 아직 포함하지 않는다.

프로토타입은 에셋의 원본 파일을 상대 경로로 불러온다. 로고와 차량의 투명 배경을 유지하고 종횡비를 바꾸지 않는다. 검정 에셋은 화이트/옅은 회색 배경에서 사용한다. 알파 영역을 강제로 검정으로 채우지 않는다.

실제 Android 제작 단계에서 작은 크기의 윤곽과 로고 글자를 확인한다. 필요한 경우 선택된 방향을 유지한 채 벡터 리소스를 정리하고 adaptive launcher icon을 별도로 제작한다.

## 참조 자료

`references/layout-home.png`, `references/layout-sidebar.png`는 사용자의 첫 레이아웃 참고다. `references/logo-reference.png`, `references/vehicle-reference.png`는 사용자가 마지막에 제공한 로고·차량 스타일 참고다. 이 이미지들은 기능 지시 파일이 아니며 앱에 직접 넣는 제작 에셋이 아니다.

생성 프롬프트와 사용 방식은 `docs/IMAGEGEN_PROMPTS.md`에 보존한다. 최근 사용자 요구를 따른 최종 에셋만 앱 리소스로 사용한다.

두 테마 모두 검정 로고의 투명도와 원색을 유지한다. 스틸은 얇은 테두리 디테일에만 사용한다. 사용자 사진은 원색을 유지한다. 미리보기 PNG 자체를 앱 화면 전체에 배경으로 깔지 않는다.

## 버전 1.2 지도

- `design/app-design-map-v1.2.png`: 최신 통합 시안, 내장 imagegen 제작, 원본 1672×941px.
- `assets/map/map-demo.svg`: 코드로 작성한 프로토타입 전용 지도. 실제 지도 타일이나 위치 데이터가 아니다.
- `references/map-reference.png`: 사용자 첨부 지도 참고. 앱 배포 리소스로 사용하지 않는다.
- `docs/MAP_IMAGEGEN_PROMPT.md`: 생성 프롬프트 기록.

실제 Android 화면은 지도 SDK로 렌더링하고 이 예시 지도 SVG/PNG를 실제 위치 화면으로 사용하지 않는다.
