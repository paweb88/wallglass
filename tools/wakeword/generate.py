"""Generate 'Hej Dom' positives and Polish negatives as 16 kHz int16 numpy arrays."""
import itertools, json, os, random, subprocess, tempfile, wave
import numpy as np
from scipy.signal import resample_poly
from piper import PiperVoice, SynthesisConfig

random.seed(7)
np.random.seed(7)
OUT = "data"
os.makedirs(OUT, exist_ok=True)
VOICES = {}
def _load():
    for n in ["pl_PL-gosia-medium", "pl_PL-darkman-medium", "pl_PL-mc_speech-medium"]:
        VOICES[n] = PiperVoice.load(f"voices/{n}.onnx")

POSITIVE = ["Hej dom", "Hej, dom", "Hej dom!", "Hej Dom.", "hej dom?", "Hejj dom"]
NEGATIVE = [
    "hej", "dom", "domu", "w domu", "nowy dom", "mój dom", "hej ty", "hej Tom", "hejnał", "okej",
    "hej Dominik", "Dominika", "Domek", "Hej, co tam", "hej hop", "Tom i Jerry", "do domu", "Hela dom",
    "dzień dobry", "dobranoc", "włącz światło", "wyłącz telewizor", "jaka jest pogoda", "chodź tutaj",
    "gdzie są klucze", "zrób herbatę", "idziemy na spacer", "obiad jest gotowy", "zamknij drzwi",
    "otwórz okno", "Kasia, chodź na kolację", "Tomek, myj zęby", "kocham cię", "jutro jest sobota",
    "w telewizji leci film", "pada deszcz", "kupiłem chleb", "zadzwoń do mamy", "gdzie jest pies",
    "hej, słuchaj", "hej, kochanie", "dom jest duży", "ten dom", "domofon dzwoni", "ktoś dzwoni do drzwi",
    "piec grzeje", "rolety w dół", "jaki jest dzisiaj dzień", "posprzątaj pokój", "zgaś lampę",
    "oglądamy bajkę", "hejka", "cześć", "halo", "dom dom dom", "a ja do domu", "Hej, Edek",
    "Hej, mamo", "Hej, tato", "ej, to moje", "hej, zobacz", "zdejmij buty", "idę spać", "dobrze",
    "nie wiem", "może jutro", "pięć minut", "proszę", "dziękuję", "ok", "tak", "nie",
]

def synth(voice, text, length, noise, noise_w):
    cfg = SynthesisConfig(length_scale=length, noise_scale=noise, noise_w_scale=noise_w)
    chunks = [c.audio_int16_array for c in voice.synthesize(text, syn_config=cfg)]
    audio = np.concatenate(chunks).astype(np.float32)
    rate = voice.config.sample_rate
    return resample_poly(audio, 16000, rate).astype(np.float32)

def say(text, voice, rate):
    with tempfile.TemporaryDirectory() as d:
        aiff, wav = os.path.join(d, "a.aiff"), os.path.join(d, "a.wav")
        subprocess.run(["say", "-v", voice, "-r", str(rate), "-o", aiff, text], check=True)
        subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16@16000", "-c", "1", aiff, wav], check=True)
        with wave.open(wav) as w:
            return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32)

def augment(x):
    """Random gain, pitch/speed shift, simple reverb and background noise."""
    y = x.copy()
    shift = random.choice([0.9, 0.95, 1.0, 1.0, 1.05, 1.1])
    if shift != 1.0:
        up, down = int(100 * shift), 100
        y = resample_poly(y, down, up).astype(np.float32)
    if random.random() < 0.5:
        decay = np.exp(-np.linspace(0, random.uniform(4, 9), int(16000 * random.uniform(0.08, 0.3))))
        ir = np.random.randn(len(decay)).astype(np.float32) * decay
        ir[0] = 1.0
        y = np.convolve(y, ir / np.abs(ir).sum() * 3)[: len(y)].astype(np.float32)
    peak = np.abs(y).max() + 1e-6
    y = y / peak * random.uniform(1500, 20000)
    noise = np.random.randn(len(y)).astype(np.float32) * random.uniform(0, 600)
    return np.clip(y + noise, -32768, 32767).astype(np.float32)

def collect(texts, n_param_sets):
    clips = []
    params = list(itertools.product([0.75, 0.9, 1.0, 1.15, 1.3], [0.3, 0.6, 0.9], [0.5, 0.8, 1.1]))
    for name, voice in VOICES.items():
        for text in texts:
            for p in random.sample(params, n_param_sets):
                clips.append((name, synth(voice, text, *p)))
    for text in texts:
        for rate in random.sample([140, 160, 180, 200, 220, 240], 3):
            clips.append(("say-Zosia", say(text, "Zosia", rate)))
    return clips

if __name__ == "__main__":
    _load()
    pos = collect(POSITIVE, 12)
    neg = collect(NEGATIVE, 4)
    print("raw", len(pos), "positives,", len(neg), "negatives")

    def save(name, clips, aug):
        out = []
        for voice, c in clips:
            out.append((voice, c))
            for _ in range(aug):
                out.append((voice, augment(c)))
        np.save(f"{OUT}/{name}.npy", np.array([c for _, c in out], dtype=object), allow_pickle=True)
        json.dump([v for v, _ in out], open(f"{OUT}/{name}_voices.json", "w"))
        print(name, len(out))

    save("positive", pos, 3)
    save("negative", neg, 2)
