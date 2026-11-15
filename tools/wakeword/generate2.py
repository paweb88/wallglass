"""Extra speaker diversity: LibriTTS multi-speaker voice saying 'hey dom' (+ similar negatives)."""
import json, random
import numpy as np
from scipy.signal import resample_poly
from piper import PiperVoice, SynthesisConfig
from generate import augment  # noqa: E402  (re-runs nothing heavy: guarded below)

random.seed(11)
np.random.seed(11)
voice = PiperVoice.load("voices/en_US-libritts_r-medium.onnx")
rate = voice.config.sample_rate
POS = ["hey dom", "hey, dom", "hay dom", "hey dome", "hey Dom!"]
NEG = ["hey", "dom", "hey Tom", "hey mom", "hey John", "home", "hey there", "okay", "hey dude", "don't",
       "hey Don", "dumb", "hey Bob", "a dog", "hey you", "the dome", "hey Siri", "hello", "come on", "hey, look"]

def synth(text, spk):
    cfg = SynthesisConfig(speaker_id=spk, length_scale=random.uniform(0.8, 1.25),
                          noise_scale=random.uniform(0.4, 0.9), noise_w_scale=random.uniform(0.5, 1.0))
    a = np.concatenate([c.audio_int16_array for c in voice.synthesize(text, syn_config=cfg)]).astype(np.float32)
    return resample_poly(a, 16000, rate).astype(np.float32)

speakers = random.sample(range(voice.config.num_speakers), 400)
pos, pv, neg, nv = [], [], [], []
for spk in speakers:
    c = synth(random.choice(POS), spk)
    pos += [c, augment(c)]; pv += [f"lt{spk}"] * 2
    for t in random.sample(NEG, 2):
        c = synth(t, spk)
        neg += [c, augment(c)]; nv += [f"lt{spk}"] * 2
np.save("data/positive2.npy", np.array(pos, dtype=object), allow_pickle=True)
np.save("data/negative2.npy", np.array(neg, dtype=object), allow_pickle=True)
json.dump(pv, open("data/positive2_voices.json", "w")); json.dump(nv, open("data/negative2_voices.json", "w"))
print("libritts", len(pos), "positives,", len(neg), "negatives")
