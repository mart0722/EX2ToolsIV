# Third-party notices

The original EX2ToolsIV source code is licensed under the MIT License
(see `LICENSE`). The following components keep their own licenses.

## Open-source libraries

- AndroidX AppCompat, Core, ConstraintLayout, RecyclerView — Apache License 2.0
- Material Components for Android — Apache License 2.0
- ZXing Core — Apache License 2.0
- Vosk Android — Apache License 2.0
- Orbitron font — SIL Open Font License 1.1 (`licenses/orbitron-OFL.txt`)

The corresponding license texts are available from the upstream projects and
from the Gradle dependency artifacts.

## Models and media

- `app/src/main/assets/model-ru` — a third-party Russian speech-recognition
  model. It is included as application data and is not covered by the MIT
  license of the original code. Check the model's upstream license before
  redistributing this repository or an APK built from it.
- `app/src/main/assets/lock_sounds` — demo WAV assets. Their redistribution
  rights are not confirmed by the project authors. Replace them with your own
  assets if you publish a fork.
- Background images and some visual assets are included as demo/UI material.
  Their redistribution rights are not confirmed by the project authors.
  Replace them with your own assets if you publish a fork.
- `preview.jpg` is a screenshot of the app interface. The car render and
  background artwork visible in it follow the same asset-rights caveat.

If you are a rights holder and believe an asset should not be distributed,
open an issue and it will be reviewed.
