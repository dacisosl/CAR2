# Google Play 제출 체크리스트 (주차기록 `app.car.parking`)

점검일 2026-10-04 · 현재 버전 0.3.11(versionCode 14) · 첫 제출 예정 0.3.12(versionCode 15)

표시: ✅ 준비됨 · ⚠️ 사용자 작업 필요 · ❌ 누락

계정 등록, 결제, 신원 확인, 업로드 키·비밀번호, 법적 동의, 테스터 모집은 개발자 본인만 할 수 있다. 비밀번호·키·전화번호는 이 저장소(공개)에 적지 않는다.

## 한눈에 보기 (첨부 이미지 항목 대조)

| 이미지 항목 | 상태 | 근거 / 할 일 |
|---|---|---|
| A1 인앱 결제 정책(계좌 이체·외부 결제 금지) | ✅ | 결제·후원 기능 없음. [`APP_CONTENT.md`](APP_CONTENT.md) 7절 |
| A2 개인 계정 비공개 테스트 | ⚠️ | 현재 요건은 **12명·14일 연속**(이미지의 20명은 이전 기준). 20명 이상 모집 권장. [`CLOSED_TESTING.md`](CLOSED_TESTING.md) |
| A3 저작권·상표 | ⚠️ | 아이콘·그래픽·설명에 타사 로고 없음 ✅. 0.3.12 주차 영상의 권리 확인 필요. 부록 B |
| B1 Google 계정 2단계 인증 | ⚠️ | 본인 설정 |
| B2 해외 결제 카드(US$25) | ⚠️ | 본인 결제 |
| B3 신분증 | ⚠️ | 본인 인증 |
| B4 개인정보처리방침 URL | ✅ / ⚠️ | 주소 공개 접근 확인(200). 이번 수정본은 커밋·푸시해야 반영 |
| B5 앱 빌드 파일(.aab) | ⚠️ | AAB는 있으나 0.3.11·**서명 없음** → 업로드 키 만든 뒤 0.3.12로 다시 빌드 |
| C1 개발자 계정·$25·신원 확인 | ⚠️ | 1단계 |
| C2 앱 만들기·설명·아이콘·그래픽·스크린샷 | ✅ | 문구·아이콘·그래픽 이미지·스크린샷 6장 준비(0.3.12 실제 캡처). 앱 만들기 입력은 본인. 2단계 |
| C3 콘텐츠 등급·방침·광고·타겟 연령 | ✅ | 응답 준비 완료, 입력은 본인. 3단계 |
| C4 AAB 업로드·비공개 테스트 | ⚠️ | 4단계 |
| C5 프로덕션 신청·검토 | ⚠️ | 5단계 |

---

## 1단계. 개발자 계정 (C1, B1~B3)

| | 항목 | 상태 | 방법 |
|---|---|---|---|
| 1-1 | Google 계정 2단계 인증 | ⚠️ | myaccount.google.com → 보안 → 2단계 인증 |
| 1-2 | Play Console 가입: **개인** 계정 선택 | ⚠️ | play.google.com/console. 조직 계정은 D-U-N-S 번호가 필요하고 테스트 요건이 없다 |
| 1-3 | 등록비 US$25 결제 | ⚠️ | 해외 결제 가능한 VISA/Mastercard. 1회 |
| 1-4 | 신원 확인 | ⚠️ | 주민등록증·운전면허증·여권. 보통 1~3일. 연락처 이메일·전화 인증, Android 기기 인증(Play Console 앱)을 요구할 수 있다. 전화번호는 Console에만 입력하고 스토어에 공개하지 않는다 |
| 1-5 | 개발자 이름 정하기 | ⚠️ | 스토어에 공개된다. 개인정보처리방침은 ‘Play 개발자 페이지에 표시된 개발자’로 적어 두었다 |

## 2단계. 앱 만들기와 스토어 등록정보 (C2)

| | 항목 | 상태 | 파일 / 값 |
|---|---|---|---|
| 2-1 | 앱 만들기: 이름, 기본 언어 **한국어(ko-KR)**, 앱, **무료**, 개발자 프로그램 정책·미국 수출법 동의 | ⚠️ | 이름은 [`LISTING.md`](LISTING.md). 무료 앱은 나중에 유료로 바꿀 수 없다 |
| 2-2 | 앱 이름 / 간단한 설명 / 자세한 설명 | ✅ | [`LISTING.md`](LISTING.md) (15자 / 45자 / 1,221자) |
| 2-3 | 앱 아이콘 512×512 | ✅ | `store/icon-512.png` (32비트 PNG, 15KB) |
| 2-4 | 그래픽 이미지 1024×500 | ✅ | `store/feature-graphic-1024x500.png` (24비트, 알파 없음). 0.3.12 홈 실제 캡처 |
| 2-5 | 휴대전화 스크린샷 2~8장 | ✅ | `store/screenshots/01`~`06` 6장, 1080×1920. 02~06은 0.3.12 play 빌드 실제 캡처. 화면이 바뀌면 [`LISTING.md`](LISTING.md) ‘다시 캡처하는 방법’ |
| 2-6 | 카테고리·태그·연락처 | ✅ | 자동차 및 차량, 이메일 dydy7270@naver.com, 전화번호 비움 |

## 3단계. 앱 콘텐츠 (C3)

응답은 [`APP_CONTENT.md`](APP_CONTENT.md), [`DATA_SAFETY.md`](DATA_SAFETY.md). Console 입력은 본인이 한다.

| | 항목 | 상태 | 응답 요약 |
|---|---|---|---|
| 3-1 | 개인정보처리방침 | ✅ / ⚠️ | `https://github.com/dacisosl/CAR2/blob/main/docs/PRIVACY_POLICY.md` (공개 접근 200 확인). **수정본 푸시 필요** |
| 3-2 | 광고 | ✅ | 없음 |
| 3-3 | 앱 액세스 | ✅ | 로그인 불필요 + 자동 기록 재현 방법(한/영) |
| 3-4 | 콘텐츠 등급(IARC) | ✅ | 기타 앱 유형, 모든 항목 ‘아니요’ → 전체이용가 예상 |
| 3-5 | 타겟층 | ✅ | 18세 이상만, 어린이 관심 없음 |
| 3-6 | 데이터 보안 | ✅ / ⚠️ | 위치(대략·정확)·진단 ‘수집, 공유 안 함’(네이버 지도 SDK). 네이버클라우드에 SDK 수집 항목 문의 권장 |
| 3-7 | 광고 ID | ✅ | 사용 안 함 |
| 3-8 | 뉴스 / 정부 / 금융 / 건강 | ✅ | 모두 해당 없음 |
| 3-9 | 권한 신고 양식 | ✅ | 신고 양식이 필요한 권한 없음(부록 A) |

## 4단계. AAB 업로드와 비공개 테스트 (C4, B5)

| | 항목 | 상태 | 방법 |
|---|---|---|---|
| 4-1 | 업로드 키 만들기 | ⚠️ | 아래 ‘업로드 키’. `android/keystore.properties` **없음**(2026-10-04 확인) |
| 4-2 | 0.3.12 play AAB 빌드 | ⚠️ | 다른 작업(0.3.12 앱 변경)이 끝난 뒤 `cd android` → `./gradlew.bat bundlePlayRelease` |
| 4-3 | AAB 점검 | ⚠️ | `python tools/check_play_bundle.py` → 서명 있음, versionCode 15, `REQUEST_INSTALL_PACKAGES` 없음 확인 |
| 4-4 | Play 앱 서명 사용 | ⚠️ | 첫 업로드 때 ‘Google에서 생성한 앱 서명 키’ 사용(기본값). 업로드 키와 배포 키가 분리된다 |
| 4-5 | 비공개 테스트 트랙·테스터 등록·업로드 | ⚠️ | [`CLOSED_TESTING.md`](CLOSED_TESTING.md) 2절 |
| 4-6 | 테스터 12명 이상 14일 연속 참여 | ⚠️ | 20명 이상 모집, 중간 업데이트 1회 이상 |

업로드할 파일: `android/app/build/outputs/bundle/playRelease/app-play-release.aab`

현재 그 위치의 AAB(32.8MB)는 **0.3.12 / versionCode 15**로 다시 빌드했지만 업로드 키가 없어 **서명 없음** 상태라 아직 업로드할 수 없다. `check_play_bundle.py` 결과 자체 업데이트 코드는 없고, 남은 항목은 키 설정·서명 2개다. 병합 매니페스트 기준 권한은 정상(`REQUEST_INSTALL_PACKAGES`·백그라운드 위치·전체 화면 인텐트·정확한 알람·`AD_ID` 없음), targetSdk 36(2026-08-31 요건 충족).

### 업로드 키 (한 번만, 잃어버리지 말 것)

키 파일은 **저장소 밖**에 둔다. 0.3.12부터 저장소 루트 `.gitignore`도 `*.jks`·`*.keystore`·`keystore.properties`를 무시하지만, 공개 저장소이므로 키는 처음부터 저장소 밖에 두는 것이 안전하다.

PowerShell에서 직접 실행한다(비밀번호는 입력 창에서 직접 정한다).

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\keys"
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore "$env:USERPROFILE\keys\car-upload-key.jks" -alias upload `
  -keyalg RSA -keysize 2048 -validity 10000
```

`android/keystore.properties`를 만든다(`android/.gitignore`에 등록되어 커밋되지 않음). 경로는 슬래시(`/`)로 쓴다.

```properties
storeFile=C:/Users/<Windows 사용자 이름>/keys/car-upload-key.jks
storePassword=<직접 정한 비밀번호>
keyAlias=upload
keyPassword=<직접 정한 비밀번호>
```

- `.jks` 파일과 비밀번호를 비밀번호 관리자·외장 저장소에 따로 백업한다. 업로드 키를 잃으면 Console에서 업로드 키 재설정을 요청해야 한다.
- 커밋 전에 `git status`에 `.jks`·`keystore.properties`가 보이지 않는지 확인한다.
- GitHub 테스트 APK(디버그 키)와 Play 버전은 서명이 달라 덮어 설치되지 않는다. 테스트 폰에서는 GitHub 버전을 지운 뒤 Play 버전을 설치한다(기록도 지워짐).

## 5단계. 프로덕션 신청과 출시 (C5)

| | 항목 | 상태 | 방법 |
|---|---|---|---|
| 5-1 | 프로덕션 액세스 신청 | ⚠️ | 14일 뒤 대시보드에서 신청. 답변 초안 [`CLOSED_TESTING.md`](CLOSED_TESTING.md) 5절(실제 결과로 채움) |
| 5-2 | 프로덕션 릴리스 | ⚠️ | 테스트한 AAB 승격, 국가 대한민국, 출시 노트 |
| 5-3 | 검토 대기 | ⚠️ | 보통 3~7일, 처음에는 더 걸릴 수 있음 |

## 출시 전 실기기 확인 (VERIFICATION.md 미검증 항목)

1. 실제 차량에서 시동을 끄고 내렸을 때 앱 자동 표시(화면 켜짐·꺼짐·잠금, 앱 종료 상태)
2. 시동을 끄면 2초 안에 패널이 뜨는지, 2초 안에 다시 연결하면 기록하지 않는지
3. 0.3.12 사이드바 영상 재생·각인 애니메이션, 저사양 기기에서 끊김 여부
4. 사진 찍기 → 미리보기 → 크게 보기 → 다시 찍기 / 삭제
5. 위젯 배치와 1분 갱신, 2·3시간 색 변화
6. 제조사(삼성 등) 절전 설정에서의 동작 — ‘항상 동작’한다고 설명하지 않는다

---

## 부록 A. 권한별 Play 신고 필요 여부 (play 빌드 병합 매니페스트 기준)

| 권한 | 출처 | Play Console 신고 양식 | 비고 |
|---|---|---|---|
| `BLUETOOTH_CONNECT`, `BLUETOOTH`(API 30 이하) | 앱 | 없음 | 페어링 목록·연결 상태만. `BLUETOOTH_SCAN` 없음 |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | 앱 | 없음(포그라운드 위치) | 데이터 보안에 반영. 화면이 보일 때만 사용 |
| `ACCESS_BACKGROUND_LOCATION` | — | **해당 없음(권한 없음)** | 있으면 위치 권한 신고서 + 시연 동영상 필요 |
| `SYSTEM_ALERT_WINDOW` | 앱 | 없음 | 심사자가 용도를 물을 수 있다 → 아래 사유 문구를 설명·앱 액세스에 사용 |
| `POST_NOTIFICATIONS` | 앱 | 없음 | 런타임 권한 |
| `POST_PROMOTED_NOTIFICATIONS` | 앱 | 없음 | Android 16 실시간 업데이트 승격 요청. 시스템 기준을 충족하지 않으면 일반 알림으로 표시 |
| `RECEIVE_BOOT_COMPLETED` | 앱 | 없음 | |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | 앱 / 네이버 지도 SDK | 없음 | |
| `REQUEST_INSTALL_PACKAGES` | github 빌드만 | **play 빌드에 없음 ✅** | 있으면 신고 대상이며 이 앱 용도는 허용되지 않음 |
| 포그라운드 서비스(`FOREGROUND_SERVICE_*`) | — | 해당 없음 | 사용 안 함 |
| `USE_FULL_SCREEN_INTENT` | — | 해당 없음 | 잠금 화면 위 표시는 Activity 플래그로 처리 |
| `QUERY_ALL_PACKAGES` | — | 해당 없음 | |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | — | 해당 없음 | 위젯 갱신은 정확하지 않은 `setRepeating(ELAPSED_REALTIME)` |
| `READ_MEDIA_IMAGES` 등 사진 권한 | — | 해당 없음 | 시스템 카메라 앱(`TakePicture`)만 사용 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | — | 해당 없음 | 배터리 설정 화면으로 이동만 함 |
| `com.google.android.gms.permission.AD_ID` | — | 해당 없음 | 광고 ID ‘아니요’ |

### 사유 문구 (심사 질의·설명·앱 액세스에 사용)

**다른 앱 위에 표시(SYSTEM_ALERT_WINDOW)**

```
'다른 앱 위에 표시'는 사용자가 등록한 차량의 Bluetooth 연결이 끊겼을 때(차에서 내릴 때) 알림을 누르지 않아도 주차 층수 기록 화면을 바로 열기 위해서만 사용합니다. 이 권한으로 다른 앱 위에 창, 버튼, 광고를 그리지 않습니다. 사용자가 설정에서 직접 허용하며, 허용하지 않으면 알림으로 대신 안내합니다.
```

```
"Display over other apps" is used only to bring the floor-recording screen to the front, without tapping a notification, when the Bluetooth connection of the car the user registered is disconnected (the user is leaving the car). The app never draws windows, buttons or ads over other apps. The user grants it manually in Settings; without it the app shows a notification instead.
```

**근처 기기(BLUETOOTH_CONNECT)**

```
이미 페어링된 기기 목록에서 사용자가 자신의 차량을 고르고, 그 차량의 연결·해제만 감지하기 위해 사용합니다. 주변 기기를 검색하지 않으며 기기 정보는 휴대폰 밖으로 보내지 않습니다.
```

```
Used to let the user pick their car from already-paired devices and to detect only that car's connect/disconnect events. The app does not scan for nearby devices and never sends device information off the phone.
```

**위치(ACCESS_FINE/COARSE_LOCATION)**

```
앱 화면이 보이는 동안에만 현재 위치를 받아 주차 위치를 저장하고 지도에 표시합니다. 백그라운드 위치 권한은 요청하지 않으며 위치는 휴대폰에만 저장됩니다.
```

```
Location is used only while the app is visible, to save the parking spot and show it on the map. The app does not request background location, and locations are stored only on the device.
```

**알림(POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS)**

```
사용자가 켠 경우 저장한 주차 층수를 상태바·잠금 화면에 표시하고, 앱 화면을 자동으로 열 수 없을 때 기록 안내를 보냅니다. 광고·홍보 알림은 보내지 않습니다.
```

```
When the user turns it on, the saved parking floor is shown in the status bar and on the lock screen, and a reminder is posted when the app cannot open automatically. No promotional notifications are sent.
```

## 부록 B. 정책·권리 위험 점검

| 심각도 | 항목 | 내용 | 조치 |
|---|---|---|---|
| 해결 | play 빌드의 자체 업데이트 코드 | 0.3.11까지는 `AppUpdater`(GitHub에서 APK 내려받기, 설치 화면 열기)가 `main` 소스에 있어 play 빌드 dex에도 남아 있었다 | 0.3.12에서 `src/github/`로 옮김. play 빌드에는 업데이트 구현이 없고(`SelfUpdaters.create` = null) dex에 `api.github.com`·APK 설치 문자열이 없음을 확인 |
| 중간 | 0.3.12 사용자 제공 영상·이미지 권리 | `res/raw/parking_reverse.mp4`(600×540, 5초), `drawable-nodpi/parking_first.webp`·`parking_last.webp`는 APK와 **공개 저장소**에 그대로 들어간다. 프레임 확인 결과 제조사 엠블럼·글자 없음(일반 선화 차량) | 직접 만들었거나 상업적 이용·재배포가 허용된 것인지 확인. AI 생성이면 해당 도구 약관의 상업적 이용 허용 여부, 스톡이면 앱 내장과 원본 파일 공개 배포 허용 여부 확인. 확인 전에는 제출하지 않는다 |
| 해결 | 업로드 키 위치 | 이전 안내(`storeFile=../upload-key.jks`)는 키를 저장소 루트에 두었고 루트 `.gitignore`가 `*.jks`를 무시하지 않았다 | 안내를 저장소 밖 경로로 바꾸고, 0.3.12에서 루트 `.gitignore`에 `*.jks`·`*.keystore`·`keystore.properties`·`local.properties` 추가 |
| 해결 | 스크린샷이 실제 앱과 다름 | ‘지도 미연결’, ‘업데이트 확인’ 버튼(play 빌드에 없음), ‘4가지 디자인’ | 01 수정, 02~06·그래픽 이미지를 0.3.12 play 빌드로 다시 캡처 |
| 낮음 | 상표 | 아이콘(‘주차’ 표지판·정면 차량), 그래픽 이미지, 설명에 네이버·자동차 제조사 로고나 이름 없음. ‘Bluetooth’는 기능 설명 단어로만 사용. 설정 화면의 ‘CAR’ 로고는 일반 단어(가칭) | 재캡처 시 지도 안의 네이버 로고·저작권 표시는 SDK 조건이라 그대로 둔다. 아이콘·그래픽 이미지·설명에는 넣지 않는다 |
| 낮음 | 패키지 이름 `app.car.parking` | Play에 공개된 같은 이름의 앱 없음(스토어 페이지 404 확인). 다른 개발자가 비공개로 선점했으면 첫 업로드가 거부된다 | 거부되면 패키지 이름을 바꿔야 하며 GitHub 판과 별개 앱이 된다 |
| 정보 | 자동 표시 동작 조건 | 백그라운드 Activity 실행 예외(다른 앱 위에 표시)·제조사 절전 정책에 따라 막힐 수 있음 | 설명에 ‘강제 중지·절전 시 멈출 수 있음’ 명시함. 실기기 결과를 VERIFICATION.md에 기록 |
| 정보 | 인앱 결제 | 없음 | 설명·앱에 외부 결제·후원 링크를 넣지 않는다 |
| 정보 | 대상 API | targetSdk 36 | 2026-08-31부터의 요건(36 이상) 충족 |
