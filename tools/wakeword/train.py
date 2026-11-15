"""Train a small MLP head on openWakeWord embeddings for 'Hej Dom' and export weights as JSON."""
import json, random
import numpy as np
from openwakeword.utils import AudioFeatures
from sklearn.neural_network import MLPClassifier
from sklearn.metrics import recall_score

random.seed(1)
np.random.seed(1)
CLIP = 32000  # 2 s -> 16 embedding frames
F = AudioFeatures(inference_framework="onnx")

def place(clip, tail):
    """Put the clip at the end of a 2 s window, followed by `tail` samples of quiet noise."""
    clip = clip[-(CLIP - tail):]
    out = (np.random.randn(CLIP) * 30).astype(np.float32)
    out[CLIP - tail - len(clip): CLIP - tail] += clip
    return np.clip(out, -32768, 32767).astype(np.int16)

def embed(clips):
    x = np.stack(clips)
    e = F.embed_clips(x, batch_size=64)
    return e[:, -16:, :].reshape(len(clips), -1)

def load(name):
    return list(np.load(f"data/{name}.npy", allow_pickle=True)), json.load(open(f"data/{name}_voices.json"))
pos, pv = load("positive"); p2, pv2 = load("positive2"); pos += p2; pv += pv2
neg, nv = load("negative"); n2, nv2 = load("negative2"); neg += n2; nv += nv2
HOLD = "pl_PL-mc_speech-medium"

P = [place(c, random.randint(0, 3200)) for c in pos]
N = [place(c, random.randint(0, 3200)) for c in neg]
# negatives: fragments of positives cut short ('hej' without 'dom') and pure noise/silence
N += [place(c[: len(c) // 2], random.randint(0, 1600)) for c in random.sample(pos, 200)]
N += [(np.random.randn(CLIP) * random.uniform(10, 900)).astype(np.int16) for _ in range(200)]
nvx = nv + ["cut"] * 200 + ["noise"] * 200

XP, XN = embed(P), embed(N)
ptest = np.array([v == HOLD for v in pv])
ntest = np.array([v == HOLD for v in nvx])
Xtr = np.vstack([XP[~ptest], XN[~ntest]]); ytr = np.r_[np.ones((~ptest).sum()), np.zeros((~ntest).sum())]
Xte = np.vstack([XP[ptest], XN[ntest]]); yte = np.r_[np.ones(ptest.sum()), np.zeros(ntest.sum())]

clf = MLPClassifier(hidden_layer_sizes=(64,), alpha=1e-3, max_iter=400, random_state=1, early_stopping=True)
clf.fit(Xtr, ytr)
prob = clf.predict_proba(Xte)[:, 1]
for th in (0.5, 0.7, 0.85, 0.95):
    pred = prob >= th
    fa = (pred & (yte == 0)).sum()
    print(f"próg {th}: wykrycia {recall_score(yte, pred):.2%} głosu testowego, fałszywe {fa}/{int((yte == 0).sum())}")

clf.fit(np.vstack([XP, XN]), np.r_[np.ones(len(XP)), np.zeros(len(XN))])  # final model on all data
json.dump({
    "input": 16 * 96,
    "w1": clf.coefs_[0].T.tolist(), "b1": clf.intercepts_[0].tolist(),
    "w2": clf.coefs_[1].T.tolist(), "b2": clf.intercepts_[1].tolist(),
    "threshold": 0.85,
}, open("hej_dom.json", "w"))
print("zapisano hej_dom.json")
