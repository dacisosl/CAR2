# Android 빌드와 실행

`android/`가 Android 앱 프로젝트입니다. Kotlin · Jetpack Compose · Room · DataStore, 로컬 저장만 사용합니다.

## 선택한 버전 (2026-10-03 공식 안정 버전 확인)

| 항목 | 버전 | 비고 |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | 최소 Gradle 9.6.0, JDK 17, 최대 API 37 |
| Gradle Wrapper | 9.8.0 | `android/gradle/wrapper` |
| Kotlin (Compose 컴파일러 플러그인) | 2.4.20 | AGP 9 내장 Kotlin 사용, `kotlin-android` 플러그인 없음 |
| KSP | 2.3.12 | Room 컴파일러 |
| Compose BOM | 2026.09.00 | |
| core-ktx / activity-compose / lifecycle | 1.19.1 / 1.13.0 / 2.11.0 | |
| Room / DataStore | 2.8.5 / 1.2.1 | |
| play-services-location | 21.4.0 | |
| 네이버 지도 SDK | 3.24.0 | `repository.map.naver.com` |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 | |

버전은 `android/gradle/libs.versions.toml` 한 곳에서 관리합니다.

## 빌드

JDK 17 이상(Android Studio 내장 JBR 가능)과 Android SDK가 필요합니다.

```bash
cd android
./gradlew assembleGithubDebug      # GitHub 테스트 APK: app/build/outputs/apk/github/debug/
./gradlew bundlePlayRelease        # Play 업로드용 AAB: app/build/outputs/bundle/playRelease/
./gradlew testGithubDebugUnitTest  # 단위 테스트
```

`android/local.properties`에 `sdk.dir`이 없으면 Android Studio로 한 번 열거나 직접 적어 주세요. 이 파일은 커밋하지 않습니다.

빌드 종류(flavor):
- `github`: GitHub 릴리스 APK. 앱 안 업데이트 확인 포함, arm64·x86_64만 포함(약 67MB).
- `play`: Google Play용. 앱 안 업데이트와 `REQUEST_INSTALL_PACKAGES`가 없고 32비트 기기까지 포함. AAB 약 32MB.

release 서명은 `android/keystore.properties`가 있을 때만 적용된다. 만드는 법은 `store/PLAY_CONSOLE_CHECKLIST.md`.

## 지도 키 (선택)

키가 없어도 빌드되며 홈 지도는 ‘지도 미연결’ 상태로 표시됩니다. 실제 지도를 쓰려면 `android/local.properties`(또는 같은 이름의 환경 변수)에 넣습니다.

```properties
NAVER_MAP_KEY_ID=발급받은_Client_ID
NAVER_MAP_STYLE_ID=스타일_에디터에서_발행한_ID   # 비우면 기본 지도
```

스타일 로드에 실패하면 기본 지도로 복구하고 ‘기본 지도’ 표시를 띄웁니다. 인증 실패 시 ‘다시 시도’를 제공합니다.

## 구조

| 패키지 | 내용 |
|---|---|
| `ui/home`, `ui/drawer`, `ui/settings`, `ui/onboarding`, `ui/map`, `ui/theme` | Compose 화면, 층수 릴·사이드바, 테마 토큰 |
| `domain/floor` | 층 표기(0F 없음), 기압 상대 높이 기반 추천 |
| `domain/parking` | 후보·확정 기록 분리, 중복 해제 병합, 저장 |
| `data/bluetooth` | 등록 차량 ACL 연결/해제 매니페스트 수신기, 페어링 목록 |
| `data/sensor`, `data/location`, `data/storage` | 기압 샘플, 위치 상태, Room·DataStore·사진 |
| `platform/autolaunch` | 백그라운드 Activity 자동 표시(다른 앱 위에 표시 예외) |
| `platform/statusbar` | 상태바 층 표지판(CARwhere v5.9 StatusBarNotifier 이식) |
| `platform/permissions` | 자동 기록 준비 상태 계산 |

## 동작 요약

1. 등록 차량 ACL 해제 → 기압 스냅샷 → 2초 재연결 확인(재연결 시 후보 취소) → 후보 하나 생성 → `SYSTEM_ALERT_WINDOW` 허용 시 알림 없이 Activity 자동 표시, 저장된 쪽 패널 열기. 허용되지 않은 경우에만 보조 알림.
2. 자동 진입일 때만 잠금 화면 위 표시·화면 켜기. 사진 기능은 `requestDismissKeyguard`로 정상 잠금 해제 후 실행.
3. 위치는 앱이 화면에 보일 때만 사용(백그라운드 위치 권한 없음). 해제 후 3분 안에 앱이 표시되면 그 시점 위치를 ‘하차 직후 위치’로 저장.
4. 층수 추천은 같은 방문(30분·300m 이내)에 사용자가 확정한 층과 기압이 있을 때만. 그 외에는 ‘층수를 선택해주세요’.
5. 설정 ‘상태바 층 표시’를 켜면 확정 층을 상태바 아이콘·상시 알림(Android 16 Live Updates 승격 요청)으로 표시, 끄면 즉시 제거. 재부팅·업데이트 후 복원.
