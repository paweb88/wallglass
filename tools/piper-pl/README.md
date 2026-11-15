# piper-pl – Polish numbers for Piper

Piper speaks Polish digits without declension: “15 listopada” becomes “piętnaście listopada”, “-2°C” loses the minus,
“o 22:15” becomes “o dwadzieścia dwa piętnaście”. LLM answers are full of such digits.

This is a small Wyoming proxy that sits between Home Assistant and Piper. Before each sentence goes to Piper it spells
out dates, times, temperatures and common units with correct Polish declension (`pl_normalize.py`):

| Text | Spoken |
|---|---|
| `15 listopada 2026 roku` | piętnastego listopada dwa tysiące dwudziestego szóstego roku |
| `czw 19.11` | czw dziewiętnastego listopada |
| `o 22:15`, `na 6:30` | o dwudziestej drugiej piętnaście, na szóstą trzydzieści |
| `20,9°C`, `-2 stopni` | dwadzieścia przecinek dziewięć stopnia, minus dwa stopnie |
| `1013 hPa`, `12 km/h` | tysiąc trzynaście hektopaskali, dwanaście kilometrów na godzinę |

Anything it does not recognise passes through unchanged, and on an error the original text goes to Piper.
The proxy tells HA it does not stream, so HA always sends a whole sentence and numbers are never split.

```bash
python3 -m venv venv && venv/bin/pip install wyoming==1.7.2 pytest
venv/bin/pytest -q                                         # 64 tests
venv/bin/python wyoming_proxy.py --uri tcp://0.0.0.0:10201 --piper-host 127.0.0.1 --piper-port 10200
```

In Home Assistant add a **Wyoming Protocol** integration for this host and port 10201. It appears as a TTS entity
named `piper-pl`. Select it in your Assist pipeline with the same voice as before.
Run it as a service (launchd, systemd) so it restarts with the machine.
