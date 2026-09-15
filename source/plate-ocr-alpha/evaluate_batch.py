"""Offline, reproducible calibration for YOLO/OCR review thresholds.

This evaluator never writes to the application database. It samples images,
runs the current recognizer, and stores per-image results plus a JSON summary.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import random
import re
import statistics
import time
from pathlib import Path
from typing import Any, Iterable


IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
PLATE_PATTERN = re.compile(r"\d{2,3}[가-힣]\d{4}")
CSV_FIELDS = (
    "sample_index", "image_path", "ground_truth", "detected_plate_number",
    "raw_detected_plate_number", "success", "valid_plate_format", "exact_match",
    "yolo_confidence", "ocr_confidence", "message", "elapsed_ms",
    "yolo_x1", "yolo_y1", "yolo_x2", "yolo_y2",
)


def parse_ground_truth(path: Path) -> str | None:
    match = PLATE_PATTERN.search(path.stem.replace(" ", ""))
    return match.group(0) if match else None


def select_sample(paths: Iterable[Path], sample_size: int, seed: int) -> list[Path]:
    candidates = sorted(
        (path.resolve() for path in paths if path.suffix.lower() in IMAGE_SUFFIXES),
        key=lambda path: str(path).casefold(),
    )
    if sample_size < 1:
        raise ValueError("sample_size must be at least 1")
    if sample_size > len(candidates):
        raise ValueError(
            f"sample_size={sample_size} exceeds available images={len(candidates)}"
        )
    random.Random(seed).shuffle(candidates)
    return candidates[:sample_size]


def _optional_float(value: Any) -> float | None:
    if value in (None, ""):
        return None
    return float(value)


def _optional_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes"}


def prediction_row(
    *, sample_index: int, image_path: Path,
    prediction: dict[str, Any], elapsed_ms: float,
) -> dict[str, Any]:
    truth = parse_ground_truth(image_path)
    detected = prediction.get("detectedPlateNumber")
    bbox = prediction.get("bbox") or {}
    success = bool(prediction.get("success"))
    valid_format = bool(detected and PLATE_PATTERN.fullmatch(str(detected)))
    return {
        "sample_index": sample_index,
        "image_path": str(image_path.resolve()),
        "ground_truth": truth or "",
        "detected_plate_number": detected or "",
        "raw_detected_plate_number": prediction.get("rawDetectedPlateNumber") or "",
        "success": success,
        "valid_plate_format": valid_format,
        "exact_match": bool(truth and detected == truth),
        "yolo_confidence": prediction.get("yoloConfidence"),
        "ocr_confidence": prediction.get("ocrConfidence"),
        "message": prediction.get("message") or "",
        "elapsed_ms": round(elapsed_ms, 3),
        "yolo_x1": bbox.get("x1"), "yolo_y1": bbox.get("y1"),
        "yolo_x2": bbox.get("x2"), "yolo_y2": bbox.get("y2"),
    }


def _confidence_summary(rows: list[dict[str, Any]], key: str) -> dict[str, Any]:
    values = sorted(
        value for row in rows
        if (value := _optional_float(row.get(key))) is not None
    )
    if not values:
        return {"count": 0}
    mean = statistics.fmean(values)
    deviation = statistics.stdev(values) if len(values) > 1 else 0.0

    def percentile(fraction: float) -> float:
        position = (len(values) - 1) * fraction
        lower, upper = math.floor(position), math.ceil(position)
        if lower == upper:
            return values[lower]
        return values[lower] + (values[upper] - values[lower]) * (position - lower)

    return {
        "count": len(values), "mean": mean, "standard_deviation": deviation,
        "minimum": values[0], "p05": percentile(0.05),
        "p10": percentile(0.10), "median": percentile(0.50),
        "maximum": values[-1], "one_sigma_lower": max(0.0, mean - deviation),
        "two_sigma_lower": max(0.0, mean - 2 * deviation),
    }


def _policy_metrics(
    rows: list[dict[str, Any]], yolo_threshold: float, ocr_threshold: float
) -> dict[str, Any]:
    reviews = errors = captured = correct_reviews = 0
    for row in rows:
        success = _optional_bool(row.get("success"))
        valid = _optional_bool(row.get("valid_plate_format"))
        exact = _optional_bool(row.get("exact_match"))
        yolo = _optional_float(row.get("yolo_confidence"))
        ocr = _optional_float(row.get("ocr_confidence"))
        is_error = not exact
        review = (
            not success or not valid or yolo is None or ocr is None
            or yolo < yolo_threshold or ocr < ocr_threshold
        )
        errors += int(is_error)
        reviews += int(review)
        captured += int(is_error and review)
        correct_reviews += int(exact and review)
    return {
        "yolo_threshold": yolo_threshold, "ocr_threshold": ocr_threshold,
        "review_count": reviews, "review_rate": reviews / len(rows) if rows else 0.0,
        "error_count": errors, "captured_error_count": captured,
        "missed_error_count": errors - captured,
        "error_capture_rate": captured / errors if errors else 1.0,
        "correct_but_reviewed_count": correct_reviews,
        "review_precision": captured / reviews if reviews else 1.0,
    }


def build_summary(rows: list[dict[str, Any]], *, seed: int) -> dict[str, Any]:
    yolo = _confidence_summary(rows, "yolo_confidence")
    ocr = _confidence_summary(rows, "ocr_confidence")
    exact = sum(_optional_bool(row.get("exact_match")) for row in rows)
    successful = sum(_optional_bool(row.get("success")) for row in rows)
    valid = sum(_optional_bool(row.get("valid_plate_format")) for row in rows)
    elapsed = sum(_optional_float(row.get("elapsed_ms")) or 0.0 for row in rows)
    policies: dict[str, Any] = {
        "current_fixed_0_8": _policy_metrics(rows, 0.8, 0.8),
    }
    if yolo.get("count") and ocr.get("count"):
        policies["one_sigma_lower"] = _policy_metrics(
            rows, yolo["one_sigma_lower"], ocr["one_sigma_lower"]
        )
        policies["two_sigma_lower"] = _policy_metrics(
            rows, yolo["two_sigma_lower"], ocr["two_sigma_lower"]
        )
    return {
        "sample_seed": seed, "sample_count": len(rows),
        "successful_detection_count": successful,
        "successful_detection_rate": successful / len(rows) if rows else 0.0,
        "valid_plate_format_count": valid, "exact_match_count": exact,
        "exact_match_rate": exact / len(rows) if rows else 0.0,
        "recognition_error_count": len(rows) - exact,
        "elapsed_seconds": elapsed / 1000.0,
        "average_elapsed_ms": elapsed / len(rows) if rows else 0.0,
        "yolo_confidence": yolo, "ocr_confidence": ocr, "policies": policies,
    }


def read_rows(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def write_rows(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def run_evaluation(
    *, recognizer: Any, selected: list[Path], output_csv: Path,
    seed: int, progress_every: int,
) -> dict[str, Any]:
    rows = read_rows(output_csv)
    completed = {str(Path(row["image_path"]).resolve()) for row in rows}
    for index, image_path in enumerate(selected, start=1):
        if str(image_path.resolve()) in completed:
            continue
        started = time.perf_counter()
        prediction = recognizer.test(str(image_path))
        rows.append(prediction_row(
            sample_index=index, image_path=image_path, prediction=prediction,
            elapsed_ms=(time.perf_counter() - started) * 1000.0,
        ))
        if progress_every and len(rows) % progress_every == 0:
            write_rows(output_csv, rows)
            print(f"completed={len(rows)}/{len(selected)}", flush=True)
    rows.sort(key=lambda row: int(row["sample_index"]))
    write_rows(output_csv, rows)
    summary = build_summary(rows, seed=seed)
    output_csv.with_suffix(".summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", type=Path, required=True)
    parser.add_argument("--sample-size", type=int, default=300)
    parser.add_argument("--seed", type=int, default=20260825)
    parser.add_argument("--output", type=Path,
                        default=Path("outputs") / "plate_confidence_calibration.csv")
    parser.add_argument("--progress-every", type=int, default=10)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    selected = select_sample(
        (path for path in args.input_dir.iterdir() if path.is_file()),
        args.sample_size, args.seed,
    )
    from infer import PlateRecognizer

    summary = run_evaluation(
        recognizer=PlateRecognizer(), selected=selected, output_csv=args.output,
        seed=args.seed, progress_every=args.progress_every,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
