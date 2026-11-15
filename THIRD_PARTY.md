# Third-party components

## Libraries in the app

| Component | License |
|---|---|
| AndroidX, Jetpack Compose, Media3 (ExoPlayer: HLS, RTSP) | Apache-2.0 |
| AndroidX Security Crypto | Apache-2.0 |
| OkHttp / Okio | Apache-2.0 |
| kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| ONNX Runtime for Android (full APK only) | MIT |
| WebRTC (`io.getstream:stream-webrtc-android`, prebuilt libwebrtc) | BSD-3-Clause (WebRTC), Apache-2.0 (packaging) |
| Paparazzi (tests only) | Apache-2.0 |

## Models in `app/src/full/assets/wakeword/` (full APK only)

| File | Origin | License |
|---|---|---|
| `melspectrogram.onnx` | [openWakeWord](https://github.com/dscripka/openWakeWord) feature model | see note |
| `embedding_model.onnx` | openWakeWord's re-implementation of Google's speech_embedding (Google's original: Apache-2.0) | see note |
| `hej_dom.bin` | Trained for this project with `tools/wakeword` | see below |

**Note on licensing.** openWakeWord's code is Apache-2.0. Its README says the included pre-trained models are
licensed **CC BY-NC-SA 4.0**, and it does not say whether that applies to the two shared feature models above.
Until that is clarified, treat the bundled models, and so the wake-word feature, as **non-commercial use only**.
The rest of Wallglass is MIT.

`hej_dom.bin` is a small classifier head trained by this project. Its training data was:
- synthetic speech from Piper voices, macOS `say` and an ElevenLabs voice
- LLM-generated Polish sentences
- openWakeWord's precomputed validation features, which are derived from public audio datasets

No recordings of third parties are included in the repository. For commercial use, retrain the head on data you control and clarify the status of the feature models first.

## Home Assistant integration

`custom_components/wallglass` uses only Home Assistant's own APIs (Apache-2.0) and has no extra requirements.

Home Assistant is a trademark of the Open Home Foundation. Sonoff and NSPanel are trademarks of ITEAD.
This project is not affiliated with either.
