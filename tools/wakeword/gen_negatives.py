"""Natural Polish speech negatives: Qwen-written sentences spoken by Piper voices, macOS Zosia
and (a sample) a cloned ElevenLabs voice."""
import json, random
import numpy as np
from piper import PiperVoice
import generate as g
from generate_eleven import tts as eleven_tts, settings as eleven_settings

random.seed(5); np.random.seed(5)
g._load()
sentences = json.load(open("data/sentences_pl.json"))
random.shuffle(sentences)
params = [(0.8, 0.5, 0.7), (1.0, 0.6, 0.8), (1.15, 0.7, 0.9), (0.9, 0.4, 0.6), (1.25, 0.8, 1.0)]
voices = list(g.VOICES.items())
clips, who = [], []
for i, s in enumerate(sentences):
    name, voice = voices[i % len(voices)]
    clips.append(g.synth(voice, s, *random.choice(params))); who.append(name)
for s in sentences[:300]:
    clips.append(g.say(s, "Zosia", random.choice([150, 180, 210]))); who.append("say-Zosia")
for s in sentences[300:450]:
    a = eleven_tts(s, *eleven_settings())
    if a is not None: clips.append(a); who.append("eleven-clone")
clips = [g.augment(c) if random.random() < 0.5 else c for c in clips]
np.save("data/speech_pl.npy", np.array(clips, dtype=object), allow_pickle=True)
json.dump(who, open("data/speech_pl_voices.json", "w"))
print("zdania", len(clips), "≈", round(sum(len(c) for c in clips) / 16000 / 60), "min")
