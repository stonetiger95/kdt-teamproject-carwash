import re


PLATE_PATTERN = re.compile(r"^\d{2,3}[가-힣]\d{4}$")


def normalize_plate(value):
    if value is None:
        return ""
    text = str(value).strip().upper()
    text = re.sub(r"[\s\-_]+", "", text)
    text = re.sub(r"[^0-9A-Z가-힣]", "", text)
    return text


def is_valid_plate(value):
    return bool(PLATE_PATTERN.fullmatch(normalize_plate(value)))


def repair_plate_candidate(value):
    text = normalize_plate(value)
    if is_valid_plate(text):
        return text

    hangul_matches = list(re.finditer(r"[가-힣]", text))
    if not hangul_matches:
        return text

    for match in hangul_matches:
        hangul = match.group(0)
        left_digits = re.sub(r"\D", "", text[: match.start()])
        right_digits = re.sub(r"\D", "", text[match.end() :])

        for prefix_len in (3, 2):
            if len(left_digits) < prefix_len or len(right_digits) < 4:
                continue
            candidate = left_digits[-prefix_len:] + hangul + right_digits[-4:]
            if is_valid_plate(candidate):
                return candidate

    return text


def postprocess_plate(value):
    return repair_plate_candidate(value)


def plate_error_type(label, prediction):
    label = normalize_plate(label)
    prediction = postprocess_plate(prediction)

    if not prediction:
        return "empty_prediction"
    if label == prediction:
        return "exact_match"
    if not is_valid_plate(prediction):
        if not re.search(r"[가-힣]", prediction):
            return "missing_hangul"
        return "invalid_format"
    return "valid_format_mismatch"


def levenshtein_distance(a, b):
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)

    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, start=1):
        current = [i]
        for j, cb in enumerate(b, start=1):
            current.append(
                min(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + (ca != cb),
                )
            )
        previous = current
    return previous[-1]


def plate_similarity(label, prediction):
    label = normalize_plate(label)
    prediction = normalize_plate(prediction)
    max_len = max(len(label), len(prediction))
    if max_len == 0:
        return 1.0
    return max(0.0, 1.0 - levenshtein_distance(label, prediction) / max_len)
