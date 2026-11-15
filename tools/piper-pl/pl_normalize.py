"""Polish text normalisation for TTS: spells out dates, times, temperatures and units.

Piper's espeak-ng reads digits without declension ("15 listopada" -> "piętnaście listopada",
"-2" -> "dwa", "°C" -> "stopień C"). This rewrites only patterns it recognises with confidence and
leaves everything else to espeak. normalize() never raises: on any error it returns the input.
"""
import logging
import re

_LOG = logging.getLogger(__name__)

_UNITS = ["zero", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć"]
_TEENS = ["dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście", "piętnaście",
          "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście"]
_TENS = ["", "", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt", "sześćdziesiąt",
         "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt"]
_HUNDREDS = ["", "sto", "dwieście", "trzysta", "czterysta", "pięćset", "sześćset", "siedemset",
             "osiemset", "dziewięćset"]
_GROUPS = [("tysiąc", "tysiące", "tysięcy"), ("milion", "miliony", "milionów")]

_ORD_UNITS = ["", "pierwsz", "drug", "trzec", "czwart", "piąt", "szóst", "siódm", "ósm", "dziewiąt"]
_ORD_TEENS = ["dziesiąt", "jedenast", "dwunast", "trzynast", "czternast", "piętnast", "szesnast",
              "siedemnast", "osiemnast", "dziewiętnast"]
_ORD_TENS = ["", "", "dwudziest", "trzydziest", "czterdziest", "pięćdziesiąt", "sześćdziesiąt",
             "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt"]
_ORD_HUNDREDS = ["", "setn", "dwusetn", "trzechsetn", "czterechsetn", "pięćsetn", "sześćsetn",
                 "siedemsetn", "osiemsetn", "dziewięćsetn"]
_ORD_THOUSANDS = {1: "tysięczn", 2: "dwutysięczn", 3: "trzytysięczn"}
_ENDINGS = {"m_gen": "ego", "f_nom": "a", "f_gen": "ej", "f_acc": "ą"}

MONTHS = ["stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia",
          "września", "października", "listopada", "grudnia"]


def plural(n: int, forms: tuple) -> str:
    """Pick the Polish form for 1 / 2–4 / 5+ (forms = one, few, many)."""
    if n == 1:
        return forms[0]
    if n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14):
        return forms[1]
    return forms[2]


def _below_1000(n: int) -> list[str]:
    hundreds, rest = divmod(n, 100)
    words = [_HUNDREDS[hundreds]] if hundreds else []
    if 10 <= rest < 20:
        words.append(_TEENS[rest - 10])
    elif rest:
        tens, units = divmod(rest, 10)
        words += [w for w in (_TENS[tens], _UNITS[units] if units else "") if w]
    return words


def cardinal(n: int) -> str:
    if n == 0:
        return "zero"
    words: list[str] = []
    for power, forms in reversed(list(enumerate(_GROUPS, start=1))):
        count, n = divmod(n, 1000 ** power)
        if count:
            words += ([forms[0]] if count == 1 else _below_1000(count) + [plural(count, forms)])
    return " ".join(words + _below_1000(n))


def _ending(stem: str, form: str) -> str:
    ending = _ENDINGS[form]
    if stem.endswith("c") or (stem.endswith(("g", "k")) and ending.startswith("e")):
        ending = "i" + ending
    return stem + ending


def ordinal(n: int, form: str) -> str:
    """Ordinal number; form is m_gen (dates, years), f_nom / f_gen / f_acc (hours)."""
    if n <= 0:
        return cardinal(n)
    thousands, rest = divmod(n, 1000)
    if thousands:
        if rest == 0 and thousands in _ORD_THOUSANDS:
            return _ending(_ORD_THOUSANDS[thousands], form)
        prefix = cardinal(thousands * 1000)
        return prefix if rest == 0 else f"{prefix} {ordinal(rest, form)}"
    hundreds, rest = divmod(n, 100)
    if rest == 0:
        return _ending(_ORD_HUNDREDS[hundreds], form)
    words = [_HUNDREDS[hundreds]] if hundreds else []
    if rest < 10:
        words.append(_ending(_ORD_UNITS[rest], form))
    elif rest < 20:
        words.append(_ending(_ORD_TEENS[rest - 10], form))
    else:
        tens, units = divmod(rest, 10)
        words.append(_ending(_ORD_TENS[tens], form))
        if units:
            words.append(_ending(_ORD_UNITS[units], form))
    return " ".join(words)


def _date(day: str, month: str, year: str | None = None) -> str | None:
    d, m = int(day), int(month)
    if not (1 <= d <= 31 and 1 <= m <= 12):
        return None
    spoken = f"{ordinal(d, 'm_gen')} {MONTHS[m - 1]}"
    return f"{spoken} {ordinal(int(year), 'm_gen')} roku" if year else spoken


def _iso_date(m: re.Match) -> str:
    return _date(m["d"], m["m"], m["y"]) or m[0]


def _full_date(m: re.Match) -> str:
    return _date(m["d"], m["m"], m["y"]) or m[0]


def _dotted_date(m: re.Match) -> str:
    spoken = _date(m["d"], m["m"])
    if spoken is None:
        return m[0]
    after = m.string[m.end():]
    keep_dot = after == "" or re.match(r"\s+[A-ZĄĆĘŁŃÓŚŹŻ]", after)
    return spoken + ("." if keep_dot else "")


def _short_date(m: re.Match) -> str:
    if re.search(r"(?:wersj\w*|v)\s*$", m.string[:m.start()], re.IGNORECASE):
        return m[0]
    return _date(m["d"], m["m"]) or m[0]


def _named_date(m: re.Match) -> str:
    day = int(m["d"])
    if not 1 <= day <= 31:
        return m[0]
    spoken = f"{ordinal(day, 'm_gen')} {m['month']}"
    return f"{spoken} {ordinal(int(m['y']), 'm_gen')} roku" if m["y"] else spoken


_HOUR_CASE = {
    **dict.fromkeys(("o", "po", "przy"), ("godzinie", "f_gen")),
    **dict.fromkeys(("od", "do", "około", "koło"), ("godziny", "f_gen")),
    **dict.fromkeys(("przed", "między", "pomiędzy", "nad"), ("godziną", "f_acc")),
    "na": ("godzinę", "f_acc"),
}
_HOUR_WORD_CASE = {"godzinie": "f_gen", "godziny": "f_gen", "godziną": "f_acc", "godzinę": "f_acc"}


def _time(m: re.Match) -> str:
    prep, godz = m["prep"], m["godz"]
    if m["sep"] == "." and not (prep or godz):
        return m[0]
    godz_word, form = _HOUR_CASE.get((prep or "").lower(), ("godzina", "f_nom"))
    if godz and not prep:
        godz_word, form = godz, _HOUR_WORD_CASE.get(godz.lower(), "f_nom")
    hour, minute = int(m["h"]), int(m["m"])
    words = [prep] if prep else []
    if godz:
        words.append(godz_word)
    words.append(ordinal(hour, form) if hour else "zero")
    if minute:
        words.append(("zero " if minute < 10 else "") + cardinal(minute))
    return " ".join(words)


_UNIT_FORMS = {  # one, few, many, fraction
    "stopień": ("stopień", "stopnie", "stopni", "stopnia"),
    "%": ("procent",) * 4,
    "hpa": ("hektopaskal", "hektopaskale", "hektopaskali", "hektopaskala"),
    "kwh": ("kilowatogodzina", "kilowatogodziny", "kilowatogodzin", "kilowatogodziny"),
    "km/h": ("kilometr na godzinę", "kilometry na godzinę", "kilometrów na godzinę", "kilometra na godzinę"),
    "m/s": ("metr na sekundę", "metry na sekundę", "metrów na sekundę", "metra na sekundę"),
    "mm": ("milimetr", "milimetry", "milimetrów", "milimetra"),
    "cm": ("centymetr", "centymetry", "centymetrów", "centymetra"),
}


def _unit_key(unit: str) -> str:
    unit = unit.lower()
    return "stopień" if unit.startswith(("°", "stop")) else unit


def _fraction(digits: str) -> str:
    if digits == "5":
        return "i pół"
    zeros = len(digits) - len(digits.lstrip("0"))
    rest = digits.lstrip("0")
    return "przecinek " + " ".join(["zero"] * zeros + ([cardinal(int(rest))] if rest else []))


def _measure(m: re.Match) -> str:
    forms = _UNIT_FORMS[_unit_key(m["unit"])]
    number = int(m["int"])
    words = (["minus"] if m["sign"] else []) + [cardinal(number)]
    if m["frac"]:
        words += [_fraction(m["frac"]), forms[3]]
    else:
        words.append(plural(number, forms[:3]))
    return " ".join(words)


def _minus(m: re.Match) -> str:
    before = m.string[:m.start()].rstrip()
    if before and before[-1].isdigit():
        return m[0]
    return f"minus {cardinal(int(m['n']))}"


_MONTH_RE = "|".join(MONTHS)
_ROK = r"(?:\s*(?:r\.|roku\b))?"
_RULES = [
    (re.compile(r"(?<![\d-])(?P<y>\d{4})-(?P<m>\d{2})-(?P<d>\d{2})(?![\d-])" + _ROK), _iso_date),
    (re.compile(r"(?<![\d.])(?P<d>\d{1,2})\.(?P<m>\d{1,2})\.(?P<y>\d{4})(?!\d|\.\d)" + _ROK), _full_date),
    (re.compile(r"(?<![\w.,\-−–])(?P<sign>[-−]\s?)?(?P<int>\d+)(?:[.,](?P<frac>\d+))?\s*"
                r"(?P<unit>°\s?C|°|%|hPa|kWh|km/h|m/s|mm|cm|stopni|stopnie|stopień|stopnia)(?![\w/])"),
     _measure),
    (re.compile(r"(?<!\w)(?:(?P<prep>o|od|do|po|około|koło|przy|przed|między|pomiędzy|nad|na)\s+)?"
                r"(?:(?P<godz>godz\.|godzinie|godziny|godziną|godzinę|godzina)\s+)?"
                r"(?<![\d.:])(?P<h>[01]?\d|2[0-4])(?P<sep>[:.])(?P<m>[0-5]\d)(?![\d:]|\.\d)",
                re.IGNORECASE), _time),
    (re.compile(r"(?<![\d.])(?P<d>\d{1,2})\.(?P<m>\d{1,2})\.(?!\d)"), _dotted_date),
    (re.compile(r"(?<![\d.])(?P<d>\d{1,2})\.(?P<m>\d{2})(?![\d.])"), _short_date),
    (re.compile(rf"(?<![\d.,])(?P<d>\d{{1,2}})(?:-(?:go|ego))?\s+(?P<month>{_MONTH_RE})\b"
                rf"(?:\s+(?P<y>\d{{4}})(?!\d){_ROK})?"), _named_date),
    (re.compile(r"(?:(?<=\s)|^|(?<=\())[-−](?P<n>\d+)(?![\d.,:])"), _minus),
]


def normalize(text: str) -> str:
    """Spell out what espeak would misread. Returns the input unchanged on any error."""
    try:
        result = text
        for pattern, replace in _RULES:
            result = pattern.sub(replace, result)
        return result
    except Exception:  # noqa: BLE001 – fail open, TTS must keep working
        _LOG.exception("normalize failed, using original text")
        return text
