"""'Hej Dom' (and negatives) in a cloned ElevenLabs voice (e.g. your own), 16 kHz PCM.

Needs ELEVENLABS_API_KEY and ELEVENLABS_VOICE_ID in the environment."""
import json, os, random, time, urllib.request
import numpy as np
from generate import augment, NEGATIVE

random.seed(21)
KEY = os.environ.get("ELEVENLABS_API_KEY", "")
VOICE = os.environ.get("ELEVENLABS_VOICE_ID", "")
POS = ["Hej Dom", "Hej, dom", "Hej dom!", "hej dom?", "Hej... dom", "Heej dom", "hej Dom.", "Hej, Dom!"]

def tts(text, model, stability, similarity, style, speed):
    body = {"text": text, "model_id": model, "language_code": "pl",
            "voice_settings": {"stability": stability, "similarity_boost": similarity, "style": style, "speed": speed}}
    req = urllib.request.Request(
        f"https://api.elevenlabs.io/v1/text-to-speech/{VOICE}?output_format=pcm_16000",
        data=json.dumps(body).encode(), headers={"xi-api-key": KEY, "Content-Type": "application/json"})
    for attempt in range(3):
        try:
            return np.frombuffer(urllib.request.urlopen(req, timeout=60).read(), dtype="<i2").astype(np.float32)
        except Exception as e:  # rate limit / transient
            print("retry", text, e); time.sleep(3 * (attempt + 1))
    return None

def settings():
    return (random.choice(["eleven_multilingual_v2", "eleven_flash_v2_5"]), random.choice([0.3, 0.45, 0.6, 0.8]),
            random.choice([0.6, 0.8, 0.95]), random.choice([0.0, 0.2, 0.4]), random.choice([0.85, 0.95, 1.0, 1.1, 1.2]))

if __name__ == "__main__":
    if not KEY or not VOICE:
        raise SystemExit("Set ELEVENLABS_API_KEY and ELEVENLABS_VOICE_ID")
    pos, neg = [], []
    for i in range(60):
        a = tts(random.choice(POS), *settings())
        if a is not None and len(a) > 1600: pos.append(a)
    for text in random.sample(NEGATIVE, 40):
        a = tts(text, *settings())
        if a is not None and len(a) > 1600: neg.append(a)
    print("eleven", len(pos), "positives,", len(neg), "negatives")

    def save(name, clips, aug):
        out = []
        for c in clips:
            out.append(c); out += [augment(c) for _ in range(aug)]
        np.save(f"data/{name}.npy", np.array(out, dtype=object), allow_pickle=True)
        json.dump(["eleven-clone"] * len(out), open(f"data/{name}_voices.json", "w"))
        print(name, len(out))

    save("positive3", pos, 4)
    save("negative3", neg, 3)
