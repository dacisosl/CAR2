# Google Play 등록 자료

먼저 [`PLAY_CONSOLE_CHECKLIST.md`](PLAY_CONSOLE_CHECKLIST.md)를 연다. 계정 → 등록정보 → 앱 콘텐츠 → AAB·비공개 테스트 → 프로덕션 순서이며, 항목마다 준비 상태(✅ / ⚠️ / ❌)와 올릴 파일 경로가 있다.

| 파일 | 내용 |
|---|---|
| `PLAY_CONSOLE_CHECKLIST.md` | 단계별 제출 체크리스트, 업로드 키 만들기, 권한별 신고 여부·사유 문구(한/영), 정책·권리 위험 |
| `LISTING.md` | 앱 이름·간단한 설명·자세한 설명·카테고리·연락처, 그래픽 규격과 재캡처 방법 |
| `APP_CONTENT.md` | 개인정보처리방침 URL, 광고, 앱 액세스(한/영), 콘텐츠 등급 설문, 타겟층, 뉴스·정부·금융·건강·광고 ID, 인앱 결제 진술 |
| `DATA_SAFETY.md` | 데이터 보안 양식 단계별 응답과 근거 |
| `CLOSED_TESTING.md` | 테스터 12명(권장 20명) 14일 비공개 테스트 운영, 테스터 안내문, 프로덕션 액세스 신청 답변 초안 |
| `icon-512.png` | 앱 아이콘 512×512, 32비트 PNG |
| `feature-graphic-1024x500.png` | 그래픽 이미지 1024×500, 24비트 PNG(알파 없음) |
| `infographic-features-1080x1920.png` | 핵심 기능 소개 카드(`screenshots/01-features.png`와 같음) |
| `screenshots/` | 휴대전화 스크린샷 6장, 1080×1920(9:16). 02~06은 0.3.12 play 빌드 실제 캡처 |
| `../docs/PRIVACY_POLICY.md` | 개인정보처리방침(한국어 + 영문 요약) |
| `../docs/APP_AUDIT.md` | 앱 점검 결과(Play 제출 점검 포함) |
| `../tools/store_assets.py` | 이미지 생성·규격 확인. `python tools/store_assets.py`(확인만) |
| `../tools/check_play_bundle.py` | 업로드 전 AAB 점검(서명·버전·권한). 빌드하지 않음 |

이미지는 실제 에뮬레이터 캡처와 앱 아이콘으로 만들었다(가짜 화면 없음). 글꼴은 Noto Sans KR(OFL).
