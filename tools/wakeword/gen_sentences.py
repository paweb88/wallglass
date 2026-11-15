import json, urllib.request, re
topics = ["rozmowy przy obiedzie", "dzieci i szkoła", "zakupy i gotowanie", "praca zdalna", "pogoda i spacer", "telewizja i filmy",
          "sport i rower", "remont domu", "wakacje i podróże", "zdrowie i lekarz", "auto i warsztat", "ogród i rośliny",
          "zabawa z dziećmi", "sprzątanie", "sprawy urzędowe", "muzyka i koncerty", "gry planszowe", "zwierzęta domowe",
          "wiadomości i polityka lokalna", "rozmowy telefoniczne"]
out = set()
for t in topics:
    prompt = (f"Napisz 60 różnych, krótkich (3–12 słów) zdań po polsku, takich jakie ludzie mówią w domu na temat: {t}. "
              "Potoczny język, różne formy (pytania, polecenia, wykrzyknienia). Bez numeracji, każde zdanie w nowej linii. "
              "Nie używaj słowa 'hej' ani 'dom'.")
    body = {"model": "qwen3.8:27b-nvfp4", "prompt": prompt, "stream": False, "think": False, "options": {"temperature": 0.9, "num_predict": 1500}}
    req = urllib.request.Request("http://127.0.0.1:11434/api/generate", data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    text = json.load(urllib.request.urlopen(req, timeout=300))["response"]
    for line in text.splitlines():
        line = re.sub(r"^[\-\*\d\.\)\s]+", "", line).strip()
        if 3 <= len(line.split()) <= 14 and "hej" not in line.lower(): out.add(line)
    print(t, len(out), flush=True)
json.dump(sorted(out), open("data/sentences_pl.json", "w"), ensure_ascii=False, indent=0)
