<template>
    <main class="owner-register-page">
        <section class="owner-register-card">
            <h1>업주 등록</h1>

            <form
                class="owner-register-form"
                @submit.prevent="submitOwner"
            >
                <div class="form-row">
                    <label for="owner-login-id">로그인 아이디</label>

                    <div class="field-control">
                        <div class="inline-control">
                            <input
                                id="owner-login-id"
                                v-model="form.loginId"
                                required
                                autocomplete="username"
                                autocapitalize="none"
                                spellcheck="false"
                                placeholder="영문 및 숫자"
                                @input="handleLoginIdInput"
                            >

                            <button
                                class="check-button"
                                type="button"
                                :disabled="checkingLoginId || !form.loginId"
                                @click="checkLoginId"
                            >
                                {{ checkingLoginId ? "확인 중..." : "중복 확인" }}
                            </button>
                        </div>

                        <small
                            v-if="loginIdMessage"
                            class="field-message"
                            :class="{ success: loginIdAvailable }"
                        >
                            {{ loginIdMessage }}
                        </small>
                    </div>
                </div>

                <div class="form-row">
                    <label for="owner-password">비밀번호</label>

                    <div class="field-control">
                        <div class="password-inputs">
                            <input
                                id="owner-password"
                                v-model="form.password"
                                type="password"
                                required
                                autocomplete="new-password"
                                placeholder="비밀번호"
                            >

                            <input
                                v-model="confirmPassword"
                                type="password"
                                required
                                autocomplete="new-password"
                                placeholder="비밀번호 확인"
                            >
                        </div>

                        <small
                            v-if="passwordsMismatch"
                            class="field-message error"
                        >
                            비밀번호가 다릅니다.
                        </small>
                    </div>
                </div>

                <div class="form-row">
                    <label for="owner-name">업주명</label>

                    <input
                        id="owner-name"
                        v-model.trim="form.ownerName"
                        required
                    >
                </div>

                <div class="form-row">
                    <label for="business-number">사업자번호</label>

                    <div class="business-number-inputs">
                        <input
                            id="business-number"
                            :value="businessNumberParts.first"
                            required
                            inputmode="numeric"
                            maxlength="3"
                            pattern="[0-9]{3}"
                            placeholder="123"
                            aria-label="사업자번호 앞 3자리"
                            @input="handleBusinessNumberPartInput(
                                'first',
                                3,
                                $event,
                                businessNumberMiddleInput
                            )"
                        >

                        <span aria-hidden="true">-</span>

                        <input
                            ref="businessNumberMiddleInput"
                            :value="businessNumberParts.middle"
                            required
                            inputmode="numeric"
                            maxlength="2"
                            pattern="[0-9]{2}"
                            placeholder="45"
                            aria-label="사업자번호 가운데 2자리"
                            @input="handleBusinessNumberPartInput(
                                'middle',
                                2,
                                $event,
                                businessNumberLastInput
                            )"
                        >

                        <span aria-hidden="true">-</span>

                        <input
                            ref="businessNumberLastInput"
                            :value="businessNumberParts.last"
                            required
                            inputmode="numeric"
                            maxlength="5"
                            pattern="[0-9]{5}"
                            placeholder="67890"
                            aria-label="사업자번호 뒤 5자리"
                            @input="handleBusinessNumberPartInput(
                                'last',
                                5,
                                $event
                            )"
                        >
                    </div>
                </div>

                <div class="form-row">
                    <label for="owner-phone-first">전화번호</label>

                    <div class="field-control">
                        <div class="phone-number-inputs">
                            <input
                                id="owner-phone-first"
                                :value="phoneParts.first"
                                required
                                inputmode="numeric"
                                maxlength="3"
                                pattern="[0-9]{3}"
                                placeholder="010"
                                aria-label="전화번호 앞 3자리"
                                @input="handlePhonePartInput(
                                    'first',
                                    3,
                                    $event,
                                    phoneMiddleInput
                                )"
                            >

                            <span aria-hidden="true">-</span>

                            <input
                                ref="phoneMiddleInput"
                                :value="phoneParts.middle"
                                required
                                inputmode="numeric"
                                maxlength="4"
                                pattern="[0-9]{4}"
                                placeholder="1234"
                                aria-label="전화번호 가운데 4자리"
                                @input="handlePhonePartInput(
                                    'middle',
                                    4,
                                    $event,
                                    phoneLastInput
                                )"
                            >

                            <span aria-hidden="true">-</span>

                            <input
                                ref="phoneLastInput"
                                :value="phoneParts.last"
                                required
                                inputmode="numeric"
                                maxlength="4"
                                pattern="[0-9]{4}"
                                placeholder="5678"
                                aria-label="전화번호 뒤 4자리"
                                @input="handlePhonePartInput(
                                    'last',
                                    4,
                                    $event
                                )"
                                @blur="checkPhone"
                            >
                        </div>

                        <small
                            v-if="phoneMessage"
                            class="field-message error"
                        >
                            {{ phoneMessage }}
                        </small>
                    </div>
                </div>

                <div class="form-row">
                    <label for="owner-email-id">이메일</label>

                    <div class="field-control">
                        <div class="email-inputs">
                            <input
                                id="owner-email-id"
                                v-model.trim="emailId"
                                required
                                placeholder="이메일 아이디"
                                @input="resetEmailCheck"
                                @blur="checkEmail"
                            >

                            <span>@</span>

                            <input
                                v-if="emailDomainOption === 'direct'"
                                v-model.trim="emailDomain"
                                required
                                placeholder="직접 입력"
                                @input="resetEmailCheck"
                                @blur="checkEmail"
                            >

                            <input
                                v-else
                                :value="emailDomainOption"
                                readonly
                            >

                            <select
                                v-model="emailDomainOption"
                                @change="handleEmailDomainChange"
                                @blur="checkEmail"
                            >
                                <option value="naver.com">naver.com</option>
                                <option value="gmail.com">gmail.com</option>
                                <option value="daum.net">daum.net</option>
                                <option value="hanmail.net">hanmail.net</option>
                                <option value="direct">직접 입력</option>
                            </select>
                        </div>

                        <small
                            v-if="emailMessage"
                            class="field-message error"
                        >
                            {{ emailMessage }}
                        </small>
                    </div>
                </div>

                <div class="form-row">
                    <label for="business-postcode">사업장 주소</label>

                    <div class="field-control address-control">
                        <div class="postcode-control">
                            <input
                                id="business-postcode"
                                :value="postcode"
                                placeholder="우편번호"
                                readonly
                                aria-label="우편번호"
                            >

                            <button
                                class="check-button"
                                type="button"
                                @click="openJusoAddressSearch"
                            >
                                주소 검색
                            </button>
                        </div>

                        <input
                            id="business-address"
                            :value="displayRoadAddress"
                            placeholder="주소 검색 버튼으로 도로명주소를 선택해주세요."
                            readonly
                            required
                            aria-label="도로명주소"
                        >

                        <input
                            ref="detailAddressInput"
                            v-model.trim="detailAddress"
                            placeholder="상세주소를 입력해주세요."
                            :disabled="!roadAddress"
                            maxlength="100"
                            aria-label="상세주소"
                            @input="syncBusinessAddress"
                        >

                        <small
                            v-if="addressSearchError"
                            class="field-message error"
                            role="alert"
                        >
                            {{ addressSearchError }}
                        </small>
                    </div>
                </div>

                <p
                    v-if="message"
                    class="result-message"
                    :class="messageType"
                    role="status"
                >
                    {{ message }}
                </p>

                <button
                    class="submit-button"
                    type="submit"
                    :disabled="submitting"
                >
                    {{ submitting ? "등록 중..." : "업주 등록" }}
                </button>
            </form>
        </section>
    </main>
</template>

<script setup>
import { computed, nextTick, reactive, ref } from "vue"
import { useRouter } from "vue-router"
import * as adminApi from "@/features/admin/api/adminApi"
import { useJusoAddressPopup } from "@/features/address/composables/useJusoAddressPopup"
import * as authApi from "../api/authApi"

const router = useRouter()

const submitting = ref(false)
const message = ref("")
const messageType = ref("")
const checkingLoginId = ref(false)
const loginIdAvailable = ref(false)
const checkedLoginId = ref("")
const loginIdMessage = ref("")
let loginIdCheckRequestId = 0
const confirmPassword = ref("")
const passwordCheckAttempted = ref(false)
const phoneMessage = ref("")
const emailMessage = ref("")
const emailId = ref("")
const emailDomainOption = ref("naver.com")
const emailDomain = ref("")
const businessNumberMiddleInput = ref(null)
const businessNumberLastInput = ref(null)
const phoneMiddleInput = ref(null)
const phoneLastInput = ref(null)
const postcode = ref("")
const roadAddress = ref("")
const roadAddressExtra = ref("")
const detailAddress = ref("")
const detailAddressInput = ref(null)
const businessNumberParts = reactive({
    first: "",
    middle: "",
    last: ""
})
const phoneParts = reactive({
    first: "",
    middle: "",
    last: ""
})

const form = reactive({
    loginId: "",
    password: "",
    ownerName: "",
    businessNumber: "",
    phone: "",
    emailAddress: "",
    businessAddress: ""
})

const displayRoadAddress = computed(() =>
    [roadAddress.value, roadAddressExtra.value]
        .filter(Boolean)
        .join(" ")
)

function syncBusinessAddress() {
    const address = [
        displayRoadAddress.value,
        detailAddress.value.trim()
    ]
        .filter(Boolean)
        .join(" ")
        .replace(/\s+/g, " ")

    form.businessAddress = postcode.value && address
        ? `[${postcode.value}] ${address}`
        : address
}

function handleJusoAddressSelected(address) {
    postcode.value = String(address.zipNo || "").trim()
    roadAddress.value = String(
        address.roadAddrPart1 || address.roadFullAddr || ""
    ).trim()
    roadAddressExtra.value = String(address.roadAddrPart2 || "").trim()
    detailAddress.value = String(address.addrDetail || "").trim()
    syncBusinessAddress()

    nextTick(() => detailAddressInput.value?.focus())
}

const {
    errorMessage: addressSearchError,
    openJusoAddressSearch
} = useJusoAddressPopup(handleJusoAddressSelected)

const passwordsMismatch = computed(() =>
    (
        confirmPassword.value.length > 0
        || passwordCheckAttempted.value
    ) && form.password !== confirmPassword.value
)

function handleLoginIdInput() {
    const filtered = form.loginId.replace(/[^A-Za-z0-9]/g, "")
    const removedInvalidCharacters = form.loginId !== filtered

    form.loginId = filtered
    loginIdCheckRequestId += 1
    checkingLoginId.value = false
    loginIdAvailable.value = false
    checkedLoginId.value = ""
    loginIdMessage.value = removedInvalidCharacters
        ? "아이디는 영문과 숫자만 입력할 수 있습니다."
        : ""
}

async function checkLoginId() {
    if (!form.loginId) {
        loginIdAvailable.value = false
        checkedLoginId.value = ""
        loginIdMessage.value = "아이디를 입력해주세요."
        return false
    }

    const loginId = form.loginId
    const requestId = ++loginIdCheckRequestId
    checkingLoginId.value = true
    loginIdAvailable.value = false
    checkedLoginId.value = ""

    try {
        const available = await authApi.checkLoginId(loginId)

        if (
            requestId !== loginIdCheckRequestId
            || form.loginId !== loginId
        ) {
            return false
        }

        loginIdAvailable.value = available
        checkedLoginId.value = available ? loginId : ""
        loginIdMessage.value = available
            ? "사용 가능한 아이디입니다."
            : "이미 사용 중인 아이디입니다."

        return available
    } catch (e) {
        if (
            requestId === loginIdCheckRequestId
            && form.loginId === loginId
        ) {
            loginIdAvailable.value = false
            checkedLoginId.value = ""
            loginIdMessage.value = "중복 확인에 실패했습니다."
        }

        return false
    } finally {
        if (requestId === loginIdCheckRequestId) {
            checkingLoginId.value = false
        }
    }
}

function digitsOnly(value) {
    return value.replace(/\D/g, "")
}

function handleBusinessNumberPartInput(
    part,
    maxLength,
    event,
    nextInput = null
) {
    const value = digitsOnly(event.target.value).slice(0, maxLength)

    businessNumberParts[part] = value
    event.target.value = value
    form.businessNumber = [
        businessNumberParts.first,
        businessNumberParts.middle,
        businessNumberParts.last
    ].join("-")

    if (value.length === maxLength) {
        nextInput?.focus()
    }
}

function handlePhonePartInput(
    part,
    maxLength,
    event,
    nextInput = null
) {
    const value = digitsOnly(event.target.value)
        .slice(0, maxLength)

    phoneParts[part] = value
    form.phone = [
        phoneParts.first,
        phoneParts.middle,
        phoneParts.last
    ].join("")
    phoneMessage.value = ""
    event.target.value = value

    if (value.length === maxLength) {
        nextInput?.focus()
    }
}

async function checkPhone() {
    const phone = form.phone

    if (!/^\d{11}$/.test(phone)) {
        phoneMessage.value =
            "전화번호 11자리를 입력해주세요."

        return false
    }

    try {
        const available = await authApi.checkPhone(phone)

        if (form.phone === phone) {
            phoneMessage.value = available
                ? ""
                : "이미 등록된 전화번호입니다."
        }

        return form.phone === phone && available
    } catch (e) {
        if (form.phone === phone) {
            phoneMessage.value = "전화번호 중복 확인에 실패했습니다."
        }

        return false
    }
}

function selectedEmailAddress() {
    const selectedDomain = emailDomainOption.value === "direct"
        ? emailDomain.value.trim()
        : emailDomainOption.value

    if (!emailId.value || !selectedDomain) {
        return ""
    }

    return `${emailId.value}@${selectedDomain}`.toLowerCase()
}

function resetEmailCheck() {
    emailMessage.value = ""
}

function handleEmailDomainChange() {
    resetEmailCheck()

    if (emailDomainOption.value !== "direct") {
        checkEmail()
    }
}

async function checkEmail() {
    const emailAddress = selectedEmailAddress()

    if (!emailAddress) {
        return false
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailAddress)) {
        emailMessage.value = "이메일 형식을 확인해주세요."
        return false
    }

    try {
        const available = await authApi.checkEmail(emailAddress)

        if (selectedEmailAddress() === emailAddress) {
            emailMessage.value = available
                ? ""
                : "이미 등록된 이메일입니다."
        }

        return selectedEmailAddress() === emailAddress && available
    } catch (e) {
        if (selectedEmailAddress() === emailAddress) {
            emailMessage.value = "이메일 중복 확인에 실패했습니다."
        }

        return false
    }
}

async function submitOwner() {
    message.value = ""
    messageType.value = ""
    passwordCheckAttempted.value = true

    if (passwordsMismatch.value) {
        return
    }

    if (
        !loginIdAvailable.value
        || checkedLoginId.value !== form.loginId
    ) {
        message.value = "아이디 중복 확인을 해주세요."
        messageType.value = "error"
        return
    }

    const emailAddress = selectedEmailAddress()

    if (!emailAddress) {
        emailMessage.value = "이메일을 입력해주세요."
        return
    }

    if (!roadAddress.value || !form.businessAddress) {
        addressSearchError.value =
            "주소 검색 버튼으로 사업장 주소를 선택해주세요."
        return
    }

    const [phoneAvailable, emailAvailable] = await Promise.all([
        checkPhone(),
        checkEmail()
    ])

    if (!phoneAvailable || !emailAvailable) {
        message.value = "입력한 정보를 확인해주세요."
        messageType.value = "error"
        return
    }

    submitting.value = true

    try {
        await adminApi.registerOwner({
            ...form,
            emailAddress
        })

        message.value = "업주 등록이 완료되었습니다."
        messageType.value = "success"

        window.setTimeout(() => {
            router.push({ name: "admin-wash-sites" })
        }, 800)
    } catch (e) {
        message.value =
            e.response?.data?.message ||
            e.response?.data ||
            "업주 등록에 실패했습니다."
        messageType.value = "error"
    } finally {
        submitting.value = false
    }
}
</script>

<style scoped>
* {
    box-sizing: border-box;
}

.owner-register-page {
    max-width: 760px;
    margin: 50px auto;
    padding: 0 20px;
    color: #071b3a;
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.owner-register-card {
    padding: 36px;
    border: 1px solid #e1e7ef;
    border-radius: 8px;
    background: #ffffff;
    box-shadow: 0 10px 25px rgba(7, 27, 58, 0.08);
}

h1 {
    margin: 0 0 24px;
    color: #1e3a5f;
    font-size: 30px;
    font-weight: 800;
}

.owner-register-form {
    display: grid;
}

.form-row {
    display: grid;
    grid-template-columns: 150px minmax(0, 1fr);
    align-items: center;
    gap: 14px;
    padding: 16px 0;
    border-bottom: 1px solid #eef2f7;
}

.form-row > label {
    color: #1e3a5f;
    font-size: 14px;
    font-weight: 800;
}

.field-control {
    display: grid;
    gap: 7px;
    min-width: 0;
}

.inline-control {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 110px;
    gap: 8px;
}

.address-control {
    gap: 8px;
}

.postcode-control {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 110px;
    gap: 8px;
}

.address-control input[readonly] {
    color: #34445d;
    background: #f1f4f8;
    cursor: default;
}

.address-control input:disabled {
    color: #8b96a6;
    background: #f1f4f8;
    cursor: not-allowed;
}

.password-inputs {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
}

.business-number-inputs {
    display: grid;
    grid-template-columns:
        minmax(0, 3fr)
        12px
        minmax(0, 2fr)
        12px
        minmax(0, 5fr);
    align-items: center;
    gap: 6px;
}

.phone-number-inputs {
    display: grid;
    grid-template-columns:
        minmax(0, 3fr)
        12px
        minmax(0, 4fr)
        12px
        minmax(0, 4fr);
    align-items: center;
    gap: 6px;
}

.business-number-inputs > span,
.phone-number-inputs > span {
    color: #52637d;
    font-weight: 800;
    text-align: center;
}

.email-inputs {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 18px minmax(0, 1fr) 120px;
    align-items: center;
    gap: 6px;
}

.email-inputs > span {
    color: #52637d;
    font-weight: 800;
    text-align: center;
}

input,
select {
    width: 100%;
    height: 44px;
    padding: 0 13px;
    border: 1px solid #ccd5e0;
    border-radius: 8px;
    color: #071b3a;
    background: #fafbfd;
    font: inherit;
    font-size: 14px;
    outline: none;
}

input:focus,
select:focus {
    border-color: #1e3a5f;
    background: #ffffff;
    box-shadow: 0 0 0 3px rgba(30, 58, 95, 0.12);
}

input::placeholder {
    color: #8b96a6;
}

.check-button {
    min-height: 44px;
    border: 0;
    border-radius: 8px;
    color: #ffffff;
    background: #1e3a5f;
    font: inherit;
    font-size: 14px;
    font-weight: 800;
    cursor: pointer;
}

.check-button:disabled,
.submit-button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.field-message {
    color: #52637d;
    font-size: 13px;
    line-height: 1.5;
}

.field-message.success,
.result-message.success {
    color: #16794f;
}

.field-message.error,
.result-message.error {
    color: #b42318;
}

.result-message {
    margin: 18px 0 0;
    font-size: 14px;
    font-weight: 700;
    text-align: center;
}

.submit-button {
    width: 100%;
    min-height: 48px;
    margin-top: 26px;
    border: 0;
    border-radius: 8px;
    color: #ffffff;
    background: #1e3a5f;
    font: inherit;
    font-size: 15px;
    font-weight: 800;
    cursor: pointer;
}

.submit-button:hover:not(:disabled),
.check-button:hover:not(:disabled) {
    background: #284d77;
}

@media (max-width: 720px) {
    .owner-register-page {
        margin: 24px auto;
        padding: 0 14px;
    }

    .owner-register-card {
        padding: 26px 20px;
    }

    .form-row {
        grid-template-columns: 1fr;
        gap: 8px;
    }

    .password-inputs,
    .email-inputs {
        grid-template-columns: 1fr;
    }

    .postcode-control {
        grid-template-columns: minmax(0, 1fr) 100px;
    }

    .email-inputs > span {
        display: none;
    }
}
</style>
