# Voice assistant

The panel is a thin client: it listens, sends audio to a Home Assistant **Assist pipeline**
(`assist_pipeline/run`, speech-to-text → conversation → text-to-speech), shows the transcript and answer,
and plays the reply. Recognition, intent handling, device control and the voice all happen in HA. Any
combination works: Whisper or Speech-to-Phrase or HA Cloud for STT, the built-in agent or an LLM, Piper or Cloud for TTS.

## Starting it

- Say **“Hej Dom”** (wake word, detected on the panel; only in the full APK, see below), or
- tap the **microphone button** on the start screen, or
- swipe up on the start screen (default gesture).

A short beep means the panel is listening. After each answer it **listens again** in the same conversation, so you
can ask a follow-up or answer the assistant's question. End the conversation by saying “OK”, “dzięki”, “dziękuję”,
“to wszystko”, “koniec” or “nieważne”, or just stay silent for 6 seconds: the panel goes back to the start screen
and to sleep. A closing word counts only as a reply to the assistant, so “OK, turn on the light” right after the wake
word works normally.

The panel uses your **preferred** Assist pipeline. Set it in *Settings → Voice assistants*.
At night, and while **quiet mode** is on, the answer is shown as text only, without sound.

**Quiet mode** silences the panel's own voice. Toggle it with the speaker button on the start screen, in the panel
settings, or with the panel's *Quiet mode* switch entity in HA. Expose that switch to Assist (give it an alias like
“panel”) and “Hej Dom, mute the panel” works too.

**Reminders by voice** need a small script, because Assist's list intent cannot set a due time.
See [automations](automations.md#reminders).

## Full or lite APK

| APK | Wake word | Size |
|---|---|---|
| `wallglass-<version>.apk` | “Hej Dom” on the device (ONNX Runtime + models) | ~63 MB |
| `wallglass-<version>-lite.apk` | none; start the assistant with the button or a swipe | ~29 MB |

Everything else is the same, including the assistant and talking to the gate. Both are signed with the same key,
so you can switch by installing the other one over it.

## Example local setup

This setup answers in about 2.5–3 s after you stop speaking:

| Part | What | Notes |
|---|---|---|
| STT | whisper.cpp `large-v3-turbo` behind a Wyoming server | ~0.2 s on Apple Silicon (Metal) |
| Agent | Ollama conversation integration with a local Qwen model, *prefer handling commands locally* on | Keep the model loaded (`keep_alive: -1`) |
| TTS | Piper (`wyoming-piper`) with a Polish voice | ~0.3 s |
| TTS fix | [`tools/piper-pl`](../tools/piper-pl) between HA and Piper | Spells out Polish dates, times and temperatures with correct declension |

Expose only the entities you want controlled by voice: *Settings → Voice assistants → Expose*.
Don't expose gates, garage doors or water and gas valves.

## The “Hej Dom” wake word

- Runs fully on the panel with ONNX Runtime and the [openWakeWord](https://github.com/dscripka/openWakeWord) feature
  models: a mel-spectrogram, then a speech embedding, then a small MLP head trained for “Hej Dom”
  (`app/src/full/assets/wakeword/hej_dom.bin`).
- Nothing is sent anywhere until the word is detected. The microphone is then handed over to the assistant.
- You can turn it off on the panel (long-press the clock → *Hej Dom*).
- Logcat shows the levels every 2 s: `adb logcat -s WakeWord:* VoiceAssistant:* MicInput:*`
  (`peakRms` is the microphone level, `maxP` the highest score). Use these to tune the threshold or the gain.
- **Quiet-room guard.** Most false wakes happen in a silent room: the model reacts to amplified background hiss.
  A detection therefore only counts when something at speech level (`MIN_SPEECH_RMS`, 3000 after the gain) was
  heard in the last ~1.2 s. Rejected ones are logged as `Odrzucono wybudzenie – za cicho`. If real calls get
  rejected on your device, lower the value in `voice/LoudnessGate.kt`.

On held-out test data, the bundled model (v4) detects the target voice in about 95% of cases.
It gives about 3 false wakes per hour on general audio and none on Polish speech, at threshold 0.95 with 2 consecutive hits.
Accuracy for other voices and rooms will vary.
Recordings from your own panel's microphone help the most.

### Training your own wake word

You can train “Hej Dom” for your own voice and room, or a different phrase. It is a CPU-only process that takes
about an hour on a laptop. The scripts and a step table are in [`tools/wakeword`](../tools/wakeword).

1. **Set up** (once):
   ```bash
   cd tools/wakeword
   python3.12 -m venv venv && . venv/bin/activate
   pip install openwakeword onnxruntime numpy scipy scikit-learn piper-tts soundfile
   ```
   Put a few Piper voices in `voices/`, and download openWakeWord's `validation_set_features.npy` into `data/`
   (real-audio negatives).
2. **Choose the phrase.** For a different phrase, edit `POSITIVE` in `generate.py` (and the positives in
   `generate2.py` / `generate_eleven.py`). Two syllables or more, not a common word.
3. **Generate examples:** `python generate.py && python generate2.py`, optionally `python generate_eleven.py`
   with a cloned voice (`ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID`), then `python gen_sentences.py && python gen_negatives.py`
   for natural sentences that must *not* wake the panel.
4. **Add your own recordings** (best gain). Record the phrase 30–50 times on the panel itself (or at the same
   distance), plus a few minutes of normal room sound, as 16 kHz mono WAV, and add them to the positives and negatives.
5. **Train:** `python train_v3.py`. It prints recall and false wakes per hour on held-out data. Aim for under 1 false
   wake per hour.
6. **Check it like the app does:** `python stream_eval.py`.
7. **Export and build:**
   ```bash
   python export_hdw1.py hej_dom_v4.json ../../app/src/full/assets/wakeword/hej_dom.bin --threshold 0.95
   cd ../.. && ./gradlew assembleFullDebug
   ```
   The file name stays `hej_dom.bin` even for another phrase.

The `HDW1` format is little-endian:
`"HDW1"`, `int32 inputs`, `int32 hidden`, `float32 threshold`, then `w1[hidden×inputs]`, `b1[hidden]`, `w2[hidden]`, `b2[1]`.
The input is 16 frames × 96 embedding values.

## Microphone notes (NSPanel Pro)

The NSPanel Pro's PDM microphone is very quiet. The app records with `VOICE_RECOGNITION`, the platform AGC and
noise suppression, and applies software gain ×8.
