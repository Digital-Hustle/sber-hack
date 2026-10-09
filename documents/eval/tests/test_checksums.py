from docs_eval.checksums import inn_complete, inn_valid, mrz_check, mrz_td3, snils_check, snils_format, snils_valid


def test_snils_known_value():
    assert snils_valid("112-233-445 95")
    assert not snils_valid("112-233-445 96")


def test_snils_small_and_special_sums():
    assert snils_check("000000001") == "01"
    assert snils_format("123456789").startswith("123-456-789 ")


def test_inn_known_value():
    assert inn_valid("500100732259")
    assert not inn_valid("500100732258")
    assert inn_complete("5001007322") == "500100732259"


def test_mrz_icao_specimen_digits():
    # Образец ICAO 9303 (Utopia): номер L898902C3, рождение 740812, срок 120415.
    assert mrz_check("L898902C3") == "6"
    assert mrz_check("740812") == "2"
    assert mrz_check("120415") == "9"
    line2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
    composite = line2[0:10] + line2[13:20] + line2[21:43]
    assert mrz_check(composite) == line2[43]


def test_mrz_td3_shape_and_digits():
    l1, l2 = mrz_td3(country="UZB", surname="KARIMOV", given="BOBUR", number="AB1234567",
                     nationality="UZB", birth="900101", sex="M", expiry="300101")
    assert len(l1) == len(l2) == 44
    assert l1.startswith("P<UZBKARIMOV<<BOBUR<")
    assert l2[9] == mrz_check(l2[0:9])
    assert l2[19] == mrz_check(l2[13:19])
    assert l2[27] == mrz_check(l2[21:27])
    assert l2[43] == mrz_check(l2[0:10] + l2[13:20] + l2[21:43])
