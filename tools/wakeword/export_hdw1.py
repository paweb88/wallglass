"""Convert a trained head (JSON from train_v3.py) into the app's HDW1 binary.

usage: python export_hdw1.py hej_dom_v4.json ../../app/src/full/assets/wakeword/hej_dom.bin [--threshold 0.95]
"""
import argparse, json, struct
import numpy as np

p = argparse.ArgumentParser()
p.add_argument("src"); p.add_argument("dst")
p.add_argument("--threshold", type=float, default=None, help="override the threshold stored in the JSON")
a = p.parse_args()

m = json.load(open(a.src))
w1, b1 = np.asarray(m["w1"], "<f4"), np.asarray(m["b1"], "<f4")      # w1: hidden × inputs
w2, b2 = np.asarray(m["w2"], "<f4").reshape(-1), np.asarray(m["b2"], "<f4").reshape(-1)
hidden, inputs = w1.shape
assert b1.shape == (hidden,) and w2.shape == (hidden,) and b2.shape == (1,), "unexpected layer shapes"
threshold = a.threshold if a.threshold is not None else float(m.get("threshold", 0.5))
with open(a.dst, "wb") as f:
    f.write(b"HDW1" + struct.pack("<iif", inputs, hidden, threshold))
    for arr in (w1, b1, w2, b2):
        f.write(arr.tobytes())
print(f"{a.dst}: inputs={inputs} hidden={hidden} threshold={threshold}")
