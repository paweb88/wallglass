"""Streaming false-alarm check: negatives concatenated into one long stream, processed in
80 ms chunks exactly like the panel (2 consecutive hits above threshold = wake)."""
import json, sys
import numpy as np
from openwakeword.utils import AudioFeatures

h = json.load(open("hej_dom.json"))
w1, b1 = np.array(h["w1"], np.float32), np.array(h["b1"], np.float32)
w2, b2 = np.array(h["w2"], np.float32), np.array(h["b2"], np.float32)

def head(x):
    z = np.maximum(w1 @ x + b1, 0)
    return float(1 / (1 + np.exp(-(w2 @ z + b2)[0])))

def run(stream, thresholds=(0.7, 0.85, 0.95)):
    F = AudioFeatures(inference_framework="onnx")
    hits = {t: 0 for t in thresholds}; wakes = {t: 0 for t in thresholds}; cool = {t: 0 for t in thresholds}
    for i in range(0, len(stream) - 1280, 1280):
        F._streaming_features(stream[i:i + 1280])
        feats = F.get_features(16)
        if feats.shape[1] < 16: continue
        p = head(feats[0].reshape(-1))
        for t in thresholds:
            if cool[t] > 0: cool[t] -= 1; continue
            hits[t] = hits[t] + 1 if p >= t else 0
            if hits[t] >= 2: wakes[t] += 1; hits[t] = 0; cool[t] = 25  # 2 s refractory
    return wakes

clips = []
for name in ("negative", "negative2", "negative3"):
    for c in np.load(f"data/{name}.npy", allow_pickle=True):
        clips.append(c); clips.append(np.zeros(int(16000 * np.random.uniform(0.2, 1.0)), np.float32))
neg_stream = np.clip(np.concatenate(clips), -32768, 32767).astype(np.int16)
hours = len(neg_stream) / 16000 / 3600
wakes = run(neg_stream)
print(f"negatywy {hours:.2f} h:", {t: f"{w} ({w / hours:.1f}/h)" for t, w in wakes.items()})

pos = []
for c in np.load("data/positive3.npy", allow_pickle=True)[-60:]:  # target speaker, mostly the held-out tail
    pos.append(np.zeros(16000, np.float32)); pos.append(c)
pos.append(np.zeros(16000, np.float32))
pw = run(np.clip(np.concatenate(pos), -32768, 32767).astype(np.int16))
print("target speaker (60 clips in a stream):", pw)
