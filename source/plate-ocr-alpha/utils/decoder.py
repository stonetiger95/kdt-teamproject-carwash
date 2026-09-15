# utils/decoder.py
import torch


def greedy_decode(logits, idx_to_char, blank_idx):
    """
    logits : [1, C, T]

    return
        "123가4567"
    """

    pred_indices = logits.argmax(dim=1)[0].detach().cpu().numpy()

    chars = []
    prev = None

    for idx in pred_indices:
        idx = int(idx)

        if idx != prev:
            if idx != blank_idx:
                chars.append(idx_to_char[idx])

        prev = idx

    return "".join(chars)


def greedy_decode_with_confidence(logits, idx_to_char, blank_idx):
    """
    logits : [1, C, T]

    return
        (plate_text, confidence)
    """

    probs = torch.softmax(logits, dim=1)
    max_probs, pred_indices = probs.max(dim=1)

    pred_indices = pred_indices[0].detach().cpu().numpy()
    max_probs = max_probs[0].detach().cpu().numpy()

    chars = []
    char_probs = []
    prev = None

    for idx, prob in zip(pred_indices, max_probs):
        idx = int(idx)

        if idx != prev:
            if idx != blank_idx:
                chars.append(idx_to_char[idx])
                char_probs.append(float(prob))

        prev = idx

    confidence = float(sum(char_probs) / len(char_probs)) if char_probs else 0.0

    return "".join(chars), confidence

#검증용
def greedy_decode_batch(logits, idx_to_char, blank_idx):
    """
    logits: [B, C, T]
    """
    pred_indices = logits.argmax(dim=1).detach().cpu().numpy()  # [B, T]

    results = []

    for seq in pred_indices:
        chars = []
        prev = None

        for idx in seq:
            idx = int(idx)

            if idx != prev:
                if idx != blank_idx:
                    chars.append(idx_to_char[idx])

            prev = idx

        results.append("".join(chars))

    return results
