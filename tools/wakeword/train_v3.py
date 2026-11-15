"""'Hej Dom' head v3: sliding-window negatives from Polish speech + openWakeWord real-audio
features, hard-negative mining, honest held-out evaluation (wakes/hour, recall on the target speaker)."""
import json, random
import numpy as np
from openwakeword.utils import AudioFeatures
from sklearn.neural_network import MLPClassifier

random.seed(9); np.random.seed(9)
F = AudioFeatures(inference_framework="onnx")
CLIP = 32000
load = lambda n: list(np.load(f"data/{n}.npy", allow_pickle=True))

def stream_of(clips, gap=(0.1, 1.0)):
    parts = []
    for c in clips:
        parts.append(np.random.randn(int(16000 * np.random.uniform(*gap))).astype(np.float32) * np.random.uniform(5, 300))
        parts.append(c)
    return np.clip(np.concatenate(parts), -32768, 32767).astype(np.int16)

def windows_from_emb(emb):
    return np.lib.stride_tricks.sliding_window_view(emb, (16, 96))[:, 0].reshape(-1, 16 * 96)

def windows(stream):
    return windows_from_emb(F._get_embeddings(stream))

def place(clip, tail):
    clip = clip[-(CLIP - tail):]
    out = (np.random.randn(CLIP) * np.random.uniform(5, 300)).astype(np.float32)
    out[CLIP - tail - len(clip): CLIP - tail] += clip
    return np.clip(out, -32768, 32767).astype(np.int16)

# ---- positives (target speaker's last 60 clips held out for testing)
p3 = load("positive3")
target_test = p3[-60:]
pos = load("positive") + load("positive2") + p3[:-60]
P = [place(c, t) for c in pos for t in (0, random.randint(800, 2600), random.randint(2600, 5120))]
XP = F.embed_clips(np.stack(P), batch_size=64)[:, -16:, :].reshape(len(P), -1)

# ---- negatives
speech = load("speech_pl"); random.shuffle(speech)
cut = int(len(speech) * 0.8)
acav = np.load("data/validation_set_features.npy").astype(np.float32)
acut = int(len(acav) * 0.8)
confusers = load("negative") + load("negative2") + load("negative3")
XN = np.vstack([
    windows(stream_of(speech[:cut])),
    windows_from_emb(acav[:acut]),
    windows(stream_of(confusers)),
    windows(stream_of([c[: len(c) // 2] for c in random.sample(pos, 400)])),
])
print("pozytywy", len(XP), "| negatywy", len(XN))

def fit(XN):
    reps = max(1, len(XN) // (4 * len(XP)))
    X = np.vstack([np.repeat(XP, reps, axis=0), XN]); y = np.r_[np.ones(len(XP) * reps), np.zeros(len(XN))]
    clf = MLPClassifier(hidden_layer_sizes=(128,), alpha=3e-3, max_iter=200, random_state=1, early_stopping=True)
    return clf.fit(X, y)

clf = fit(XN)
for round_ in range(4):
    hard = XN[clf.predict_proba(XN)[:, 1] > 0.2]
    print(f"runda {round_ + 1}: trudne negatywy {len(hard)}")
    if len(hard) == 0: break
    XN = np.vstack([XN, np.repeat(hard, 6, axis=0)])
    clf = fit(XN)

def wakes(probs, th, need=2):
    n = hits = cool = 0
    for v in probs:
        if cool: cool -= 1; continue
        hits = hits + 1 if v >= th else 0
        if hits >= need: n += 1; hits = 0; cool = 25
    return n

ev_acav = clf.predict_proba(windows_from_emb(acav[acut:]))[:, 1]; h_acav = (len(acav) - acut) * 0.08 / 3600
s_ev = stream_of(speech[cut:]); ev_pl = clf.predict_proba(windows(s_ev))[:, 1]; h_pl = len(s_ev) / 16000 / 3600
ev_pw = clf.predict_proba(windows(stream_of(target_test + [np.zeros(16000, np.float32)], gap=(1.0, 2.0))))[:, 1]
for need in (2, 3):
    for th in (0.5, 0.7, 0.85, 0.95):
        print(f"{need} trafienia, próg {th}: dźwięk ogólny {wakes(ev_acav, th, need) / h_acav:.2f}/h, "
              f"polska mowa {wakes(ev_pl, th, need) / h_pl:.1f}/h, target speaker {wakes(ev_pw, th, need)}/60")

json.dump({"w1": clf.coefs_[0].T.tolist(), "b1": clf.intercepts_[0].tolist(),
           "w2": clf.coefs_[1].T.tolist(), "b2": clf.intercepts_[1].tolist(), "threshold": 0.85},
          open("hej_dom_v4.json", "w"))
print("zapisano hej_dom_v4.json")
