import pytest

from pl_normalize import cardinal, normalize, ordinal


@pytest.mark.parametrize("n, words", [
    (0, "zero"), (7, "siedem"), (13, "trzynaście"), (21, "dwadzieścia jeden"),
    (100, "sto"), (215, "dwieście piętnaście"), (1000, "tysiąc"), (2026, "dwa tysiące dwadzieścia sześć"),
    (12000, "dwanaście tysięcy"), (1_000_000, "milion"), (3_500_000, "trzy miliony pięćset tysięcy"),
])
def test_cardinal(n, words):
    assert cardinal(n) == words


@pytest.mark.parametrize("n, form, words", [
    (1, "m_gen", "pierwszego"), (2, "m_gen", "drugiego"), (3, "m_gen", "trzeciego"),
    (21, "m_gen", "dwudziestego pierwszego"), (31, "m_gen", "trzydziestego pierwszego"),
    (2026, "m_gen", "dwa tysiące dwudziestego szóstego"), (2000, "m_gen", "dwutysięcznego"),
    (1999, "m_gen", "tysiąc dziewięćset dziewięćdziesiątego dziewiątego"), (2100, "m_gen", "dwa tysiące setnego"),
    (22, "f_nom", "dwudziesta druga"), (22, "f_gen", "dwudziestej drugiej"), (7, "f_acc", "siódmą"),
    (3, "f_nom", "trzecia"), (3, "f_gen", "trzeciej"), (12, "f_gen", "dwunastej"),
])
def test_ordinal(n, form, words):
    assert ordinal(n, form) == words


@pytest.mark.parametrize("text, spoken", [
    # dates – real Qwen output and the waste schedule format
    ("Dziś jest niedziela, 15 listopada 2026 roku.", "Dziś jest niedziela, piętnastego listopada dwa tysiące dwudziestego szóstego roku."),
    ("Najbliższy wywóz śmieci jest w czwartek 19 listopada – odbiór frakcji Bio.",
     "Najbliższy wywóz śmieci jest w czwartek dziewiętnastego listopada – odbiór frakcji Bio."),
    ("Dziś jest 1 grudnia.", "Dziś jest pierwszego grudnia."),
    ("czw 19.11: Bio", "czw dziewiętnastego listopada: Bio"),
    ("Wywóz śmieci w piątek, 21.11.", "Wywóz śmieci w piątek, dwudziestego pierwszego listopada."),
    ("w czwartek 03.12 jest Bio", "w czwartek trzeciego grudnia jest Bio"),
    ("Termin: 21.11.2026 r.", "Termin: dwudziestego pierwszego listopada dwa tysiące dwudziestego szóstego roku"),
    ("Termin: 2026-11-19.", "Termin: dziewiętnastego listopada dwa tysiące dwudziestego szóstego roku."),
    ("1 stycznia 2027 r. będzie", "pierwszego stycznia dwa tysiące dwudziestego siódmego roku będzie"),
    ("do 15-go listopada", "do piętnastego listopada"),
    # times
    ("Ogrzewanie wyłączy się o 22:15.", "Ogrzewanie wyłączy się o dwudziestej drugiej piętnaście."),
    ("Jest 7:40.", "Jest siódma czterdzieści."),
    ("Budzik na 6:30.", "Budzik na szóstą trzydzieści."),
    ("od 8:00 do 16:05", "od ósmej do szesnastej zero pięć"),
    ("przed 9:00", "przed dziewiątą"),
    ("o godz. 7:40", "o godzinie siódmej czterdzieści"),
    ("Spotkanie o 6.30 rano.", "Spotkanie o szóstej trzydzieści rano."),
    # temperatures and units
    ("temperatura wynosi 20,9°C.", "temperatura wynosi dwadzieścia przecinek dziewięć stopnia."),
    ("W salonie jest 21.5 °C.", "W salonie jest dwadzieścia jeden i pół stopnia."),
    ("Na dworze jest -2 stopni.", "Na dworze jest minus dwa stopnie."),
    ("Na dworze -12°C", "Na dworze minus dwanaście stopni"),
    ("Jutro 1 °C, pojutrze 5°.", "Jutro jeden stopień, pojutrze pięć stopni."),
    ("22 stopnie", "dwadzieścia dwa stopnie"),
    ("ciśnienie 1013 hPa", "ciśnienie tysiąc trzynaście hektopaskali"),
    ("wiatr 12 km/h", "wiatr dwanaście kilometrów na godzinę"),
    ("wiatr 3 m/s", "wiatr trzy metry na sekundę"),
    ("zużycie 2,5 kWh", "zużycie dwa i pół kilowatogodziny"),
    ("wilgotność 48%", "wilgotność czterdzieści osiem procent"),
    ("opady 1 mm", "opady jeden milimetr"),
    # minus sign on its own
    ("od -3 do 5", "od minus trzy do 5"),
    # left alone
    ("Masz 3 zadania.", "Masz 3 zadania."),
    ("adres 192.168.1.10", "adres 192.168.1.10"),
    ("wersja 2.10", "wersja 2.10"),
    ("od 5-7 dni", "od 5-7 dni"),
    ("Jest siódma czterdzieści", "Jest siódma czterdzieści"),
    ("21.5", "21.5"),
    ("32.11", "32.11"),
])
def test_normalize(text, spoken):
    assert normalize(text) == spoken


def test_normalize_never_raises(monkeypatch):
    import pl_normalize

    def boom(_text):
        raise ValueError("bug")

    monkeypatch.setattr(pl_normalize, "_RULES", [(pl_normalize.re.compile("x"), boom)])
    assert pl_normalize.normalize("x 5") == "x 5"
