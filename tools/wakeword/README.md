# Wake-word training (“Hej Dom”)

These scripts train the small MLP head that the app runs on top of the openWakeWord feature models.
Everything runs on the CPU. Outputs go to `data/`, which is not committed.

```bash
python3.12 -m venv venv && . venv/bin/activate
pip install openwakeword onnxruntime numpy scipy scikit-learn piper-tts soundfile
```

Put Piper voices in `voices/` (e.g. `pl_PL-gosia-medium`, `pl_PL-darkman-medium`, `pl_PL-mc_speech-medium`,
`en_US-libritts_r-medium`). For real-audio negatives, download openWakeWord's `validation_set_features.npy` into `data/`.

| Step | Script | Output |
|---|---|---|
| 1 | `generate.py`, `generate2.py` | Synthetic positives and confusable negatives (Piper, macOS `say`) with augmentation |
| 2 | `generate_eleven.py` (optional) | Positives in a cloned ElevenLabs voice. Needs `ELEVENLABS_API_KEY` and `ELEVENLABS_VOICE_ID` |
| 3 | `gen_sentences.py`, `gen_negatives.py` | Natural Polish sentences, written by a local LLM through Ollama, spoken by the same voices |
| 4 | `train_v3.py` | Head with hard-negative mining. Reports wakes/hour and recall on held-out data → `hej_dom_v4.json` |
| 5 | `stream_eval.py` | Streaming evaluation, the same way the app runs the model |
| 6 | `export_hdw1.py hej_dom_v4.json ../../app/src/full/assets/wakeword/hej_dom.bin --threshold 0.95` | App model |

A step-by-step guide is in [docs/voice.md](../../docs/voice.md#training-your-own-wake-word). To train another phrase, change the `POSITIVE` texts in the generators. Recordings from the target
device's own microphone improve accuracy the most.
