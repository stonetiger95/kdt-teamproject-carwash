import cv2
import numpy as np
import torch

from models.plate_cnn_ctc import PlateCNNCTCBase


class PlateCNNCTCRecognizer:
    """
    알파 서버의 고정 OCR 추론 어댑터.

    신규 체크포인트/문자표 로딩, grayscale 48x192 전처리,
    PlateCNNCTC 추론과 CTC 디코딩을 한 경로로 묶는다.
    """

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

        num_classes = int(
            charset.get("size", len(self.characters))
        ) + 1

        config = checkpoint.get("config", {})
        variant = config.get("variant", "base")
        variant = {"lite": "base"}.get(variant, variant)

        if variant != "base":
            raise ValueError(
                f"PlateCNNCTC Base 가중치가 아닙니다: {variant}"
            )

        self.model = PlateCNNCTCBase(
            num_classes=num_classes,
            sequence_length=24,
        ).to(self.device)

        self.model.load_state_dict(
            checkpoint["model_state_dict"]
        )
        self.model.eval()

    def recognize(self, crop_image):
        tensor = self._preprocess(crop_image)

        with torch.no_grad():
            logits = self.model(tensor)

        return self._decode_with_confidence(logits)

    def _preprocess(self, crop_image):
        if crop_image is None or crop_image.size == 0:
            raise ValueError("OCR crop 이미지가 비어 있습니다.")

        gray = cv2.cvtColor(
            crop_image,
            cv2.COLOR_BGR2GRAY,
        )

        # Training uses a direct 48x192 resize. Inference must use the same
        # geometry; aspect-ratio padding here changes character widths and
        # creates a train/deploy distribution mismatch.
        canvas = cv2.resize(
            gray,
            (192, 48),
            interpolation=cv2.INTER_AREA,
        )

        normalized = (
            canvas.astype(np.float32) / 127.5
        ) - 1.0

        # [H, W] → [B, C, H, W]
        tensor = (
            torch.from_numpy(normalized)
            .unsqueeze(0)
            .unsqueeze(0)
            .float()
            .to(self.device)
        )

        return tensor

    def _decode_with_confidence(self, logits):
        # logits: [T, B, C]
        probabilities = torch.softmax(logits, dim=2)
        max_probs, indices = probabilities.max(dim=2)

        indices = indices[:, 0].detach().cpu().tolist()
        max_probs = max_probs[:, 0].detach().cpu().tolist()

        characters = []
        character_probs = []
        previous = None

        for index, probability in zip(indices, max_probs):
            index = int(index)

            if index != previous and index != self.blank_idx:
                if 0 <= index < len(self.characters):
                    characters.append(self.characters[index])
                    character_probs.append(float(probability))

            previous = index

        confidence = (
            sum(character_probs) / len(character_probs)
            if character_probs
            else 0.0
        )

        return "".join(characters), confidence
