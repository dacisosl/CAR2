"""Play 업로드 전 AAB 점검(빌드하지 않고 결과물만 읽는다).

사용: python tools/check_play_bundle.py
확인 항목
  1. android/keystore.properties 존재 여부(내용·비밀번호는 출력하지 않음)
  2. playRelease AAB 존재, 크기, 만든 시각
  3. AAB 서명 여부(META-INF/*.RSA|*.EC|*.DSA). 서명 없는 AAB는 Play에 올릴 수 없다
  4. 병합된 playRelease 매니페스트의 versionCode·versionName·targetSdk, 권한 목록
  5. Play 정책상 신고 양식이 필요하거나 play 빌드에 있으면 안 되는 권한
  6. 비활성화된 GitHub 자체 업데이트 코드가 dex에 남아 있는지(경고만)
"""
import os
import re
import sys
import time
import zipfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
ANDROID = os.path.join(ROOT, "android")
AAB = os.path.join(ANDROID, r"app\build\outputs\bundle\playRelease\app-play-release.aab")
MERGED = os.path.join(ANDROID, r"app\build\intermediates\merged_manifests\playRelease\processPlayReleaseManifest\AndroidManifest.xml")

# play 빌드에 있으면 안 되는 권한
FORBIDDEN = {
    "android.permission.REQUEST_INSTALL_PACKAGES": "Play 밖 자체 업데이트(github 빌드 전용)",
}
# 있으면 Play Console 신고 양식·추가 심사가 필요한 권한
DECLARE = {
    "android.permission.ACCESS_BACKGROUND_LOCATION": "위치 권한 신고 + 시연 동영상",
    "android.permission.QUERY_ALL_PACKAGES": "모든 패키지 조회 신고",
    "android.permission.USE_FULL_SCREEN_INTENT": "전체 화면 인텐트 신고(Android 14+)",
    "android.permission.SCHEDULE_EXACT_ALARM": "정확한 알람 신고",
    "android.permission.USE_EXACT_ALARM": "정확한 알람 신고",
    "android.permission.READ_MEDIA_IMAGES": "사진·동영상 권한 신고",
    "android.permission.READ_MEDIA_VIDEO": "사진·동영상 권한 신고",
    "android.permission.MANAGE_EXTERNAL_STORAGE": "모든 파일 액세스 신고",
    "android.permission.READ_SMS": "SMS·통화 기록 신고",
    "android.permission.READ_CALL_LOG": "SMS·통화 기록 신고",
    "android.permission.BIND_ACCESSIBILITY_SERVICE": "접근성 API 신고",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS": "배터리 최적화 예외 요청(허용 사용 사례 제한)",
    "com.google.android.gms.permission.AD_ID": "광고 ID 신고",
}
UPDATER_MARKERS = [b"application/vnd.android.package-archive", b"api.github.com/repos/dacisosl/CAR2/releases"]


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    problems = 0

    ks = os.path.join(ANDROID, "keystore.properties")
    print("keystore.properties:", "있음" if os.path.exists(ks) else "없음 → release 서명 불가(업로드 키를 먼저 만든다)")
    if not os.path.exists(ks):
        problems += 1

    if not os.path.exists(AAB):
        print("AAB 없음:", AAB)
        return 1
    st = os.stat(AAB)
    print(f"AAB: {AAB}\n  크기 {st.st_size / 1e6:.1f}MB, 만든 시각 {time.strftime('%Y-%m-%d %H:%M', time.localtime(st.st_mtime))}")
    with zipfile.ZipFile(AAB) as z:
        names = z.namelist()
        sig = [n for n in names if re.fullmatch(r"META-INF/[^/]+\.(RSA|EC|DSA)", n)]
        print("  서명:", ", ".join(sig) if sig else "없음 → Play에 올릴 수 없다(keystore.properties를 만든 뒤 다시 빌드)")
        if not sig:
            problems += 1
        updater = [m.decode() for n in names if n.endswith(".dex") for m in UPDATER_MARKERS if m in z.read(n)]
        if updater:
            print("  경고: 비활성 GitHub 업데이트 코드가 dex에 남아 있음 →", ", ".join(sorted(set(updater))))

    if not os.path.exists(MERGED):
        print("병합 매니페스트 없음:", MERGED)
        return 1
    xml = open(MERGED, encoding="utf-8").read()
    ver = re.search(r'versionCode="(\d+)"\s+android:versionName="([^"]+)"', xml)
    tgt = re.search(r'targetSdkVersion="(\d+)"', xml)
    print(f"매니페스트: versionCode {ver.group(1) if ver else '?'}, versionName {ver.group(2) if ver else '?'}, targetSdk {tgt.group(1) if tgt else '?'}")
    if tgt and int(tgt.group(1)) < 36:
        print("  ✗ targetSdk 36 미만: 2026-08-31부터 새 앱·업데이트는 36 이상")
        problems += 1
    if os.path.getmtime(MERGED) > st.st_mtime + 120:
        print("  주의: 매니페스트가 AAB보다 새롭다. AAB를 다시 빌드했는지 확인")
    perms = sorted(set(re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', xml)))
    print("권한:")
    for p in perms:
        print("  -", p)
    for p, why in FORBIDDEN.items():
        if p in perms:
            print("  ✗ play 빌드에 있으면 안 됨:", p, "-", why)
            problems += 1
    for p, why in DECLARE.items():
        if p in perms:
            print("  ⚠ 신고 필요:", p, "-", why)
    print("결과:", "문제 없음" if problems == 0 else f"해결할 항목 {problems}개")
    return 0 if problems == 0 else 2


if __name__ == "__main__":
    sys.exit(main())
