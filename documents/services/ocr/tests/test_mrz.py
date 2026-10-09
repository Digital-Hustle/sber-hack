from ocr import mrz

# Образец ICAO 9303 (вымышленный «UTOPIA»), контрольные цифры корректны.
TD3 = ["P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", "L898902C36UTO7408122F1204159ZE184226B<<<<<10"]
TD1 = ["I<UTOD231458907<<<<<<<<<<<<<<<", "7408122F1204159UTO<<<<<<<<<<<6", "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"]


def test_check_digit() -> None:
    assert mrz.check_digit("L898902C3") == "6"
    assert mrz.check_digit("740812") == "2"
    assert mrz.check_digit("120415") == "9"


def test_td3_parsed_and_valid() -> None:
    m = mrz.find("\n".join(["Some header text", *TD3]))
    assert m is not None
    assert m.format == "TD3"
    assert m.valid
    assert m.fields["surname"] == "ERIKSSON"
    assert m.fields["givenNames"] == "ANNA MARIA"
    assert m.fields["documentNumber"] == "L898902C3"
    assert m.fields["birthDate"] == "740812"


def test_td3_detects_wrong_check_digit() -> None:
    broken = TD3[1][:9] + "5" + TD3[1][10:]
    m = mrz.find("\n".join([TD3[0], broken]))
    assert m is not None
    assert not m.valid
    assert {c.name for c in m.checks if not c.ok} >= {"documentNumber"}


def test_td1_parsed() -> None:
    m = mrz.find("\n".join(TD1))
    assert m is not None
    assert m.format == "TD1"
    assert m.fields["documentNumber"] == "D23145890"
    assert m.fields["surname"] == "ERIKSSON"
    assert m.valid


def test_no_mrz_in_plain_text() -> None:
    assert mrz.find("Паспорт гражданина\nРоссийской Федерации") is None
