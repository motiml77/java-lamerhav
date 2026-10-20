# פסקול סינתטי לפרומו: ביט, בס, פד, "ווש" במעברים ו"בום" בנחיתות טקסט
import numpy as np, wave, sys

SR, DUR = 44100, 43.0
N = int(SR * DUR)
out = np.zeros((N, 2))
rng = np.random.default_rng(3)
BPM = 124
beat = 60 / BPM


def add(sig, t, gain=1.0, pan=0.0):
    i = int(t * SR)
    if i >= N:
        return
    s = sig[: N - i] * gain
    out[i:i + len(s), 0] += s * (1 - max(0, pan))
    out[i:i + len(s), 1] += s * (1 + min(0, pan))


def env(n, a, d):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(a, 1e-4)) * np.exp(-t / d)


def kick():
    n = int(.45 * SR); t = np.arange(n) / SR
    f = 45 + 110 * np.exp(-t * 28)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, .002, .16)


def hat(open_=False):
    n = int((.18 if open_ else .05) * SR)
    x = rng.standard_normal(n)
    x = np.diff(np.concatenate([[0], x]))          # יותר גבוה
    return x * env(n, .001, .05 if open_ else .012) * .5


def clap():
    n = int(.25 * SR)
    x = rng.standard_normal(n) * env(n, .002, .07)
    return np.convolve(x, np.ones(6) / 6, 'same')


def bass(freq, length):
    n = int(length * SR); t = np.arange(n) / SR
    x = 2 * ((t * freq) % 1) - 1                  # מסור
    x = np.convolve(x, np.ones(18) / 18, 'same')  # מסנן פשוט
    return x * env(n, .005, length * .55) * .55


def pad(freqs, length):
    n = int(length * SR); t = np.arange(n) / SR
    x = sum(np.sin(2 * np.pi * f * t + np.sin(2 * np.pi * .3 * t) * .4) for f in freqs) / len(freqs)
    a = np.minimum(1, t / .6) * np.minimum(1, (length - t) / .6)
    return x * a


def whoosh(length, rise=True):
    n = int(length * SR); t = np.arange(n) / SR
    x = rng.standard_normal(n)
    k = np.linspace(40, 4, n) if rise else np.linspace(4, 40, n)
    y = np.zeros(n); acc = 0
    for i in range(n):                            # מסנן נמוך משתנה
        acc += (x[i] - acc) / k[i]; y[i] = acc
    a = (t / length) ** 2 if rise else (1 - t / length) ** 2
    return y * a * 2.2


def boom():
    n = int(1.2 * SR); t = np.arange(n) / SR
    f = 32 + 60 * np.exp(-t * 9)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, .003, .45)
    nz = rng.standard_normal(n) * env(n, .001, .05) * .35
    return s + nz


def click():
    n = int(.03 * SR)
    return rng.standard_normal(n) * env(n, .0005, .004) * .5


A, F, C, G = 55.0, 43.65, 65.41, 49.0
CHORDS = [(A, [220, 261.6, 329.6]), (F, [174.6, 220, 261.6]), (C, [196, 261.6, 329.6]), (G, [196, 246.9, 293.7])]

# הקלדה
for k in range(14):
    add(click(), .2 + k * .057, .5, rng.uniform(-.3, .3))

# פד לאורך כל הסרטון
bar = beat * 4
t = 0.0; ci = 0
while t < DUR - .5:
    root, fr = CHORDS[ci % 4]
    add(pad(fr, bar + .6), t, .10)
    t += bar; ci += 1

# ביט: מתחיל בנחיתה של "הרצה", מלא מהסצנה הראשונה, נעצר בסוף
START, FULL, END = 1.75, 6.2, 41.0
t = START; i = 0
while t < END:
    in_break = 35.4 < t < 38.3                   # המילים המהירות — רק קיק
    add(kick(), t, .9)
    if t >= FULL and not in_break:
        add(hat(), t + beat / 2, .35, .3)
        add(hat(), t + beat / 4, .15, -.3)
        add(hat(), t + 3 * beat / 4, .15, -.3)
        if i % 2 == 1:
            add(clap(), t, .35)
        root = CHORDS[(int((t - START) / bar)) % 4][0]
        add(bass(root * 2, beat / 2), t + beat / 2, .8)
    t += beat; i += 1
# ריצה של מילים מהירות: קיק על כל מילה
for k in range(8):
    add(kick(), 35.6 + .25 + k * .3, .7)
    add(hat(True), 35.6 + .25 + k * .3, .25, (k % 2) * .6 - .3)

# מעברים ונחיתות
for cut in [6.2, 16.0, 25.8, 35.6]:
    add(whoosh(.5, True), cut - .5, .55)
    add(boom(), cut, .7)
for hit in [1.75, 3.2, 4.6]:
    add(boom(), hit, .6)
for sc in [6.2, 16.0, 25.8]:
    add(whoosh(.45, False), sc + 1.75, .35)     # החלון נכנס
    add(boom(), sc + .25, .35)                  # כותרת הסצנה
# סיום: בום גדול ואקורד ארוך
FIN = 35.6 + .25 + 8 * .3 + .2 + 2.3
add(whoosh(.6, True), FIN - .6, .6)
add(boom(), FIN, 1.0)
add(pad([110, 220, 261.6, 329.6, 440], 43 - FIN), FIN, .22)

# מאסטר
out = np.tanh(out * 1.3)
fade = np.ones(N); fl = int(.6 * SR); fade[-fl:] = np.linspace(1, 0, fl)
out *= fade[:, None]
out /= np.max(np.abs(out)) / .89
pcm = (out * 32767).astype('<i2')
with wave.open(sys.argv[1], 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print('ok')
