<template>
    <main class="plate-log-page">
        <section class="page-hero">
            <div>
                <p class="eyebrow"></p>
                <h1>번호판 탐지 로그 관리</h1>
                <p>차량 번호 인식 결과와 검토 상태를 확인합니다.</p>
            </div>
        </section>

        <section class="filter-card">
            <label>
                <span>시작일</span>
                <input v-model="startDate" type="date" :max="endDate || undefined">
            </label>

            <label>
                <span>종료일</span>
                <input v-model="endDate" type="date" :min="startDate || undefined">
            </label>

            <label class="plate-search-field">
                <span>번호판 검색</span>
                <input
                    v-model.trim="plateNumber"
                    type="search"
                    placeholder="예: 12가3456"
                    @keyup.enter="searchLogs"
                >
            </label>

            <label>
                <span>조회 조건</span>
                <select v-model="selectedFilter">
                    <option
                        v-for="option in store.logFilterOptions"
                        :key="option.value"
                        :value="option.value"
                    >
                        {{ option.label }}
                    </option>
                </select>
            </label>

            <button
                class="primary-button"
                type="button"
                @click="searchLogs"
            >
                조회
            </button>
        </section>
        <section class="training-export-card">
            <div class="training-export-info">
                <h2>학습 데이터 내보내기</h2>
                <p>
                    학습 후보 이미지와 검수된 번호판·bbox 정보를
                    ZIP 파일로 다운로드합니다.
                </p>
            </div>

            <div class="training-export-actions">
                <button
                    class="primary-button"
                    type="button"
                    :disabled="store.downloadingTrainingData"
                    @click="store.downloadTrainingData()"
                >
                    {{
                        store.downloadingTrainingData
                            ? "ZIP 생성 중..."
                            : "학습 데이터 다운로드"
                    }}
                </button>
            </div>
        </section>

        <p
            v-if="store.loading"
            class="status-message"
        >
            불러오는 중입니다.
        </p>

        <p
            v-if="store.errorMessage"
            class="status-message error"
        >
            {{ store.errorMessage }}
        </p>

        <section class="table-card">
            <header class="section-title">
                <h2>탐지 로그 목록</h2>
                <span>총 {{ store.pageInfo.totalElements || store.plateDetectionLogs.length }}건</span>
            </header>

            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>탐지 시간</th>
                            <th>세차장</th>
                            <th>장비</th>
                            <th>탐지 번호판</th>
                            <th>탐지 결과</th>
                            <th>리뷰 필요</th>
                        </tr>
                    </thead>

                    <tbody>
                        <tr
                            v-for="log in store.plateDetectionLogs"
                            :key="log.plateDetectionLogId"
                            class="detail-row"
                            tabindex="0"
                            role="button"
                            :aria-label="`번호판 탐지 로그 ${log.plateDetectionLogId} 상세보기`"
                            @click="openDetail(log.plateDetectionLogId)"
                            @keydown.enter="openDetail(log.plateDetectionLogId)"
                            @keydown.space.prevent="openDetail(log.plateDetectionLogId)"
                        >
                            <td>{{ log.plateDetectionLogId }}</td>
                            <td>{{ formatDateTime(log.detectedTime) }}</td>
                            <td>{{ displayValue(log.siteName) }}</td>
                            <td>{{ displayValue(log.deviceName) }}</td>
                            <td class="plate-cell">{{ displayValue(log.detectedPlateNumber) }}</td>
                            <td>
                                <span :class="['badge', log.success ? 'success' : 'failed']">
                                    {{ log.success ? "성공" : "실패" }}
                                </span>
                            </td>
                            <td>
                                <span :class="['badge', log.needReview ? 'warning' : 'muted']">
                                    {{ log.needReview ? "필요" : "완료" }}
                                </span>
                            </td>
                        </tr>

                        <tr v-if="!store.loading && store.plateDetectionLogs.length === 0">
                            <td
                                class="empty-cell"
                                colspan="7"
                            >
                                조회된 번호판 탐지 로그가 없습니다.
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <div class="pagination">
                <button
                    type="button"
                    :disabled="!store.pageInfo.hasPrevious"
                    @click="movePage(store.pageInfo.page - 1)"
                >
                    이전
                </button>

                <span>
                    {{ store.pageInfo.page }} / {{ store.pageInfo.totalPages || 1 }}
                </span>

                <button
                    type="button"
                    :disabled="!store.pageInfo.hasNext"
                    @click="movePage(store.pageInfo.page + 1)"
                >
                    다음
                </button>

                <div class="page-jump">
                    <input
                        v-model="pageInput"
                        type="number"
                        min="1"
                        :max="store.pageInfo.totalPages || 1"
                        placeholder="페이지"
                        aria-label="이동할 페이지 번호"
                        @keyup.enter="goToPage"
                    >
                    <button
                        type="button"
                        :disabled="store.pageInfo.totalPages < 1"
                        @click="goToPage"
                    >
                        이동
                    </button>
                </div>

                <p v-if="pageInputError" class="page-input-error">
                    {{ pageInputError }}
                </p>
            </div>
        </section>

        <section
            v-if="store.logDetail"
            ref="detailCard"
            class="detail-card"
        >
            <header class="section-title">
                <h2>번호판 탐지 결과</h2>
                <div class="detail-actions">
                    <button
                        v-if="!isReviewed"
                        class="primary-button"
                        type="button"
                        @click="openReview('edit')"
                    >
                        리뷰
                    </button>

                    <template v-else>
                        <button
                            class="ghost-button"
                            type="button"
                            @click="openReview('view')"
                        >
                            확인
                        </button>

                        <button
                            class="primary-button"
                            type="button"
                            @click="openReview('edit')"
                        >
                            수정하기
                        </button>
                    </template>
                    <button class="ghost-button" type="button" @click="closeDetail">
                        닫기
                    </button>
                </div>
            </header>

            <div class="detail-grid">
                <section class="preview-panel">
                    <h3>촬영 이미지 비교</h3>

                    <PlateImageComparison
                        :image-url="store.logImageUrl"
                        :x1="store.logDetail.yoloX1"
                        :y1="store.logDetail.yoloY1"
                        :x2="store.logDetail.yoloX2"
                        :y2="store.logDetail.yoloY2"
                    />

                    <p class="file-path">
                        파일 경로: {{ displayValue(store.logDetail.filePath) }}
                    </p>
                </section>

                <section class="info-panel">
                    <h3>인식 결과</h3>

                    <dl>
                        <dt>번호판 번호</dt>
                        <dd>{{ displayValue(store.logDetail.detectedPlateNumber) }}</dd>

                        <dt>탐지 결과</dt>
                        <dd>{{ store.logDetail.success ? "성공" : "실패" }}</dd>

                        <dt>차량 매칭</dt>
                        <dd>{{ store.logDetail.washHistoryId ? "세차 기록 연결됨" : "미연결" }}</dd>

                        <dt>YOLO 신뢰도</dt>
                        <dd>{{ formatPercent(store.logDetail.yoloConfidence) }}</dd>

                        <dt>OCR 신뢰도</dt>
                        <dd>{{ formatPercent(store.logDetail.ocrConfidence) }}</dd>

                        <dt>촬영 시간</dt>
                        <dd>{{ formatDateTime(store.logDetail.detectedTime) }}</dd>

                        <dt>장비</dt>
                        <dd>{{ displayValue(store.logDetail.deviceName) }}</dd>

                        <dt>에러 로그</dt>
                        <dd>{{ displayValue(store.logDetail.errorLog) }}</dd>
                    </dl>
                </section>
            </div>

        </section>

        <PlateReviewModal
            :open="isReviewModalOpen"
            :mode="reviewModalMode"
            :log="selectedReviewLog"
            :image-url="store.logImageUrl"
            :review-form="reviewForm"
            :review-status-options="store.reviewStatusOptions"
            :failure-reason-options="store.failureReasonOptions"
            :loading="store.loading"
            @close="closeReviewModal"
            @save="saveReview"
            @update="updateReviewForm"
        />
    </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import PlateReviewModal from "../components/PlateReviewModal.vue"
import { usePlateDetectionLogStore } from "../stores/plateDetectionLogStore"
import PlateImageComparison from "../components/PlateImageComparison.vue"

const store = usePlateDetectionLogStore()
const route = useRoute()
const router = useRouter()
const detailCard = ref(null)
const selectedFilter = ref("ALL")
const startDate = ref("")
const endDate = ref("")
const plateNumber = ref("")
const pageInput = ref("")
const pageInputError = ref("")
const isReviewModalOpen = ref(false)
const selectedReviewLog = ref(null)
const reviewModalMode = ref("edit")

const isReviewed = computed(() => {
    const status = store.logDetail?.reviewStatus

    return Boolean(
        status &&
        status !== "UNREVIEWED"
    )
})

const reviewForm = reactive({
    reviewStatus: "",
    correctedPlateNumber: "",
    failureReason: "",
    trainingUseYn: false,
    reviewMemo: "",

    bboxReviewStatus: "",
    reviewedBox: null
})

onMounted(async () => {
    clearDetailState()
    await store.fetchLogs()
    const requestedLogId = Number(route.query.plateDetectionLogId)
    if (Number.isInteger(requestedLogId) && requestedLogId > 0) {
        await openDetail(requestedLogId, false)
    }
})

onBeforeUnmount(clearDetailState)

async function searchLogs() {
    clearDetailState()
    pageInput.value = ""
    pageInputError.value = ""
    await store.fetchLogs(buildSearchParams(1))
}

async function movePage(page) {
    clearDetailState()
    return await store.fetchLogs(buildSearchParams(page))
}

async function goToPage() {
    const page = Number(pageInput.value)
    const maxPage = Number(store.pageInfo.totalPages)

    if (maxPage < 1) {
        pageInputError.value = "이동할 페이지가 없습니다."
        return
    }

    if (!Number.isInteger(page) || page < 1 || page > maxPage) {
        pageInputError.value = `1부터 ${maxPage} 사이의 페이지를 입력해 주세요.`
        return
    }

    const success = await movePage(page)

    if (success) {
        pageInput.value = ""
        pageInputError.value = ""
    }
}

function buildSearchParams(page) {
    return {
        page,
        startDate: startDate.value || undefined,
        endDate: endDate.value || undefined,
        plateNumber: plateNumber.value || undefined,
        ...store.buildFilterParams(selectedFilter.value)
    }
}

async function openDetail(plateDetectionLogId, updateRoute = true) {
    const success = await store.fetchLogDetail(plateDetectionLogId)

    if (success) {
        await store.fetchLogImage(plateDetectionLogId)
        if (updateRoute) {
            await router.replace({ query: { ...route.query, plateDetectionLogId } })
        }
        await nextTick()
        detailCard.value?.scrollIntoView({ behavior: "smooth", block: "start" })
    }
}

function openReview(mode = "edit") {
    if (!store.logDetail) {
        return
    }

    reviewModalMode.value = mode
    selectedReviewLog.value = store.logDetail
    fillReviewForm()
    isReviewModalOpen.value = true
}

async function saveReview() {
    if (!store.logDetail) {
        return
    }

    const success = await store.saveReview(
        store.logDetail.plateDetectionLogId,
        {
            reviewStatus: reviewForm.reviewStatus,
            correctedPlateNumber: reviewForm.correctedPlateNumber,
            failureReason: reviewForm.failureReason,
            trainingUseYn: reviewForm.trainingUseYn,
            reviewMemo: reviewForm.reviewMemo,
            bboxReviewStatus: reviewForm.bboxReviewStatus,
            reviewedBox: reviewForm.reviewedBox
                ? { ...reviewForm.reviewedBox }
                : null
        }
    )

    if (success) {
        await store.fetchLogs(buildSearchParams(store.pageInfo.page))
        closeReviewModal()
    }
}

function updateReviewForm({ field, value }) {
    reviewForm[field] = value
}

function fillReviewForm() {
    const detail = store.logDetail

    const savedBox = makeBox(
        detail.reviewedX1,
        detail.reviewedY1,
        detail.reviewedX2,
        detail.reviewedY2
    )

    const yoloBox = makeBox(
        detail.yoloX1,
        detail.yoloY1,
        detail.yoloX2,
        detail.yoloY2
    )

    reviewForm.bboxReviewStatus =
        detail.bboxReviewStatus ||
        (yoloBox ? "ACCEPTED" : "NO_PLATE")

    reviewForm.reviewedBox =
        reviewForm.bboxReviewStatus === "NO_PLATE"
            ? null
            : savedBox || yoloBox

    reviewForm.reviewStatus =
        detail.reviewStatus === "UNREVIEWED"
            ? ""
            : detail.reviewStatus || ""
    reviewForm.correctedPlateNumber =
        detail.correctedPlateNumber || detail.detectedPlateNumber || ""
    reviewForm.failureReason = detail.failureReason || ""
    reviewForm.trainingUseYn = Boolean(detail.trainingUseYn)
    reviewForm.reviewMemo = detail.reviewMemo || ""
}

function closeDetail() {
    clearDetailState()
    const query = { ...route.query }
    delete query.plateDetectionLogId
    router.replace({ query })
}

function clearDetailState() {
    isReviewModalOpen.value = false
    selectedReviewLog.value = null
    store.clearLogDetail()
    resetReviewForm()
}

function closeReviewModal() {
    isReviewModalOpen.value = false
    selectedReviewLog.value = null
    resetReviewForm()
}

function resetReviewForm() {
    reviewForm.reviewStatus = ""
    reviewForm.correctedPlateNumber = ""
    reviewForm.failureReason = ""
    reviewForm.trainingUseYn = false
    reviewForm.reviewMemo = ""
}

function displayValue(value) {
    return value || "-"
}

function formatDateTime(value) {
    if (!value) {
        return "-"
    }

    return String(value).replace("T", " ").slice(0, 19)
}

function formatPercent(value) {
    if (value === null || value === undefined) {
        return "-"
    }

    return `${(Number(value) * 100).toFixed(1)}%`
}
function makeBox(x1, y1, x2, y2) {
    const values = [x1, y1, x2, y2]

    if (!values.every(Number.isFinite)) {
        return null
    }

    if (x2 <= x1 || y2 <= y1) {
        return null
    }

    return { x1, y1, x2, y2 }
}
</script>

<style scoped>
.plate-log-page {
    display: grid;
    gap: 20px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.page-hero,
.filter-card,
.training-export-card,
.table-card,
.detail-card {
    border: 1px solid #dce5f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 12px 32px rgba(7, 27, 58, 0.06);
}

.page-hero {
    padding: 30px 34px;
    text-align: center;
}

.eyebrow {
    margin: 0 0 8px;
    color: #1c3d70;
    font-size: 13px;
    font-weight: 900;
    letter-spacing: 0;
}

.page-hero h1 {
    margin: 0;
    color: #071b3a;
    font-size: 34px;
    font-weight: 900;
    letter-spacing: 0;
}

.page-hero p {
    margin: 10px 0 0;
    color: #52637d;
    font-size: 15px;
}

.filter-card {
    display: flex;
    flex-wrap: wrap;
    align-items: end;
    justify-content: center;
    gap: 10px;
    padding: 18px 22px;
}
.training-export-card {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    padding: 20px 24px;
}

.training-export-info h2 {
    margin: 0;
    color: #071b3a;
    font-size: 20px;
    font-weight: 900;
}

.training-export-info p {
    margin: 7px 0 0;
    color: #52637d;
    font-size: 14px;
    line-height: 1.5;
}

.training-export-actions {
    display: flex;
    align-items: end;
    gap: 10px;
    flex-shrink: 0;
}

.training-export-actions .primary-button {
    min-width: 190px;
    white-space: nowrap;
}

.training-export-actions .primary-button:disabled {
    background: #94a3b8;
    box-shadow: none;
    cursor: not-allowed;
    transform: none;
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
    padding: 0 12px;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    color: #071b3a;
    background: #ffffff;
    font: inherit;
    box-sizing: border-box;
    outline: none;
}

textarea {
    min-height: 96px;
    padding: 12px;
    resize: vertical;
}

select:focus,
input:focus,
textarea:focus {
    border-color: #1c3d70;
    box-shadow: 0 0 0 3px rgba(28, 61, 112, 0.12);
}

.primary-button,
.ghost-button,
.pagination button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-radius: 8px;
    font: inherit;
    font-size: 14px;
    font-weight: 900;
    cursor: pointer;
    text-decoration: none;
    transition: transform 0.18s ease, background 0.18s ease;
}

.primary-button,
.pagination button {
    color: #ffffff;
    background: #071b3a;
    box-shadow: 0 8px 16px rgba(7, 27, 58, 0.14);
}

.primary-button:hover,
.pagination button:hover:not(:disabled) {
    background: #0f2f5f;
    transform: translateY(-1px);
}

.ghost-button {
    color: #071b3a;
    background: #ffffff;
    border-color: #cbd6e6;
}

.ghost-button:hover {
    background: #f5f8fc;
}

tbody tr.detail-row {
    cursor: pointer;
    transition: background-color 120ms ease, box-shadow 120ms ease;
}

tbody tr.detail-row:hover,
tbody tr.detail-row:focus-visible {
    background: #eaf2ff;
    outline: none;
    box-shadow: inset 3px 0 0 #2563eb;
}

tbody tr.detail-row:focus-visible {
    box-shadow: inset 3px 0 0 #0b1f3a, inset 0 0 0 1px #0b1f3a;
}

.plate-search-field input {
    min-width: 190px;
}

.status-message {
    margin: 0;
    padding: 14px 16px;
    border-radius: 8px;
    color: #1c3d70;
    background: #e8eef8;
    font-weight: 800;
    text-align: center;
}

.status-message.error {
    color: #b42318;
    background: #fff1f1;
}

.table-card,
.detail-card {
    overflow: hidden;
}

.section-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 20px 24px;
    border-bottom: 1px solid #e3eaf4;
}

.section-title h2 {
    margin: 0;
    color: #071b3a;
    font-size: 22px;
    font-weight: 900;
}

.section-title span {
    color: #52637d;
    font-size: 14px;
    font-weight: 800;
}

.detail-actions {
    display: flex;
    gap: 10px;
}

.table-wrap {
    overflow-x: auto;
}

table {
    width: 100%;
    min-width: 960px;
    border-collapse: collapse;
}

th,
td {
    padding: 14px 12px;
    border-bottom: 1px solid #d8e2ef;
    color: #334766;
    font-size: 14px;
    text-align: left;
    white-space: nowrap;
}

th {
    color: #071b3a;
    background: #f5f8fc;
    font-weight: 900;
}

.plate-cell {
    color: #071b3a;
    font-weight: 900;
}

.badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 26px;
    padding: 0 10px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 900;
}

.badge.success {
    color: #1c6b4f;
    background: #e7f7ef;
}

.badge.failed {
    color: #b42318;
    background: #fff1f1;
}

.badge.warning {
    color: #925400;
    background: #fff7df;
}

.badge.muted {
    color: #52637d;
    background: #edf2f8;
}

.empty-cell {
    padding: 34px 12px;
    color: #52637d;
    font-weight: 800;
    text-align: center;
}

.pagination {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 16px 20px 20px;
}

.pagination span {
    min-width: 72px;
    color: #071b3a;
    font-weight: 900;
    text-align: center;
}

.pagination button:disabled {
    color: #94a3b8;
    background: #edf2f8;
    box-shadow: none;
    cursor: not-allowed;
}

.detail-card {
    display: grid;
}

.detail-grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(320px, 0.8fr);
    gap: 18px;
    padding: 24px;
}

.preview-panel,
.info-panel {
    border: 1px solid #e3eaf4;
    border-radius: 8px;
    background: #f8fafc;
    padding: 18px;
}

.preview-panel h3,
.info-panel h3 {
    margin: 0 0 14px;
    color: #071b3a;
    font-size: 18px;
    font-weight: 900;
}

.preview-panel img {
    display: block;
    width: 100%;
    max-height: 360px;
    object-fit: contain;
    border-radius: 8px;
    background: #ffffff;
    border: 1px solid #dce5f1;
}

.empty-preview {
    display: grid;
    place-items: center;
    min-height: 180px;
    margin: 0;
    border: 1px dashed #cbd6e6;
    border-radius: 8px;
    color: #52637d;
    background: #ffffff;
    font-weight: 800;
}

.file-path {
    margin: 12px 0 0;
    color: #52637d;
    font-size: 13px;
    word-break: break-all;
}

dl {
    display: grid;
    grid-template-columns: 120px 1fr;
    gap: 10px 14px;
    margin: 0;
}

dt,
dd {
    margin: 0;
}

dt {
    color: #52637d;
    font-weight: 800;
}

dd {
    color: #071b3a;
    font-weight: 900;
    word-break: break-word;
}

@media (max-width: 900px) {
    .filter-card,
    .section-title,
    .training-export-card {
        align-items: stretch;
        flex-direction: column;
    }

    .training-export-actions {
        align-items: stretch;
    }

    .training-export-actions .primary-button {
        width: 100%;
    }

    .detail-grid {
        grid-template-columns: 1fr;
    }
}

.page-jump {
    display: flex;
    gap: 6px;
    margin-left: 10px;
}

.page-jump input {
    width: 86px;
    min-height: 40px;
    text-align: center;
}

.page-input-error {
    flex-basis: 100%;
    margin: 2px 0 0;
    color: #b42318;
    font-size: 13px;
    font-weight: 800;
    text-align: center;
}
</style>
