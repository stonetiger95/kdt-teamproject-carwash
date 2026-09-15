import math
import re

import cv2
import numpy as np
import torch

from models.plate_cnn_ctc import PlateCNNCTCLite


PLATE_PATTERN = re.compile(r"^\d{2,3}[가-힣]\d{4}$")
NEG_INF = -float("inf")


class Lite050Recognizer:
    """Final Lite050 OCR with the locked-evaluation beam5 format decoder."""

    def __init__(self, weight_path: str, device: str):
        self.device = torch.device(device)
        checkpoint = torch.load(
            weight_path,
            map_location=self.device,
            weights_only=False,
        )

        charset = checkpoint["charset"]
        self.characters = list(charset["characters"])
        self.blank_idx = int(charset["blank_index"])
        config = checkpoint.get("config", {})

        variant = str(config.get("variant", "")).lower()
        if variant not in {"lite050", "lite_050"}:
            raise ValueError(f"Lite050 checkpoint required, got variant={variant!r}")

        self.model = PlateCNNCTCLite(
            num_classes=len(self.characters) + 1,
            width_multiplier=float(config.get("width_multiplier", 0.5)),
            sequence_length=int(config.get("sequence_length", 24)),
        ).to(self.device)
        self.model.load_state_dict(checkpoint["model_state_dict"], strict=True)
        self.model.eval()

    def recognize(self, crop_image):
        tensor = self._preprocess(crop_image)
        with torch.inference_mode():
            logits = self.model(tensor)[:, 0, :]

        log_probs = torch.log_softmax(logits.float(), dim=1).cpu().numpy()
        candidates = ctc_prefix_beam_search(
            log_probs,
            characters=self.characters,
            blank_index=self.blank_idx,
            beam_width=5,
        )
        raw_text, raw_score = candidates[0]
        selected_text, selected_score = next(
            (candidate for candidate in candidates if PLATE_PATTERN.fullmatch(candidate[0])),
            (raw_text, raw_score),
        )
        confidence = math.exp(selected_score / max(len(selected_text), 1))
        return selected_text, float(confidence)

    def _preprocess(self, crop_image):
        if crop_image is None or crop_image.size == 0:
            raise ValueError("OCR crop image is empty.")

        if crop_image.ndim == 2:
            gray = crop_image
        elif crop_image.shape[2] == 4:
            gray = cv2.cvtColor(crop_image, cv2.COLOR_BGRA2GRAY)
        else:
            gray = cv2.cvtColor(crop_image, cv2.COLOR_BGR2GRAY)

        interpolation = cv2.INTER_AREA if gray.shape[0] > 48 else cv2.INTER_CUBIC
        resized = cv2.resize(gray, (192, 48), interpolation=interpolation)
        normalized = resized.astype(np.float32) / 127.5 - 1.0
        return (
            torch.from_numpy(normalized)
            .unsqueeze(0)
            .unsqueeze(0)
            .float()
            .to(self.device)
        )


def ctc_prefix_beam_search(
    log_probs: np.ndarray,
    *,
    characters: list[str],
    blank_index: int,
    beam_width: int,
) -> list[tuple[str, float]]:
    """CTC prefix beam search matching the final locked benchmark."""
    if log_probs.ndim != 2:
        raise ValueError(f"Expected [T,C] log probabilities, got {log_probs.shape}")

    beams: dict[tuple[int, ...], tuple[float, float]] = {(): (0.0, NEG_INF)}
    for timestep in range(log_probs.shape[0]):
        next_beams: dict[tuple[int, ...], tuple[float, float]] = {}
        for prefix, (p_blank, p_nonblank) in beams.items():
            for char_index in range(log_probs.shape[1]):
                probability = float(log_probs[timestep, char_index])
                existing_blank, existing_nonblank = next_beams.get(
                    prefix, (NEG_INF, NEG_INF)
                )
                if char_index == blank_index:
                    next_beams[prefix] = (
                        _logaddexp(existing_blank, p_blank + probability, p_nonblank + probability),
                        existing_nonblank,
                    )
                    continue

                last = prefix[-1] if prefix else None
                if char_index == last:
                    next_beams[prefix] = (
                        existing_blank,
                        _logaddexp(existing_nonblank, p_nonblank + probability),
                    )
                    extended = prefix + (char_index,)
                    ext_blank, ext_nonblank = next_beams.get(extended, (NEG_INF, NEG_INF))
                    next_beams[extended] = (
                        ext_blank,
                        _logaddexp(ext_nonblank, p_blank + probability),
                    )
                else:
                    extended = prefix + (char_index,)
                    ext_blank, ext_nonblank = next_beams.get(extended, (NEG_INF, NEG_INF))
                    next_beams[extended] = (
                        ext_blank,
                        _logaddexp(
                            ext_nonblank,
                            p_blank + probability,
                            p_nonblank + probability,
                        ),
                    )

        ranked = sorted(
            next_beams.items(),
            key=lambda item: _logaddexp(item[1][0], item[1][1]),
            reverse=True,
        )[:beam_width]
        beams = dict(ranked)

    results = [
        (
            "".join(characters[index] for index in prefix),
            _logaddexp(p_blank, p_nonblank),
        )
        for prefix, (p_blank, p_nonblank) in beams.items()
    ]
    return sorted(results, key=lambda item: item[1], reverse=True)


def _logaddexp(*values: float) -> float:
    finite = [value for value in values if value != NEG_INF]
    if not finite:
        return NEG_INF
    maximum = max(finite)
    return maximum + math.log(sum(math.exp(value - maximum) for value in finite))
