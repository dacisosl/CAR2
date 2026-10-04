# 앱 콘텐츠 신고 응답 (콘텐츠 등급·타겟층·광고·기타 선언)

Play Console → 정책 및 프로그램 → **앱 콘텐츠**의 각 항목에 그대로 고른다. 데이터 보안은 [`DATA_SAFETY.md`](DATA_SAFETY.md), 권한별 신고 여부는 [`PLAY_CONSOLE_CHECKLIST.md`](PLAY_CONSOLE_CHECKLIST.md) 부록 A.

## 1. 개인정보처리방침

```
https://github.com/dacisosl/CAR2/blob/main/docs/PRIVACY_POLICY.md
```

- 2026-10-04 `curl -I`로 200 응답, 저장소 공개(public) 확인.
- ⚠️ 이번에 고친 방침(네이버 SDK·잠금 화면·영문 요약 추가)은 **커밋·푸시해야** 이 주소에 반영된다.
- 대안: GitHub Pages(저장소 Settings → Pages → Branch `main`, 폴더 `/docs` → 몇 분 뒤 `https://dacisosl.github.io/CAR2/PRIVACY_POLICY.html`. `docs/`의 다른 문서도 함께 게시된다), 또는 Notion 페이지를 ‘웹에 게시’한 공개 링크. 어느 쪽이든 로그인 없이 열리고, PDF가 아니며, 앱 이름·개발자·문의 이메일이 보여야 한다.
- 같은 주소를 **스토어 설정 → 개인정보처리방침**과 **앱 콘텐츠 → 개인정보처리방침** 두 곳에 넣는다.

## 2. 광고

| 질문 | 응답 |
|---|---|
| 앱에 광고가 포함되어 있나요? | **아니요, 앱에 광고가 없습니다** |

## 3. 앱 액세스

권장: **‘일부 또는 모든 기능이 제한됨’**을 고르고 아래 안내를 추가한다(로그인 정보 칸은 비움). 자동 기록은 실제 차량 Bluetooth가 있어야 동작하므로 심사자가 재현 방법을 알 수 있게 한다. 양식이 사용자 이름·비밀번호를 반드시 요구하면 ‘모든 기능을 특별한 액세스 권한 없이 이용할 수 있음’을 고른다.

안내 이름: `로그인 불필요 · 차량 Bluetooth 자동 기록 확인 방법`

```
로그인이나 계정이 필요 없습니다. 모든 화면은 설치 직후 바로 사용할 수 있습니다.

[수동 기록]
1. 첫 화면에서 '나중에 설정'을 누릅니다.
2. 홈의 '층수 기록' 카드를 누르면 층수 패널이 열립니다.
3. 세로 릴에서 층(예: B2)을 고르고 '저장'을 누릅니다.
4. 패널의 '상태바' 스위치를 켜고 저장하면 상태바와 잠금 화면 알림에 층수가 표시됩니다.

[자동 기록 - 실제 차량 필요]
자동 기록은 사용자가 등록한 차량의 Bluetooth 연결이 끊길 때(시동을 끄고 내릴 때) 동작합니다.
이미 페어링된 아무 Bluetooth 기기(예: 이어폰)를 설정 → 차량 관리 → 등록 차량에서 '차량'으로 고른 뒤,
'근처 기기'와 '다른 앱 위에 표시'를 허용하고 그 기기의 연결을 끊으면 약 2초 뒤 층수 패널이 자동으로 열립니다.

'다른 앱 위에 표시' 권한은 차량 연결이 끊겼을 때 앱 화면을 바로 열기 위해서만 사용하며,
다른 앱 위에 창이나 광고를 그리지 않습니다. 백그라운드 위치 권한은 요청하지 않습니다.
```

<details><summary>English version</summary>

```
No login or account is required. All screens are available right after installation.

[Manual record]
1. On the first screen tap '나중에 설정' (Set up later).
2. On Home tap the '층수 기록' (Record floor) card to open the floor panel.
3. Choose a floor (e.g. B2) on the vertical reel and tap '저장' (Save).
4. Turn on the '상태바' (Status bar) switch before saving to show the floor in the status bar and lock-screen notification.

[Automatic record - needs a real car]
Automatic recording starts when the Bluetooth connection of the car the user registered is disconnected (engine off).
To reproduce, choose any already-paired Bluetooth device (e.g. earbuds) as the car in Settings > 차량 관리 > 등록 차량,
allow 'Nearby devices' and 'Display over other apps', then disconnect that device. The floor panel opens in about 2 seconds.

'Display over other apps' is used only to bring the app to the front when the car disconnects.
The app never draws windows or ads over other apps and does not request background location.
```
</details>

## 4. 콘텐츠 등급(IARC 설문)

| 질문 | 응답 |
|---|---|
| 이메일 | dydy7270@naver.com |
| 카테고리 | **기타 모든 앱 유형**(게임·소셜/커뮤니케이션이 아닌 유틸리티) |
| 폭력·유혈 | 아니요 |
| 성적 콘텐츠·노출 | 아니요 |
| 비속어·저속한 유머 | 아니요 |
| 마약·술·담배 | 아니요 |
| 도박·모의 도박 | 아니요 |
| 공포 요소 | 아니요 |
| 사용자 간 소통·콘텐츠 공유(채팅, 게시) | 아니요 |
| 사용자 생성 콘텐츠를 다른 사용자에게 공개 | 아니요(사진은 기기에만 저장) |
| 사용자의 현재 위치를 다른 사용자와 공유 | **아니요** |
| 디지털 상품 구매 | 아니요 |
| 웹 브라우저·검색 엔진 | 아니요 |
| 나치 상징 등 | 아니요 |

예상 결과: 전체이용가(IARC 3+ / 대한민국 ‘전체이용가’). 결과가 다르면 답을 다시 확인한다.

## 5. 타겟층 및 콘텐츠

| 질문 | 응답 |
|---|---|
| 타겟 연령층 | **18세 이상**만 선택(운전자 대상) |
| 앱이 어린이의 관심을 끌 수 있나요? | 아니요 |
| 가족 정책 프로그램 | 참여 안 함 |

13세 미만을 고르면 가족 정책(광고·SDK·개인정보 요건)이 추가로 적용되므로 고르지 않는다.

## 6. 기타 선언

| 항목 | 응답 |
|---|---|
| 뉴스 앱 | 아니요 |
| 정부 앱 | **아니요** — 정부·공공기관이 만들거나 그 기관을 대신해 만든 앱이 아님(개인이 개인 자격으로 개발). 공공기관 업무로 만든 앱이라면 응답이 달라진다 |
| 금융 기능 | **내 앱은 금융 기능을 제공하지 않음** |
| 건강 앱 | **내 앱에는 건강 기능이 없음** |
| 광고 ID | **아니요**(병합 매니페스트에 `AD_ID` 권한 없음, 광고·분석 SDK 없음) |
| 데이터 보안 | [`DATA_SAFETY.md`](DATA_SAFETY.md) |
| 코로나19 접촉 추적·상태 앱 | 아니요(항목이 보이면) |
| 계정 삭제 | 해당 없음(계정 생성 기능 없음) |

## 7. 인앱 결제 진술

- 앱은 **무료**이고 앱 안 결제, 구독, 유료 기능, 후원이 없다. Google Play 결제 라이브러리를 넣지 않았다.
- 앱 안과 스토어 설명에 계좌 이체 안내, 외부 결제·후원 링크(토스·카카오페이·Buy Me a Coffee 등)를 넣지 않는다. 디지털 상품을 Play 결제 밖에서 팔면 계정 정지 사유가 된다.
- 나중에 유료 기능을 넣는다면 Google Play 결제(또는 Play가 승인한 대한민국 대체 결제 프로그램)만 쓴다. 그때 이 문서와 데이터 보안을 다시 작성한다.
