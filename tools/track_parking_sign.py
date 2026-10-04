"""주차 영상 속 기둥 표지판 위치를 프레임마다 추적해 Kotlin 파일(ParkingSignTrack.kt)로 만든다.

사용:
  1) ffmpeg -i android/app/src/main/res/raw/parking_reverse.mp4 frames/f%03d.png
  2) python tools/track_parking_sign.py frames  (마지막 프레임의 표지판 중심을 시작점으로 거꾸로 추적)

표지판 안쪽 흰 영역을 이전 프레임 중심에서 flood fill로 찾고, 왼쪽·오른쪽 끝과
윗변·아랫변(원근으로 기울어진 직선)을 맞춘다. 2프레임마다 기록하고 3프레임 이동평균으로 흔들림을 줄인다.
"""
import glob
import os
import sys
from collections import deque

import numpy as np
from PIL import Image

W, H = 600.0, 540.0
LAST_FRAME_SEED = (487, 138)   # 마지막 프레임에서 표지판 안쪽 한 점(600×540 기준)
OUT = os.path.join(os.path.dirname(__file__), "..", "android", "app", "src", "main", "java",
                   "app", "car", "parking", "ui", "drawer", "ParkingSignTrack.kt")


def region_from(binimg, seed):
    h, w = binimg.shape
    sx, sy = seed
    if not binimg[sy, sx]:
        for r in range(1, 6):
            hit = next(((sx + dx, sy + dy) for dy in range(-r, r + 1) for dx in range(-r, r + 1)
                        if 0 <= sx + dx < w and 0 <= sy + dy < h and binimg[sy + dy, sx + dx]), None)
            if hit:
                sx, sy = hit
                break
        else:
            return None
    seen = np.zeros_like(binimg, dtype=bool)
    q = deque([(sx, sy)])
    seen[sy, sx] = True
    pts = []
    while q:
        x, y = q.popleft()
        pts.append((x, y))
        if len(pts) > 20000:   # 표지판 밖으로 샜다
            return None
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not seen[ny, nx] and binimg[ny, nx]:
                seen[ny, nx] = True
                q.append((nx, ny))
    return np.array(pts)


def main(frames_dir):
    files = sorted(glob.glob(os.path.join(frames_dir, "f*.png")))
    seed = LAST_FRAME_SEED
    rows = [None] * len(files)
    for i in range(len(files) - 1, -1, -1):
        g = np.array(Image.open(files[i]).convert("L"))
        pts = region_from(g > 128, seed)
        if pts is None:
            sys.exit(f"tracking lost at frame {i}")
        xs, ys = pts[:, 0], pts[:, 1]
        left, right = int(xs.min()), int(xs.max())
        cols = np.arange(left + 2, right - 1)
        ta, tb = np.polyfit(cols, [ys[xs == c].min() for c in cols], 1)
        ba, bb = np.polyfit(cols, [ys[xs == c].max() for c in cols], 1)
        l, r = left - 0.5, right + 0.5
        rows[i] = [l / W, r / W, (ta * l + tb) / H, (ta * r + tb) / H, (ba * l + bb) / H, (ba * r + bb) / H]
        mid = (left + right) / 2
        seed = (int(mid), int(((ta * mid + tb) + (ba * mid + bb)) / 2))
    a = np.array(rows)
    sm = a.copy()
    for i in range(1, len(a) - 1):
        sm[i] = (a[i - 1] + a[i] + a[i + 1]) / 3
    keys = sm[::2]
    if (len(sm) - 1) % 2:
        keys = np.vstack([keys, sm[-1]])
    print(f"{len(keys)} keyframes; paste into {OUT} (KEYS)")
    for k in keys:
        print("        " + ", ".join("%.4ff" % v for v in k) + ",")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "frames")
