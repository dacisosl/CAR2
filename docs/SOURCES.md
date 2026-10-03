# 공식 기술 참고 자료

정리일 2026-10-03. 제작할 때 해당 버전의 최신 공식 문서를 다시 확인한다. 아래 링크는 설계 근거이며 실기기 검증을 대신하지 않는다.

| 주제 | 공식 문서 | 적용 |
|---|---|---|
| 백그라운드 Activity | https://developer.android.com/guide/components/activities/secure-bal | SYSTEM_ALERT_WINDOW 등 화면 시작 예외 |
| 백그라운드 FGS 시작 | https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start | Android 12+, 15+ 실행 조건 |
| BluetoothDevice | https://developer.android.com/reference/android/bluetooth/BluetoothDevice | ACL 이벤트와 등록 기기 식별 |
| Bluetooth 권한 | https://developer.android.com/develop/connectivity/bluetooth/bt-permissions | Nearby devices, CONNECT/SCAN 범위 |
| Companion device | https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing | 감지 실행 구조의 후보, 차량 호환 확인 |
| 센서 | https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview | 센서 유무와 백그라운드 센서 수집 조건 |
| 상대 높이 계산 | https://developer.android.com/reference/android/hardware/SensorManager#getAltitude(float,%20float) | 기압 두 지점의 상대 높이 |
| 잠금 화면 표시 | https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean) | Activity 표시 |
| 화면 켜기 | https://developer.android.com/reference/android/app/Activity#setTurnScreenOn(boolean) | Activity 화면 깨우기 |
| 보안 잠금 인증 | https://developer.android.com/reference/android/app/KeyguardManager#requestDismissKeyguard(android.app.Activity,%20android.app.KeyguardManager.KeyguardDismissCallback) | 표시와 인증 구분 |
| Compose | https://developer.android.com/compose | 네이티브 UI 기본 기술 |
| 네이버 지도 | https://navermaps.github.io/android-map-sdk/guide-ko/2-1.html | 실제 기본 지도 연결 |
| 시스템 사진 선택 | https://developer.android.com/training/data-storage/shared/photopicker | 사진 선택 권한 최소화 |

이번 패키지에는 실제 지도 타일·실제 지도 API 키·차량 Bluetooth 식별자·위치 로그가 포함되어 있지 않다.

## 버전 1.2 지도 연결 근거

- [네이버 커스텀 스타일과 스타일 ID](https://navermaps.github.io/android-map-sdk/guide-ko/2-4.html)
- [네이버 현재 위치 오버레이](https://navermaps.github.io/android-map-sdk/guide-ko/5-5.html)
- [Android 위치 업데이트 수명](https://developer.android.com/develop/sensors-and-location/location/request-updates)
- [Android 최신 위치 선택](https://developer.android.com/develop/sensors-and-location/location/retrieve-current)
