"""Build the audited stage-1 v3 predictive-maintenance artifacts.

Example:
    python scripts/build_v3_preprocessing.py

The script intentionally writes to a new output folder.  It never overwrites
the currently deployed ``pm-api/artifacts`` model files.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys


PROJECT_DIR = Path(__file__).resolve().parents[1]
if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

from app.v3_preprocessing import load_v3_csv, run_v3_preprocessing


DEFAULT_INPUT = PROJECT_DIR / "data" / "predictive_maintenance_timeseries_synthetic_v3.csv"
DEFAULT_OUTPUT = PROJECT_DIR / "outputs" / "12_reproducible_preprocessing"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build reproducible v3 preprocessing artifacts."
    )
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--allow-profile-change",
        action="store_true",
        help=(
            "Allow a dataset whose row/device/event counts differ from the "
            "approved canonical v3 profile. Schema and leakage checks remain."
        ),
    )
    return parser.parse_args()


def write_json(path: Path, payload: object) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    args = parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    raw = load_v3_csv(args.input)
    result = run_v3_preprocessing(
        raw,
        enforce_expected_profile=not args.allow_profile_change,
    )

    artifact_paths = {
        "modeling_base": args.output / "v3_modeling_base.parquet",
        "supervised_features": args.output / "v3_features_71.parquet",
        "normal_only_features": (
            args.output / "v3_features_13_robust.parquet"
        ),
        "failure_events": args.output / "failure_events.parquet",
        "device_baselines": args.output / "device_baselines.parquet",
        "fold_assignment": args.output / "device_fold_assignment.csv",
        "feature_manifest": args.output / "feature_manifest.json",
        "quality_report": args.output / "data_quality_report.json",
    }

    result.modeling_base.to_parquet(
        artifact_paths["modeling_base"], index=False
    )
    result.supervised_features.to_parquet(
        artifact_paths["supervised_features"], index=False
    )
    result.normal_only_features.to_parquet(
        artifact_paths["normal_only_features"], index=False
    )
    result.failure_events.to_parquet(
        artifact_paths["failure_events"], index=False
    )
    result.device_baselines.to_parquet(
        artifact_paths["device_baselines"], index=False
    )
    result.fold_assignment.to_csv(
        artifact_paths["fold_assignment"],
        index=False,
        encoding="utf-8-sig",
    )
    write_json(artifact_paths["feature_manifest"], result.feature_manifest)
    write_json(artifact_paths["quality_report"], result.quality_report)

    artifact_manifest = {
        "schema_version": result.feature_manifest["schema_version"],
        "source_file": str(args.input.resolve()),
        "source_sha256": sha256(args.input),
        "artifacts": {
            name: {
                "path": path.name,
                "bytes": path.stat().st_size,
                "sha256": sha256(path),
            }
            for name, path in artifact_paths.items()
        },
    }
    manifest_path = args.output / "artifact_manifest.json"
    write_json(manifest_path, artifact_manifest)

    print("v3 preprocessing completed")
    print(f"input: {args.input}")
    print(f"output: {args.output}")
    print(f"modeling rows: {len(result.modeling_base):,}")
    print(
        "supervised features: "
        f"{result.feature_manifest['supervised_feature_count']}"
    )
    print(
        "normal-only robust features: "
        f"{len(result.feature_manifest['normal_only_robust_features'])}"
    )
    print(f"validation: {result.quality_report['validation_status']}")


if __name__ == "__main__":
    main()
