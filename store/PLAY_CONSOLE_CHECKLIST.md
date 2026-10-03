# Google Play 등록 순서와 체크리스트

순서대로 진행한다. ☐는 사용자가 직접 해야 하는 일(계정·서명 키·결제·법적 동의는 대신할 수 없음).

## 0. 빌드 두 가지

| 빌드 | 명령 | 용도 |
|---|---|---|
| play | `./gradlew bundlePlayRelease` → `app/build/outputs/bundle/playRelease/app-play-release.aab` | Play 업로드. 앱 안 업데이트·설치 권한 없음, 32비트 기기 포함 |
| github | `./gradlew assembleGithubDebug` | GitHub 릴리스 테스트 APK. 앱 안 업데이트 포함 |

Play 정책상 Play 밖에서 스스로 업데이트하는 앱은 등록할 수 없어 빌드를 나눴다. play 빌드에 `REQUEST_INSTALL_PACKAGES`가 없는 것을 `aapt dump badging`으로 확인했다.

## 1. 개발자 계정

- ☐ Play Console 개발자 계정 등록(등록비, 신원 확인).
- ☐ 개인 계정은 **프로덕션 공개 전에 비공개 테스트**를 해야 한다: 테스터 12명 이상이 14일 연속 참여. 조직 계정은 이 요건이 없다. 요건은 바뀔 수 있으니 Console의 안내를 따른다.

## 2. 업로드 키 만들기 (한 번만, 잃어버리지 말 것)

☐ 아래 명령은 직접 실행한다. 비밀번호는 본인만 알고, 저장소에 올리지 않는다.

```bash
keytool -genkeypair -v -keystore upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

`android/keystore.properties` 파일을 만든다(`.gitignore`에 등록되어 커밋되지 않음).

```properties
storeFile=../upload-key.jks
storePassword=입력한_비밀번호
keyAlias=upload
keyPassword=입력한_비밀번호
```

이 파일이 있으면 `bundlePlayRelease`가 자동으로 서명한다. Console에서는 **Play 앱 서명**을 사용한다(Google이 배포 키 보관, 이 키는 업로드용).

> GitHub 테스트 APK(디버그 키)와 Play 버전은 서명이 달라 서로 덮어쓰기 설치가 안 된다. 테스트 폰에서 Play 버전으로 바꿀 때는 GitHub 버전을 지운 뒤 설치한다(기록도 지워짐).

## 3. 앱 만들기

- ☐ 앱 이름: `store/LISTING.md`
- 기본 언어: 한국어 / 앱 / 무료
- ☐ 정책 동의 체크

## 4. 스토어 등록정보

`store/LISTING.md`의 문구와 `store/` 이미지를 그대로 사용한다.

## 5. 앱 콘텐츠

| 항목 | 준비 자료 |
|---|---|
| 개인정보처리방침 | `docs/PRIVACY_POLICY.md`(GitHub 주소). 연락처 이메일을 채운 뒤 커밋 |
| 데이터 보안 | `store/DATA_SAFETY.md` |
| 광고 | 없음 |
| 콘텐츠 등급 | 유틸리티, 해당 항목 모두 없음 → 전체이용가 예상 |
| 타겟층 | 18세 이상 |
| 앱 액세스 | 아래 안내문 |

### 앱 액세스(심사자 안내) 문구

```
로그인이 필요 없습니다.
자동 기록은 사용자가 등록한 차량 Bluetooth가 끊길 때 동작하므로 심사 환경에서는 재현이 어렵습니다.
수동으로 확인하는 방법: 첫 화면 '나중에 설정' → 홈의 '층수 기록' 카드 → 릴에서 층 선택 → 저장.
상태바 스위치를 켜고 저장하면 상태바에 층수가 표시됩니다.
'다른 앱 위에 표시' 권한은 차량 연결이 끊겼을 때 앱 화면을 바로 열기 위해서만 사용하며, 다른 앱 위에 창을 그리지 않습니다.
```

## 6. 권한 관련 확인

| 권한 | Play 신고 양식 | 메모 |
|---|---|---|
| SYSTEM_ALERT_WINDOW | 별도 양식 없음 | 설명과 앱 액세스 문구에 용도를 적음. 심사에서 질문받을 수 있음 |
| ACCESS_FINE/COARSE_LOCATION | 양식 없음(백그라운드 위치 미사용) | 데이터 보안에 반영 |
| BLUETOOTH_CONNECT | 없음 | |
| POST_PROMOTED_NOTIFICATIONS | 없음 | Android 16 실시간 업데이트 기준 충족 여부 확인 |
| REQUEST_INSTALL_PACKAGES | play 빌드에 없음 | github 빌드 전용 |
| 포그라운드 서비스 | 사용 안 함 | |

## 7. 릴리스

- ☐ 테스트 트랙(비공개) 생성 → `app-play-release.aab` 업로드 → 테스터 이메일 목록 추가
- 버전 이름·코드는 `android/app/build.gradle.kts`의 `versionName`, `versionCode`. 업로드마다 `versionCode`를 올린다.
- targetSdk 36. Play의 최신 대상 API 요건 이상이다. 등록 시점에 Console 경고를 확인한다.
- ☐ 14일 테스트 후 프로덕션 신청

## 8. 출시 전에 실제 휴대폰으로 확인할 것

`VERIFICATION.md`의 미검증 항목. 특히:
1. 실제 차량에서 시동을 끄고 내렸을 때 앱 자동 표시(화면 켜짐·잠금 상태 포함)
2. 6초 안에 다시 연결하면 기록하지 않는지
3. 사진 찍기 → 미리보기 → 크게 보기
4. 위젯 배치와 1분 갱신, 2·3시간 색 변화
5. 제조사(삼성 등) 절전 설정에서의 동작
