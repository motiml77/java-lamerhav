# פסקול לסרטוני ההדרכה: מוזיקת רקע רגועה (פד, בס, ביט קל) + צליל "קליק" בכל לחיצת עכבר + "ווש" במעברים.
# python3 audio.py timing.json out.wav   (timing.json: {"dur":..., "clicks":[...], "cuts":[...]})
import json, sys, wave
import numpy as np

info = json.load(open(sys.argv[1]))
SR = 44100
DUR = float(info["dur"]) + 0.5
N = int(SR * DUR)
out = np.zeros((N, 2))
rng = np.random.default_rng(5)


def add(sig, t, gain=1.0, pan=0.0):
    i = int(t * SR)
    if i >= N or i < 0:
        return
    s = sig[: N - i] * gain
    out[i:i + len(s), 0] += s * (1 - max(0, pan))
    out[i:i + len(s), 1] += s * (1 + min(0, pan))


def env(n, a, d):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(a, 1e-4)) * np.exp(-t / d)


def tone(freq, length, a=.02, d=.5, kind="sine"):
    n = int(length * SR)
    t = np.arange(n) / SR
    ph = 2 * np.pi * freq * t
    if kind == "tri":
        w = 2 / np.pi * np.arcsin(np.sin(ph))
    elif kind == "soft":
        w = np.sin(ph) + .3 * np.sin(2 * ph) + .12 * np.sin(3 * ph)
    else:
        w = np.sin(ph)
    return w * env(n, a, d)


def note(m):
    return 440 * 2 ** ((m - 69) / 12)


# --- מוזיקה: Cmaj7 · Am7 · Fmaj7 · G6 (רך), 96 BPM
BPM = 96
beat = 60 / BPM
bar = 4 * beat
chords = [[48, 55, 64, 71], [45, 52, 60, 67], [41, 48, 57, 64], [43, 50, 59, 64]]
t = 0.0
i = 0
while t < DUR:
    ch = chords[i % 4]
    for k, m in enumerate(ch):
        add(tone(note(m + 12), bar * 1.1, a=.35, d=bar * .9, kind="soft"), t, .05, pan=(k - 1.5) * .3)
    add(tone(note(ch[0] - 12), bar, a=.01, d=bar * .5, kind="tri"), t, .16)
    add(tone(note(ch[0] - 12), beat * 1.5, a=.01, d=.25, kind="tri"), t + 2.5 * beat, .1)
    # ארפג'ו קטן
    for s in range(8):
        m = ch[[0, 2, 1, 3, 2, 1, 3, 2][s]] + 24
        add(tone(note(m), beat * .6, a=.005, d=.18), t + s * beat / 2, .035, pan=.4 if s % 2 else -.4)
    # ביט קל
    for b in range(4):
        n = int(.3 * SR); tt = np.arange(n) / SR
        f = 50 + 70 * np.exp(-tt * 30)
        add(np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, .002, .12), t + b * beat, .22 if b % 2 == 0 else .1)
        hn = int(.04 * SR)
        h = np.diff(np.concatenate([[0], rng.standard_normal(hn)])) * env(hn, .001, .01)
        add(h, t + b * beat + beat / 2, .05)
    t += bar
    i += 1

# פתיחה וסיום רכים
fade_in = np.minimum(1, np.arange(N) / (SR * 1.5))
fade_out = np.minimum(1, (N - np.arange(N)) / (SR * 2.0))
out *= (fade_in * fade_out)[:, None]

# --- קליקים של העכבר
for c in info.get("clicks", []):
    n = int(.05 * SR)
    x = rng.standard_normal(n) * env(n, .0005, .006)
    tt = np.arange(n) / SR
    x += np.sin(2 * np.pi * 2400 * tt) * env(n, .0005, .012) * .6
    add(x, c, .35)
    add(tone(1800, .06, a=.001, d=.015), c + .03, .08)

# --- "ווש" במעברים
for c in info.get("cuts", []):
    n = int(.9 * SR)
    noise = rng.standard_normal(n)
    k = np.exp(-((np.arange(n) / SR - .45) ** 2) / .03)
    sm = np.convolve(noise, np.ones(30) / 30, mode="same")
    add(sm * k, c - .45, .5)

out /= max(1e-9, np.max(np.abs(out))) / .8
pcm = (out * 32767).astype(np.int16)
w = wave.open(sys.argv[2], "wb")
w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
w.writeframes(pcm.tobytes())
w.close()
print("audio ok", round(DUR, 1), "s")
