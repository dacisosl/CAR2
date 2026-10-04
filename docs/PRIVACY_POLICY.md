# 주차기록 개인정보처리방침

시행일: 2026년 10월 4일 (최초 게시 2026년 10월 3일)
앱: 주차기록 (Android 패키지 `app.car.parking`)
개발자: Google Play 스토어의 ‘주차기록’ 개발자 페이지에 표시된 개발자(이하 ‘개발자’)
문의: dydy7270@naver.com

## 요약

- 주차기록(이하 ‘앱’)은 계정·로그인이 없고, 개발자는 서버를 운영하지 않습니다.
- 주차 기록, 사진, 위치, 등록 차량 정보는 **이용자의 휴대폰 안에만** 저장하며 개발자에게 전송하지 않습니다.
- 광고, 분석, 추적 SDK를 사용하지 않습니다. 개인정보를 판매하지 않습니다. 앱 안 결제가 없습니다.
- 예외: 지도 화면을 표시할 때 지도 제공자인 네이버(네이버클라우드)에 지도 데이터를 요청합니다(3절).

## 1. 휴대폰 안에만 저장하는 정보

| 정보 | 목적 | 보관 위치 |
|---|---|---|
| 주차 층수, 감지·저장 시각 | 주차 기록 표시, 경과 시간 계산 | 앱 전용 저장소(휴대폰) |
| 주차 위치(위도·경도·정확도·측정 시각) | 지도와 홈 화면에 주차 위치 표시 | 앱 전용 저장소 |
| 집 위치(이용자가 등록한 경우) | 집 근처 주차일 때 상태바 표시 기본값 결정 | 앱 전용 저장소 |
| 등록 차량의 Bluetooth 기기 주소·이름, 연결 상태 | 등록한 차량의 연결 해제만 감지 | 앱 전용 저장소 |
| 이용자가 찍은 주차 사진 | 주차 위치 확인 | 앱 전용 폴더(휴대폰 갤러리에 저장하지 않음) |
| 기압 측정값 | 같은 주차장에서의 층수 추천 | 앱 전용 저장소 |
| 디자인 테마, 사이드바 위치 등 설정 | 앱 화면 구성 | 앱 전용 저장소 |

이 정보는 개발자나 제3자에게 전송하지 않습니다. 다른 앱은 앱 전용 저장소에 접근할 수 없으며, 클라우드 백업과 기기 간 이전에서도 제외됩니다.

## 2. 권한과 사용 시점

| 권한·기능 | 사용 시점 |
|---|---|
| 근처 기기(Bluetooth 연결) | 이미 페어링된 기기 목록에서 차량을 고를 때, 등록한 차량의 연결·해제를 확인할 때. 새 기기를 검색하지 않습니다 |
| 위치(정확한/대략적) | 앱 화면이 보이는 동안 현재 위치·주차 위치를 표시하거나 저장할 때(차에서 내린 뒤 자동으로 열린 화면 포함). **백그라운드 위치 권한은 요청하지 않으며**, 앱 화면이 보이지 않을 때는 위치를 받지 않습니다 |
| 기압 센서(권한 없음) | 등록 차량의 연결이 끊긴 직후 몇 초 동안만 측정합니다 |
| 다른 앱 위에 표시 | 차에서 내렸을 때 앱 화면을 바로 열기 위해서만 사용합니다. 다른 앱 위에 창이나 광고를 그리지 않습니다 |
| 알림 | 상태바·잠금 화면의 층수 표시와, 앱 화면을 자동으로 열 수 없을 때의 안내 |
| 카메라(휴대폰 기본 카메라 앱 사용) | 이용자가 ‘주차 사진 찍기’를 누를 때만. 앱은 카메라 권한을 요청하지 않습니다 |
| 부팅 완료 수신 | 휴대폰을 다시 켠 뒤 상태바 층수 표시 복원 |
| 인터넷·네트워크 상태 | 지도 데이터 수신 |

## 3. 외부 서비스(제3자 처리)

- **네이버 지도 SDK(네이버클라우드)**: 지도 화면을 그리기 위해 네이버 서버에서 지도 데이터를 받습니다. 이때 표시 중인 지도 영역(주차 위치나 현재 위치 주변일 수 있음)과 확대 수준, IP 주소 등 네트워크 정보, 기기·운영체제 정보, 앱 식별 정보(패키지 이름, 지도 이용 키)가 네이버에 전달될 수 있습니다. SDK에 오류가 생기면 오류·진단 정보가 네이버로 전송될 수 있습니다. 개발자는 이 정보를 받지 않습니다. 네이버 지도 이용약관과 [네이버클라우드 개인정보 처리방침](https://privacy.navercloudcorp.com/ko/ncp/PrivacyPolicy/ncp-p), [네이버 개인정보처리방침](https://policy.naver.com/policy/privacy.html)이 적용됩니다.
- **Google Play 서비스 위치 API**: 휴대폰의 현재 위치를 계산하는 데 사용합니다. [Google 개인정보처리방침](https://policies.google.com/privacy)이 적용됩니다.
- **GitHub(GitHub에서 내려받은 설치 파일만 해당)**: GitHub 배포판은 새 버전 확인을 위해 GitHub 서버에 접속하며, 이때 IP 주소 등 접속 정보가 GitHub에 전달됩니다. Google Play에서 설치한 앱은 이 확인을 하지 않습니다. [GitHub 개인정보 처리방침](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement)이 적용됩니다.

## 4. 화면에 보이는 정보

- 상태바 층수 표시를 켜면 층수(예: B2)가 상태바와 **잠금 화면 알림**에 보일 수 있습니다. 설정에서 언제든 끌 수 있습니다.
- 차에서 내렸을 때 층수 선택 화면이 잠금 화면 위에 열릴 수 있습니다. 사진 찍기는 휴대폰 잠금을 해제한 뒤에만 열립니다.
- 홈 화면 위젯을 추가하면 층수, 위치 종류, 주차 경과 시간이 홈 화면에 표시됩니다.

## 5. 보관 기간과 삭제

- 휴대폰 안의 정보는 이용자가 지우거나 앱을 삭제할 때까지 보관합니다.
- 앱 안에서 주차 사진과 집 위치를 삭제할 수 있습니다. 휴대폰 설정 → 앱 → 주차기록 → 저장공간 → ‘데이터 삭제’로 모든 기록을 지울 수 있습니다.
- 앱을 삭제하면 휴대폰에 저장된 모든 기록과 사진이 함께 삭제됩니다.
- 개발자가 보관하는 이용자 정보는 없으므로 별도의 서버 삭제 절차가 없습니다.

## 6. 이용자의 선택

- 휴대폰 설정에서 언제든 권한을 거부하거나 철회할 수 있습니다.
- 위치를 거부해도 층수와 사진 기록은 사용할 수 있습니다. 근처 기기 권한을 거부하면 차량 하차 자동 감지만 동작하지 않습니다.

## 7. 아동

앱은 운전자를 위한 앱이며 13세 미만 아동을 대상으로 하지 않습니다.

## 8. 변경과 문의

이 방침이 바뀌면 이 페이지에 시행일과 함께 게시합니다. 개인정보 관련 문의와 요청은 개인정보 보호 담당자인 개발자에게 이메일로 보내 주세요.

문의: dydy7270@naver.com

---

## Privacy Policy (English summary)

Effective October 4, 2026 · App: 주차기록 (Parking Record), package `app.car.parking` · Developer: the developer shown on the app's Google Play page · Contact: dydy7270@naver.com

- No accounts and no developer servers. Parking floor, time, location, photos, home location, the registered car's Bluetooth address/name and barometer readings are stored **only on the user's device**, excluded from cloud backup and device transfer, and deleted when the app is uninstalled.
- Permissions: Nearby devices (detect the registered car's Bluetooth connect/disconnect; no scanning), Location (only while the app is visible; no background location), Display over other apps (only to bring the app to the front when the user leaves the car; no overlay windows), Notifications (floor in the status bar and on the lock screen), Camera through the system camera app (no CAMERA permission), Boot completed, Internet.
- Third parties: the NAVER Map SDK (NAVER Cloud) receives the displayed map area, zoom level, IP/device/OS information, app identifiers and SDK error diagnostics in order to draw the map. Google Play services computes the device location. Only the GitHub-distributed build (not the Google Play build) checks GitHub for updates.
- No ads, analytics or tracking SDKs, no in-app purchases, no sale of data. Not directed to children under 13.
