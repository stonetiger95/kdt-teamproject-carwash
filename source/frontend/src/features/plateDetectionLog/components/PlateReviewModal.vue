<template>
    <Teleport to="body">
        <div
            v-if="open"
            class="modal-backdrop"
            @click.self="$emit('close')"
        >
            <section class="review-modal" role="dialog" aria-modal="true" aria-labelledby="review-modal-title">
                <header class="modal-header">
                    <div>
                        <p class="modal-eyebrow">번호판 검수</p>
                        <h2 id="review-modal-title">
                            {{ modalTitle }}
                        </h2>
                    </div>
                    <button class="ghost-button" type="button" @click="$emit('close')">
                        닫기
                    </button>
                </header>

                <p class="modal-summary">
                    감지 번호판:
                    <strong>{{ log?.detectedPlateNumber || "-" }}</strong>
                </p>

                <PlateBboxEditor
                    :image-url="imageUrl"
                    :predicted-box="predictedBox"
                    :model-value="reviewForm.reviewedBox"
                    :status="reviewForm.bboxReviewStatus"
                    :padding-ratio="0.12"
                    :readonly="readonly"
                    @update:model-value="update('reviewedBox', $event)"
                    @update:status="update('bboxReviewStatus', $event)"
                />

                <form @submit.prevent="submit">
                    <label>
                        <span>리뷰 상태</span>
                        <select 
                            :value="reviewForm.reviewStatus" 
                            :disabled="readonly"
                            @change="update('reviewStatus', $event.target.value)"
                        >
                            <option value="">선택</option>
                            <option v-for="option in reviewStatusOptions" :key="option.value" :value="option.value">
                                {{ option.label }}
                            </option>
                        </select>
                    </label>

                    <label>
                        <span>수정 번호판</span>
                        <input
                            :value="reviewForm.correctedPlateNumber"
                            :disabled="readonly"
                            type="text"
                            @input="update('correctedPlateNumber', $event.target.value)"
                        >
                    </label>

                    <label>
                        <span>실패/의심 사유</span>
                        <select 
                            :value="reviewForm.failureReason" 
                            :disabled="readonly"
                            @change="update('failureReason', $event.target.value)"
                        >
                            <option value="">선택</option>
                            <option v-for="option in failureReasonOptions" :key="option.value" :value="option.value">
                                {{ option.label }}
                            </option>
                        </select>
                    </label>

                    <label class="checkbox-field">
                        <input
                            :checked="reviewForm.trainingUseYn"
                            type="checkbox"
                            :disabled="readonly"
                            @change="update('trainingUseYn', $event.target.checked)"
                        >
                        학습 데이터로 사용
                    </label>

                    <label class="memo-field">
                        <span>리뷰 메모</span>
                        <textarea
                            :value="reviewForm.reviewMemo"
                            :disabled="readonly"
                            @input="update('reviewMemo', $event.target.value)"
                        ></textarea>
                    </label>

                    <div class="form-actions">
                        <button 
                        v-if="!readonly"
                        class="primary-button" 
                        type="submit" 
                        :disabled="loading">
                            검수완료
                        </button>
                        <button 
                            class="ghost-button" 
                            type="button" 
                            @click="$emit('close')">
                            {{readonly?"닫기":"취소"}}
                        </button>
                    </div>
                </form>
            </section>
        </div>
    </Teleport>
</template>

<script setup>
import { computed } from "vue"
import PlateBboxEditor from "./PlateBboxEditor.vue"

const props = defineProps({
    open: {
        type: Boolean,
        default: false
    },
    log: {
        type: Object,
        default: null
    },
    mode: {
        type: String,
        default: "edit",
        validator: (value) =>
            ["edit", "view"].includes(value)
    },
    imageUrl: {
        type: String,
        default: ""
    },
    reviewForm: {
        type: Object,
        required: true
    },
    reviewStatusOptions: {
        type: Array,
        default: () => []
    },
    failureReasonOptions: {
        type: Array,
        default: () => []
    },
    loading: {
        type: Boolean,
        default: false
    }
})

const emit = defineEmits([
    "close",
    "save",
    "update"
])

const readonly = computed(() =>
    props.mode === "view"
)

const modalTitle = computed(() =>
    readonly.value
        ? "검수 결과 확인"
        : props.log?.reviewStatus &&
          props.log.reviewStatus !== "UNREVIEWED"
            ? "검수 결과 수정"
            : "리뷰하기"
)

const predictedBox = computed(() => {
    const box = {
        x1: props.log?.yoloX1,
        y1: props.log?.yoloY1,
        x2: props.log?.yoloX2,
        y2: props.log?.yoloY2
    }

    return isValidBox(box)
        ? box
        : null
})

const bboxReady = computed(() => {
    const status =
        props.reviewForm.bboxReviewStatus

    if (status === "NO_PLATE") {
        return true
    }

    if (
        status === "ACCEPTED" ||
        status === "CORRECTED"
    ) {
        return isValidBox(
            props.reviewForm.reviewedBox
        )
    }

    return false
})

const canSave = computed(() =>
    !props.loading &&
    Boolean(props.reviewForm.reviewStatus) &&
    bboxReady.value
)

function update(field, value) {
    if (readonly.value) {
        return
    }

    emit("update", {
        field,
        value
    })
}

function submit() {
    if (readonly.value || !canSave.value) {
        return
    }

    emit("save")
}

function isValidBox(box) {
    if (!box) {
        return false
    }

    const coordinates = [
        box.x1,
        box.y1,
        box.x2,
        box.y2
    ]

    return (
        coordinates.every(Number.isFinite) &&
        box.x1 >= 0 &&
        box.y1 >= 0 &&
        box.x2 > box.x1 &&
        box.y2 > box.y1
    )
}
</script>

<style scoped>
.modal-backdrop {
    position: fixed;
    inset: 0;
    z-index: 1000;
    display: grid;
    place-items: center;
    padding: 20px;
    background: rgba(7, 27, 58, 0.55);
}

.review-modal {
    width: min(1080px, 100%);
    max-height: 90vh;
    overflow-y: auto;
    padding: 24px;
    border-radius: 12px;
    background: #ffffff;
    box-shadow: 0 24px 60px rgba(0, 0, 0, 0.25);
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.modal-header,
.form-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
}

.modal-header {
    margin-bottom: 18px;
}

.modal-eyebrow {
    margin: 0 0 4px;
    color: #52637d;
    font-size: 13px;
    font-weight: 800;
}

h2 {
    margin: 0;
    font-size: 24px;
}

.modal-summary {
    margin: 0 0 18px;
    padding: 12px;
    border-radius: 8px;
    background: #f5f8fc;
}

.plate-image {
    margin: 0 0 20px;
}

.plate-image img {
    display: block;
    width: 100%;
    max-height: 360px;
    object-fit: contain;
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #f8fafc;
}

.empty-image {
    display: grid;
    place-items: center;
    min-height: 180px;
    border: 1px dashed #cbd6e6;
    border-radius: 8px;
    color: #52637d;
    background: #f8fafc;
    font-weight: 800;
}

form {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
}

label {
    display: grid;
    gap: 7px;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
}

select,
input,
textarea {
    min-height: 40px;
    box-sizing: border-box;
    padding: 0 12px;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    color: #071b3a;
    background: #ffffff;
    font: inherit;
}

textarea {
    min-height: 96px;
    padding: 12px;
    resize: vertical;
}

.checkbox-field {
    display: flex;
    align-items: center;
    gap: 8px;
    align-self: end;
    min-height: 40px;
}

.checkbox-field input {
    width: auto;
    min-height: auto;
}

.memo-field,
.form-actions {
    grid-column: 1 / -1;
}

.form-actions {
    justify-content: end;
    margin-top: 4px;
}

.primary-button,
.ghost-button {
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-radius: 8px;
    font: inherit;
    font-weight: 900;
    cursor: pointer;
}

.primary-button {
    color: #ffffff;
    background: #071b3a;
}

.ghost-button {
    color: #071b3a;
    background: #ffffff;
    border-color: #cbd6e6;
}

select:disabled,
input:disabled,
textarea:disabled {
    color: #52637d;
    background: #edf2f8;
    cursor: default;
    opacity: 1;
}

@media (max-width: 700px) {
    form {
        grid-template-columns: 1fr;
    }

    .form-actions {
        justify-content: stretch;
    }

    .form-actions button {
        flex: 1;
    }
}
</style>
