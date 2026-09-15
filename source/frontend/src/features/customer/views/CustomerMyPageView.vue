<template>

    <div class="mypage">

        <h1>
            {{ isEditing ? "회원정보 수정" : "마이페이지" }}
        </h1>


        <p v-if="loading">
            불러오는 중...
        </p>


        <p
            v-else-if="loadError"
            class="error-message"
        >
            {{ loadError }}
        </p>


        <!-- 1. 처음 마이페이지를 클릭했을 때: 조회 화면 -->
        <div
            v-else-if="myPage && !isEditing"
            class="mypage-card"
        >

            <div class="info-row">
                <strong>로그인 아이디</strong>
                <span>{{ myPage.loginId }}</span>
            </div>

            <div class="info-row password-row">
                <strong>비밀번호</strong>

                <span class="password-action-cell">
                    <RouterLink
                        v-if="canEditProfile"
                        :to="{ name: 'CustomerPasswordChange' }"
                        class="password-change-link"
                    >
                        비밀번호 변경
                    </RouterLink>
                </span>
            </div>

            <div class="info-row">
                <strong>이름</strong>
                <span>{{ myPage.name }}</span>
            </div>

            <div class="info-row">
                <strong>생년월일</strong>
                <span>{{ myPage.birthDate }}</span>
            </div>

            <div class="info-row">
                <strong>휴대전화번호</strong>
                <span>{{ formatPhoneNumber(myPage.phone) }}</span>
            </div>

            <div class="info-row">
                <strong>이메일</strong>
                <span>{{ myPage.emailAddress }}</span>
            </div>

            <div class="info-row">
                <strong>성별</strong>
                <span>{{ genderText }}</span>
            </div>

            <div class="info-row">
                <strong>고객 유형</strong>
                <span>{{ customerTypeText }}</span>
            </div>

            <div class="info-row">
                <strong>상태</strong>
                <span>{{ statusText }}</span>
            </div>

            <div class="info-row">
                <strong>LINE 알림</strong>
                <LineNotificationLink return-route="/customer/mypage" />
            </div>

            <div class="info-row wallet-row">
                <strong>선불 잔액</strong>
                <span>{{ formatAmount(walletBalance) }}</span>
            </div>

            <template v-if="hasBenefits">
                <div class="info-row">
                    <strong>세차장별 마일리지 합계</strong>
                    <span>{{ formatNumber(totalMileageBalance) }}점</span>
                </div>

                <div class="info-row">
                    <strong>사용 가능 쿠폰</strong>
                    <span class="benefit-value">
                        {{ formatNumber(totalAvailableCouponCount) }}개
                        <RouterLink
                            class="benefit-link"
                            :to="{ name: 'customer-benefits' }"
                        >
                            혜택 확인
                        </RouterLink>
                    </span>
                </div>
            </template>

            <button
                v-if="canEditProfile"
                type="button"
                @click="startEditing"
            >
                회원정보 수정
            </button>

            <button
                class="withdraw-button"
                type="button"
                @click="openWithdrawModal"
            >
                회원탈퇴
            </button>

        </div>


        <!-- 2. 회원정보 수정을 클릭했을 때: 수정 화면 -->
        <form
            v-else-if="myPage && isEditing"
            class="mypage-card"
            @submit.prevent="saveMyPage"
        >

            <div class="info-row">
                <strong>로그인 아이디</strong>
                <span>{{ myPage.loginId }}</span>
            </div>


            <label class="info-row">
                <strong>이름</strong>
                <input
                    v-model.trim="editForm.name"
                    required
                >
            </label>


            <label class="info-row">
                <strong>생년월일</strong>
                <input
                    v-model="editForm.birthDate"
                    type="date"
                    required
                >
            </label>


            <div class="info-row">
                <strong>휴대전화번호</strong>

                <div class="phone-number-inputs">
                    <input
                        :value="phoneParts.first"
                        required
                        inputmode="numeric"
                        pattern="[0-9]*"
                        maxlength="3"
                        aria-label="휴대전화번호 앞자리"
                        @input="handlePhonePartInput('first', 3, $event, phoneMiddleInput)"
                    >
                    <span aria-hidden="true">-</span>
                    <input
                        ref="phoneMiddleInput"
                        :value="phoneParts.middle"
                        required
                        inputmode="numeric"
                        pattern="[0-9]*"
                        maxlength="4"
                        aria-label="휴대전화번호 가운데자리"
                        @input="handlePhonePartInput('middle', 4, $event, phoneLastInput)"
                    >
                    <span aria-hidden="true">-</span>
                    <input
                        ref="phoneLastInput"
                        :value="phoneParts.last"
                        required
                        inputmode="numeric"
                        pattern="[0-9]*"
                        maxlength="4"
                        aria-label="휴대전화번호 뒷자리"
                        @input="handlePhonePartInput('last', 4, $event)"
                    >
                </div>
            </div>


            <div class="info-row email-edit-row">
                <strong>이메일</strong>

                <div class="email-verification-fields">
                    <div class="email-input-actions">
                        <input
                            v-model.trim="editForm.emailAddress"
                            type="email"
                            required
                            :disabled="emailChangeSending || emailChangeVerifying"
                            @input="handleEmailAddressInput"
                        >

                        <button
                            type="button"
                            class="email-verification-button"
                            :disabled="emailChangeSending || emailChangeVerifying"
                            @click="startEmailChange"
                        >
                            {{ emailChangeSending
                                ? "발송 중..."
                                : emailChangeChallengeId
                                    ? "인증번호 재발송"
                                    : "이메일 인증"
                            }}
                        </button>
                    </div>

                    <div
                        v-if="emailChangeChallengeId"
                        class="email-input-actions"
                    >
                        <input
                            v-model.trim="emailChangeCode"
                            inputmode="numeric"
                            pattern="[0-9]{6}"
                            maxlength="6"
                            autocomplete="one-time-code"
                            placeholder="6자리 인증번호"
                            aria-label="이메일 인증번호"
                            :disabled="emailChangeVerifying"
                            @input="handleEmailCodeInput"
                        >

                        <button
                            type="button"
                            class="email-verification-button"
                            :disabled="emailChangeVerifying || emailChangeCode.length !== 6"
                            @click="verifyEmailChange"
                        >
                            {{ emailChangeVerifying
                                ? "확인 중..."
                                : "인증번호 확인"
                            }}
                        </button>
                    </div>

                    <p class="email-help-message">
                        인증이 완료되면 회원정보 이메일과 비밀번호 복구 이메일이 함께 변경됩니다.
                    </p>

                    <p
                        v-if="emailChangeMessage"
                        class="email-success-message"
                    >
                        {{ emailChangeMessage }}
                    </p>

                    <p
                        v-if="emailChangeError"
                        class="email-error-message"
                    >
                        {{ emailChangeError }}
                    </p>
                </div>
            </div>


            <div class="info-row">

                <strong>성별</strong>

                <div class="gender-options">

                    <label>
                        <input
                            v-model="editForm.gender"
                            type="radio"
                            value="M"
                        >
                        남자
                    </label>

                    <label>
                        <input
                            v-model="editForm.gender"
                            type="radio"
                            value="F"
                        >
                        여자
                    </label>

                    <label>
                        <input
                            v-model="editForm.gender"
                            type="radio"
                            value="N"
                        >
                        선택 안 함
                    </label>

                </div>

            </div>


            <div class="info-row">
                <strong>고객 유형</strong>
                <span>{{ customerTypeText }}</span>
            </div>

            <div class="info-row">
                <strong>상태</strong>
                <span>{{ statusText }}</span>
            </div>


            <p
                v-if="saveError"
                class="error-message"
            >
                {{ saveError }}
            </p>


            <div class="button-row">

                <button
                    type="submit"
                    :disabled="saving || emailChangeSending || emailChangeVerifying"
                >
                    {{ saving ? "수정 중..." : "수정완료" }}
                </button>

                <button
                    type="button"
                    :disabled="saving || emailChangeSending || emailChangeVerifying"
                    @click="cancelEditing"
                >
                    취소
                </button>

            </div>

        </form>
        <div
            v-if="isWithdrawModalOpen"
            class="withdraw-modal-backdrop"
            @click.self="closeWithdrawModal"
        >
            <form
                class="withdraw-modal"
                @submit.prevent="confirmWithdraw"
            >
                <h2>회원탈퇴</h2>

                <p>
                    회원탈퇴 후에는 서비스를 이용할 수 없습니다.
                    본인 확인을 위해 현재 비밀번호를 입력해주세요.
                </p>

                <label>
                    <span>현재 비밀번호</span>

                    <input
                        v-model="withdrawPassword"
                        type="password"
                        autocomplete="current-password"
                        required
                        :disabled="withdrawing"
                        placeholder="현재 비밀번호 입력"
                    >
                </label>

                <p
                    v-if="withdrawError"
                    class="error-message"
                >
                    {{ withdrawError }}
                </p>

                <div class="withdraw-modal-actions">
                    <button
                        class="withdraw-confirm-button"
                        type="submit"
                        :disabled="withdrawing"
                    >
                        {{
                            withdrawing
                                ? "탈퇴 처리 중..."
                                : "탈퇴하기"
                        }}
                    </button>

                    <button
                        type="button"
                        :disabled="withdrawing"
                        @click="closeWithdrawModal"
                    >
                        취소
                    </button>
                </div>
            </form>
        </div>
    </div>

</template>


<script setup>

import {
    computed,
    onMounted,
    reactive,
    ref
} from "vue"
import { formatPhoneNumber } from "@/shared/utils/phoneNumber"

import * as customerApi
    from "../api/customerApi"

import { usePaymentStore }
    from "@/features/payment/stores/paymentStore"

import * as benefitApi
    from "@/features/benefit/api/benefitApi"

import { useAuthStore } 
    from "../../auth/stores/authStore"

import LineNotificationLink
    from "@/features/auth/components/LineNotificationLink.vue"

const authStore = useAuthStore()

const myPage = ref(null)

const paymentStore = usePaymentStore()

const benefitSummary = ref(null)

const loading = ref(false)

const saving = ref(false)

const isEditing = ref(false)

const loadError = ref("")

const saveError = ref("")

const emailChangeChallengeId = ref("")

const emailChangeTarget = ref("")

const emailChangeCode = ref("")

const emailChangeSending = ref(false)

const emailChangeVerifying = ref(false)

const emailChangeMessage = ref("")

const emailChangeError = ref("")

const phoneMiddleInput = ref(null)

const phoneLastInput = ref(null)

const phoneParts = reactive({

    first: "",

    middle: "",

    last: ""

})


const editForm = reactive({

    name: "",

    birthDate: "",

    phone: "",

    gender: "",

    emailAddress: ""

})


const customerTypeText = computed(() => {

    if (myPage.value?.customerType === "MEMBER") {
        return "회원"
    }

    return myPage.value?.customerType || ""
})


const statusText = computed(() => {

    if (myPage.value?.status === "ACTIVE") {
        return "활성"
    }

    return myPage.value?.status || ""
})


const genderText = computed(() => {

    if (myPage.value?.gender === "M") {
        return "남자"
    }

    if (myPage.value?.gender === "F") {
        return "여자"
    }

    return "선택 안 함"
})


const walletBalance = computed(() =>
    paymentStore.wallet?.balance || 0
)

const hasBenefits = computed(() =>
    Number(benefitSummary.value?.activeProgramCount || 0) > 0
    || Number(benefitSummary.value?.totalMileageBalance || 0) > 0
    || Number(benefitSummary.value?.totalAvailableCouponCount || 0) > 0
)

const totalMileageBalance = computed(() =>
    Number(benefitSummary.value?.totalMileageBalance || 0)
)

const totalAvailableCouponCount = computed(() =>
    Number(benefitSummary.value?.totalAvailableCouponCount || 0)
)

const canEditProfile = computed(() =>
    authStore.canUseFeature("profile-edit")
)

const isEmailChanged = computed(() =>
    normalizeEmailAddress(editForm.emailAddress)
    !== normalizeEmailAddress(myPage.value?.emailAddress)
)
const isWithdrawModalOpen = ref(false)
const withdrawPassword = ref("")
const withdrawError = ref("")
const withdrawing = ref(false)

async function loadBenefitSummary() {
    try {
        benefitSummary.value = await benefitApi.getMyBenefitSummary()
    } catch (error) {
        benefitSummary.value = null
    }
}

function formatNumber(value) {
    return Number(value || 0).toLocaleString("ko-KR")
}

function setEditForm(data) {

    editForm.name =
        data.name || ""

    editForm.birthDate =
        data.birthDate || ""

    setPhoneParts(data.phone)

    editForm.gender =
        data.gender || "N"

    editForm.emailAddress =
        data.emailAddress || ""
}

function normalizeEmailAddress(value) {

    return String(value || "")
        .trim()
        .toLowerCase()
}

function resetEmailChangeState() {

    emailChangeChallengeId.value = ""

    emailChangeTarget.value = ""

    emailChangeCode.value = ""

    emailChangeMessage.value = ""

    emailChangeError.value = ""
}


function digitsOnly(value) {

    return String(value || "")
        .replace(/\D/g, "")
}


function syncPhoneValue() {

    editForm.phone =
        phoneParts.first
        + phoneParts.middle
        + phoneParts.last
}


function setPhoneParts(value) {

    const digits =
        digitsOnly(value).slice(0, 11)

    phoneParts.first =
        digits.slice(0, 3)

    phoneParts.middle =
        digits.slice(3, 7)

    phoneParts.last =
        digits.slice(7, 11)

    syncPhoneValue()
}


function startEditing() {

    if (!canEditProfile.value) {
        return
    }

    setEditForm(myPage.value)

    saveError.value = ""

    resetEmailChangeState()

    isEditing.value = true
}

async function withdrawAccount() {

   if (!confirm("정말로 회원탈퇴를 진행하시겠습니까?")) {
        return
    }

    try {
        await customerApi.withdrawAccount()

        authStore.clearAuth()

        alert("회원탈퇴가 완료되었습니다.")

        window.location.href = "/home"
    } catch (e) {
        console.error(e)

        const message =
            e.response?.data?.message
            || "회원탈퇴에 실패했습니다."

        alert(message)
    }
}

function cancelEditing() {

    setEditForm(myPage.value)

    saveError.value = ""

    resetEmailChangeState()

    isEditing.value = false
}

function handleEmailAddressInput() {

    if (
        emailChangeTarget.value
        && normalizeEmailAddress(editForm.emailAddress)
            !== emailChangeTarget.value
    ) {
        resetEmailChangeState()
    } else {
        emailChangeMessage.value = ""
        emailChangeError.value = ""
    }
}

function handleEmailCodeInput(event) {

    emailChangeCode.value = digitsOnly(event.target.value)
        .slice(0, 6)

    event.target.value = emailChangeCode.value

    emailChangeError.value = ""
}

function emailChangeFailureMessage(error, fallback) {

    return error.response?.data?.detail
        || error.response?.data?.message
        || fallback
}

async function startEmailChange() {

    const emailAddress = normalizeEmailAddress(
        editForm.emailAddress
    )

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailAddress)) {
        emailChangeError.value =
            "이메일 형식을 확인해주세요."
        return
    }

    emailChangeSending.value = true
    emailChangeError.value = ""
    emailChangeMessage.value = ""

    try {
        const response = await customerApi.startEmailChange(
            emailAddress
        )

        if (response.alreadySynchronized) {
            emailChangeChallengeId.value = ""
            emailChangeTarget.value = ""
            emailChangeCode.value = ""
            emailChangeMessage.value = response.message
            return
        }

        emailChangeChallengeId.value = response.challengeId
        emailChangeTarget.value = emailAddress
        emailChangeCode.value = ""
        emailChangeMessage.value = response.message
    } catch (error) {
        console.error(error)
        emailChangeError.value = emailChangeFailureMessage(
            error,
            "이메일 인증번호를 발송하지 못했습니다."
        )
    } finally {
        emailChangeSending.value = false
    }
}

async function verifyEmailChange() {

    if (!emailChangeChallengeId.value
        || !/^\d{6}$/.test(emailChangeCode.value)) {
        emailChangeError.value =
            "인증번호 6자리를 입력해주세요."
        return
    }

    emailChangeVerifying.value = true
    emailChangeError.value = ""
    emailChangeMessage.value = ""

    try {
        const updated = await customerApi.verifyEmailChange(
            emailChangeChallengeId.value,
            emailChangeCode.value
        )

        myPage.value = {
            ...myPage.value,
            emailAddress: updated.emailAddress
        }
        editForm.emailAddress = updated.emailAddress

        emailChangeChallengeId.value = ""
        emailChangeTarget.value = ""
        emailChangeCode.value = ""
        emailChangeMessage.value =
            "이메일 인증과 비밀번호 복구 이메일 동기화가 완료되었습니다."
    } catch (error) {
        console.error(error)
        emailChangeError.value = emailChangeFailureMessage(
            error,
            "인증번호를 확인하지 못했습니다."
        )
    } finally {
        emailChangeVerifying.value = false
    }
}


function handlePhonePartInput(
    part,
    maxLength,
    event,
    nextInput = null
) {

    const value =
        digitsOnly(event.target.value)
            .slice(0, maxLength)

    phoneParts[part] = value

    event.target.value = value

    syncPhoneValue()

    if (
        value.length === maxLength
        && nextInput
    ) {
        nextInput.focus()
    }
}


async function loadMyPage() {

    loading.value = true

    loadError.value = ""


    try {

        myPage.value =
            await customerApi.getMyPage()

        authStore.updateUserName(myPage.value?.name)

    } catch (e) {

        console.error(e)

        loadError.value =
            "마이페이지 정보를 불러오지 못했습니다."

    } finally {

        loading.value = false
    }
}


async function saveMyPage() {

    if (isEmailChanged.value) {

        saveError.value =
            "새 이메일의 인증번호 확인을 먼저 완료해주세요."

        return
    }

    if (!/^\d{11}$/.test(editForm.phone)) {

        saveError.value =
            "휴대전화번호 11자리를 입력해주세요."

        return
    }

    saving.value = true

    saveError.value = ""


    try {

        myPage.value =
            await customerApi.updateMyPage({

                name: editForm.name,

                birthDate: editForm.birthDate,

                phone: editForm.phone,

                gender: editForm.gender,

                emailAddress:
                    editForm.emailAddress

            })

        authStore.updateUserName(myPage.value?.name)


        isEditing.value = false

    } catch (e) {

        console.error(e)

        saveError.value =
            "회원정보 수정에 실패했습니다."

    } finally {

        saving.value = false
    }
}
function openWithdrawModal() {
    withdrawPassword.value = ""
    withdrawError.value = ""
    isWithdrawModalOpen.value = true
}

function closeWithdrawModal() {
    if (withdrawing.value) {
        return
    }

    isWithdrawModalOpen.value = false
    withdrawPassword.value = ""
    withdrawError.value = ""
}

async function confirmWithdraw() {
    if (!withdrawPassword.value.trim()) {
        withdrawError.value =
            "현재 비밀번호를 입력해주세요."
        return
    }

    withdrawError.value = ""

    try {
        withdrawing.value = true

        await customerApi.withdrawAccount(
            withdrawPassword.value
        )

        authStore.clearAuth()

        alert("회원탈퇴가 완료되었습니다.")
        window.location.href = "/home"
    } catch (e) {
        console.error(e)

        withdrawError.value =
            e.response?.data?.detail
            || e.response?.data?.message
            || "회원탈퇴에 실패했습니다."
    } finally {
        withdrawing.value = false
    }
}


function formatAmount(value) {

    return `${Number(value || 0).toLocaleString()}원`
}


onMounted(() => {

    loadMyPage()

    paymentStore.fetchWallet({
        silent: true
    })

    loadBenefitSummary()

})

</script>


<style scoped>

.mypage {
  max-width: 760px;
  margin: 0 auto;
  padding: 64px 20px;
  color: #071b3a;
  font-family: 'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.mypage h1 {
  margin: 0 0 34px;
  color: #071b3a;
  font-size: 42px;
  font-weight: 900;
  text-align: center;
}

.mypage-card {
  display: grid;
  gap: 0;
  padding: 42px 46px;
  border: 1px solid #e4e9f1;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 30px 70px rgba(7, 27, 58, 0.09);
}

.info-row {
  display: grid;
  grid-template-columns: 180px 1fr;
  align-items: center;
  gap: 18px;
  padding: 17px 4px;
  border-bottom: 1px solid #e4e9f1;
}

.info-row strong {
  color: #52637d;
  font-size: 15px;
  font-weight: 900;
}

.info-row span,
.info-row a {
  color: #071b3a;
  font-size: 15px;
  font-weight: 800;
}

.benefit-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.benefit-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid #b9d7c2;
  border-radius: 6px;
  background: #f5faf7;
  color: #166534 !important;
  font-size: 13px !important;
  text-decoration: none;
}

.benefit-link:hover {
  border-color: #166534;
}

.password-change-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-height: 38px;
  padding: 0 14px;
  color: #174d82;
  text-decoration: none;
  background: #f0f6fc;
  border: 1px solid #c9dcef;
  border-radius: 999px;
  box-shadow: 0 4px 12px rgba(23, 77, 130, 0.08);
  transition: background 0.2s ease, border-color 0.2s ease,
    box-shadow 0.2s ease, transform 0.2s ease;
}

.password-action-cell {
  display: flex;
  justify-content: center;
  width: 100%;
}

.password-change-link:hover {
  color: #0f3d6b;
  background: #e5f0fa;
  border-color: #9fbedc;
  box-shadow: 0 7px 16px rgba(23, 77, 130, 0.14);
  transform: translateY(-1px);
}

.password-change-link:focus-visible {
  outline: 3px solid rgba(23, 77, 130, 0.2);
  outline-offset: 2px;
}

.info-row input:not([type="radio"]) {
  width: 100%;
  height: 42px;
  padding: 0 14px;
  border: 1px solid #d7deea;
  border-radius: 8px;
  background: #f8fafc;
  color: #071b3a;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.info-row input:not([type="radio"]):focus {
  border-color: #071b3a;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(7, 27, 58, 0.08);
}

.email-verification-fields {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.email-input-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.email-verification-button {
  min-width: 132px;
  padding: 0 14px;
  border: 1px solid #9fb3cc;
  background: #f0f6fc;
  color: #0f3d6b;
}

.email-help-message,
.email-success-message,
.email-error-message {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
}

.email-help-message {
  color: #64748b;
}

.email-success-message {
  color: #15803d;
}

.email-error-message {
  color: #dc2626;
}

.phone-number-inputs {
  display: grid;
  grid-template-columns: minmax(0, 3fr) 12px minmax(0, 4fr) 12px minmax(0, 4fr);
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.phone-number-inputs span {
  text-align: center;
}

.gender-options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.gender-options label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border: 1px solid #d7deea;
  border-radius: 8px;
  background: #f8fafc;
  color: #52637d;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
}

.error-message {
  margin: 18px 0 0;
  color: #dc2626;
  font-size: 14px;
  font-weight: 800;
  text-align: center;
}

button {
  height: 44px;
  padding: 0 22px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 900;
  cursor: pointer;
}

.mypage-card > button {
  width: 180px;
  margin: 28px auto 0;
  border: 0;
  background: #071b3a;
  color: #fff;
  box-shadow: 0 8px 18px rgba(7, 27, 58, 0.18);
}

.button-row {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 28px;
}

.button-row button:first-child {
  border: 0;
  background: #071b3a;
  color: #fff;
  box-shadow: 0 8px 18px rgba(7, 27, 58, 0.18);
}

.button-row button:last-child {
  border: 1px solid #aebbd0;
  background: #fff;
  color: #071b3a;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .mypage {
    padding: 34px 16px;
  }

  .mypage h1 {
    font-size: 34px;
  }

  .mypage-card {
    padding: 32px 22px;
  }

  .info-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .email-input-actions {
    grid-template-columns: 1fr;
  }

  .email-verification-button {
    width: 100%;
  }

  .benefit-value {
    align-items: flex-start;
    flex-direction: column;
  }

  .button-row {
    flex-direction: column;
  }

  .button-row button,
  .mypage-card > button {
    width: 100%;
  }
}
.withdraw-button {
    color: #b42318;
    border-color: #f0b8b3;
    background: #fff5f4;
}

.withdraw-modal-backdrop {
    position: fixed;
    inset: 0;
    z-index: 1000;
    display: grid;
    place-items: center;
    padding: 20px;
    background: rgba(7, 27, 58, 0.55);
}

.withdraw-modal {
    width: min(100%, 420px);
    padding: 24px;
    border-radius: 12px;
    background: #ffffff;
    box-shadow: 0 24px 60px rgba(7, 27, 58, 0.25);
}

.withdraw-modal h2 {
    margin: 0 0 12px;
    color: #b42318;
}

.withdraw-modal > p {
    color: #52637d;
    line-height: 1.6;
}

.withdraw-modal label {
    display: grid;
    gap: 8px;
    margin-top: 18px;
}

.withdraw-modal input {
    width: 100%;
    box-sizing: border-box;
}

.withdraw-modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 22px;
}

.withdraw-confirm-button {
    color: #ffffff;
    background: #b42318;
}
</style>
