<template>
    <main class="inquiry-page">
        <section class="inquiry-header">
            <p class="eyebrow"></p>
            <h1>문의사항</h1>
            <p class="description">
                세차장 이용 중 궁금한 점이나 요청사항을 확인할 수 있습니다.
            </p>

            <RouterLink
                v-if="actualCreateRouteName"
                class="write-button"
                :to="{ name: actualCreateRouteName }"
            >
                문의 작성
            </RouterLink>
        </section>

        <p
            v-if="inquiryStore.loading"
            class="state-text"
        >
            문의사항을 불러오는 중입니다.
        </p>

        <p
            v-if="inquiryStore.error"
            class="error-text"
        >
            {{ inquiryStore.error }}
        </p>

        <section class="inquiry-card">
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>번호</th>
                            <th>유형</th>
                            <th>제목</th>
                            <th>작성자</th>
                            <th>상태</th>
                            <th>작성일</th>
                        </tr>
                    </thead>

                    <tbody>
                        <tr
                            v-for="inquiry in inquiryStore.inquiries"
                            :key="inquiry.inquiryId"
                            class="detail-row"
                            tabindex="0"
                            role="button"
                            :aria-label="`${inquiry.title || '문의'} 상세보기`"
                            @click="openDetail(inquiry.inquiryId)"
                            @keydown.enter="openDetail(inquiry.inquiryId)"
                            @keydown.space.prevent="openDetail(inquiry.inquiryId)"
                        >
                            <td>{{ inquiry.inquiryId }}</td>
                            <td>{{ displayInquiryType(inquiry.inquiryType) }}</td>
                            <td>
                                <RouterLink
                                    class="detail-link"
                                    :to="detailRoute(inquiry.inquiryId)"
                                    @click.stop
                                >
                                    {{ inquiry.title || "제목 없음" }}
                                </RouterLink>
                            </td>
                            <td>{{ inquiry.writerName || "-" }}</td>
                            <td>
                                <span class="status-badge">
                                    {{ displayStatus(inquiry.status) }}
                                </span>
                            </td>
                            <td>{{ formatDate(inquiry.createdAt) }}</td>
                        </tr>

                        <tr v-if="!inquiryStore.loading && inquiryStore.inquiries.length === 0">
                            <td
                                class="empty-cell"
                                colspan="6"
                            >
                                등록된 문의사항이 없습니다.
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</template>

<script setup>
import { computed, onMounted } from "vue"
import { useRouter } from "vue-router"
import { getInquiryRouteNames } from "@/shared/auth/authRole"
import { useInquiryStore } from "../stores/inquiryStore"

const inquiryStore = useInquiryStore()
const router = useRouter()

const routeNames = computed(() => getInquiryRouteNames())
const actualCreateRouteName = computed(() => routeNames.value.createRouteName)

onMounted(() => {
    inquiryStore.fetchInquiryList()
})

function detailRoute(inquiryId) {
    return {
        name: routeNames.value.detailRouteName,
        params: {
            inquiryId
        }
    }
}

function openDetail(inquiryId) {
    router.push(detailRoute(inquiryId))
}

function displayStatus(value) {
    const labels = {
        WAITING: "대기",
        ANSWERED: "답변완료",
        CLOSED: "종료"
    }

    return labels[value] || value || "-"
}

function displayInquiryType(value) {
    const labels = {
        REFUND: "환불",
        WASH_SITE: "세차장",
        SERVICE: "기능 이용",
        ETC: "기타"
    }

    return labels[value] || "세차장"
}

function formatDate(value) {
    if (!value) {
        return "-"
    }

    return String(value).replace("T", " ").slice(0, 16)
}
</script>

<style scoped>
.inquiry-page {
    min-height: calc(100vh - 110px);
    padding: 56px 20px;
    background: #f7f9fc;
    color: #071b3a;
    font-family: 'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.inquiry-header {
    max-width: 980px;
    margin: 0 auto 28px;
    text-align: center;
}

.eyebrow {
    margin: 0 0 12px;
    color: #64748b;
    font-size: 13px;
    font-weight: 900;
    letter-spacing: 0;
}

h1 {
    margin: 0;
    color: #071b3a;
    font-size: 40px;
    font-weight: 900;
}

.description {
    margin: 14px 0 24px;
    color: #52637d;
    font-size: 16px;
    line-height: 1.6;
}

.write-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 130px;
    height: 42px;
    border-radius: 8px;
    background: #071b3a;
    color: #ffffff;
    font-size: 15px;
    font-weight: 900;
    text-decoration: none;
    box-shadow: 0 8px 18px rgba(7, 27, 58, 0.18);
}

.header-detail-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 104px;
    height: 34px;
    margin: 0 auto 10px;
    border: 0;
    border-radius: 6px;
    background: #071b3a;
    color: #ffffff;
    font-size: 14px;
    font-weight: 900;
    cursor: pointer;
}

.header-detail-button:hover:not(:disabled) {
    background: #163b68;
}

.header-detail-button:disabled {
    cursor: default;
    opacity: 0.45;
}

.state-text,
.error-text {
    max-width: 980px;
    margin: 0 auto 14px;
    padding: 14px 16px;
    border-radius: 8px;
    background: #ffffff;
    color: #52637d;
    font-size: 14px;
}

.error-text {
    background: #fff1f2;
    color: #be123c;
}

.inquiry-card {
    max-width: 980px;
    margin: 0 auto;
    overflow: hidden;
    border: 1px solid #e4e9f1;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 24px 60px rgba(7, 27, 58, 0.08);
}

.table-wrap {
    overflow-x: auto;
}

table {
    width: 100%;
    min-width: 780px;
    border-collapse: collapse;
}

th,
td {
    padding: 15px 18px;
    border-bottom: 1px solid #edf1f7;
    text-align: left;
    white-space: nowrap;
}

th {
    background: #071b3a;
    color: #ffffff;
    font-size: 14px;
    font-weight: 900;
}

td {
    color: #26364a;
    font-size: 14px;
}

tbody tr.detail-row {
    cursor: pointer;
    transition: background-color 120ms ease, box-shadow 120ms ease;
}

tbody tr.detail-row:hover,
tbody tr.detail-row:focus-visible {
    background: #f8fbfc;
    outline: none;
    box-shadow: inset 3px 0 0 #2563eb;
}

tbody tr.detail-row:focus-visible {
    box-shadow: inset 3px 0 0 #0b1f3a, inset 0 0 0 1px #0b1f3a;
}

tbody tr:last-child td {
    border-bottom: 0;
}

.detail-link {
    color: #071b3a;
    font-weight: 900;
    text-decoration: none;
}

.detail-link:hover {
    text-decoration: underline;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 72px;
    min-height: 26px;
    padding: 3px 10px;
    border-radius: 999px;
    background: #eef4fb;
    color: #071b3a;
    font-size: 12px;
    font-weight: 900;
}

.empty-cell {
    padding: 48px 20px;
    color: #52637d;
    text-align: center;
}

@media (max-width: 760px) {
    .inquiry-page {
        padding: 36px 16px;
    }

    h1 {
        font-size: 34px;
    }
}
</style>
