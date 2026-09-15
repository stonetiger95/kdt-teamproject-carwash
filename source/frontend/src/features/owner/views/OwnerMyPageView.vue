<template>

    <div class="mypage">

        <h1>
            {{
                isEditing
                    ? "업주정보 수정"
                    : "업주 마이페이지"
            }}
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

        <!-- 조회 화면 -->
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
                    <RouterLink :to="{ name: 'OwnerPasswordChange'}" class="password-change-link">비밀번호 변경</RouterLink>
                </span>
            </div>


            <div class="info-row">
                <strong>업주명</strong>
                <span>{{ myPage.ownerName }}</span>
            </div>


            <div class="info-row">
                <strong>사업자번호</strong>
                <span>{{ myPage.businessNumber }}</span>
            </div>


            <div class="info-row">
                <strong>전화번호</strong>
                <span>{{ formatPhoneNumber(myPage.phone) }}</span>
            </div>


            <div class="info-row">
                <strong>이메일</strong>
                <span>{{ myPage.emailAddress }}</span>
            </div>


            <div class="info-row">
                <strong>사업장 주소</strong>
                <span>{{ myPage.businessAddress }}</span>
            </div>


            <div class="info-row">
                <strong>상태</strong>
                <span>{{ statusText }}</span>
            </div>

            <button
                type="button"
                @click="startEditing"
            >
                회원정보 수정
            </button>

        </div>


        <!-- 수정 화면 -->
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
                <strong>업주명</strong>

                <input
                    v-model.trim="editForm.ownerName"
                    required
                >
            </label>


            <div class="info-row">
                <strong>사업자번호</strong>

                <div class="business-number-inputs">
                    <input
                        :value="businessNumberParts.first"
                        required
                        inputmode="numeric"
                        pattern="[0-9]*"
                        maxlength="3"
                        aria-label="사업자번호 앞자리"
                        @input="handleBusinessNumberPartInput('first', 3, $event, businessNumberMiddleInput)"
                    >
                    <span aria-hidden="true">-</span>
                    <input
                        ref="businessNumberMiddleInput"
                        :value="businessNumberParts.middle"
                        required
                        inputmode="numeric"
                        pattern="[0-9]*"
                        maxlength="2"
                        aria-label="사업자번호 가운데자리"
                        @input="handleBusinessNumberPartInput('middle', 2, $event, businessNumberLastInput)"
                    >
                    <span aria-hidden="true">-</span>
                    <input
                        ref="businessNumberLastInput"
                        :value="businessNumberParts.last"
                        required
                        inputmode="numeric"
                        pattern="[0-9]*"
                        maxlength="5"
                        aria-label="사업자번호 뒷자리"
                        @input="handleBusinessNumberPartInput('last', 5, $event)"
                    >
                </div>
            </div>


            <div class="info-row">
                <strong>전화번호</strong>

                <div class="phone-number-inputs">
                    <input
                        :value="phoneParts.first"
                        required
                        inputmode="numeric"
                        pattern="[0-9]*"
                        maxlength="3"
                        aria-label="전화번호 앞자리"
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
                        aria-label="전화번호 가운데자리"
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
                        aria-label="전화번호 뒷자리"
                        @input="handlePhonePartInput('last', 4, $event)"
                    >
                </div>
            </div>


            <div class="info-row">
                <strong>이메일</strong>

                <div class="email-inputs">
                    <input
                        v-model.trim="emailId"
                        required
                        aria-label="이메일 아이디"
                    >
                    <span aria-hidden="true">@</span>
                    <input
                        v-if="emailDomainOption === 'direct'"
                        v-model.trim="emailDomain"
                        required
                        aria-label="이메일 도메인"
                    >
                    <input
                        v-else
                        :value="emailDomainOption"
                        readonly
                        aria-label="이메일 도메인"
                    >
                    <select
                        v-model="emailDomainOption"
                        aria-label="이메일 도메인 선택"
                    >
                        <option value="naver.com">naver.com</option>
                        <option value="gmail.com">gmail.com</option>
                        <option value="daum.net">daum.net</option>
                        <option value="hanmail.net">hanmail.net</option>
                        <option value="direct">직접 입력</option>
                    </select>
                </div>
            </div>


            <label class="info-row">
                <strong>사업장 주소</strong>

                <input
                    v-model.trim="editForm.businessAddress"
                    required
                >
            </label>


            <div class="button-row">

                <button
                    type="submit"
                    :disabled="saving"
                >
                    {{
                        saving
                            ? "수정 중..."
                            : "수정완료"
                    }}
                </button>


                <button
                    type="button"
                    @click="cancelEditing"
                >
                    취소
                </button>

            </div>


            <p
                v-if="saveError"
                class="error-message"
            >
                {{ saveError }}
            </p>

        </form>

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

import * as ownerApi
    from "../api/ownerApi"


const myPage = ref(null)

const loading = ref(false)

const loadError = ref("")

const isEditing = ref(false)

const saving = ref(false)

const saveError = ref("")

const businessNumberMiddleInput = ref(null)

const businessNumberLastInput = ref(null)

const phoneMiddleInput = ref(null)

const phoneLastInput = ref(null)

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

const emailId = ref("")

const emailDomainOption = ref("naver.com")

const emailDomain = ref("")


const editForm = reactive({

    ownerName: "",

    businessNumber: "",

    phone: "",

    emailAddress: "",

    businessAddress: ""

})


const presetEmailDomains = [
    "naver.com",
    "gmail.com",
    "daum.net",
    "hanmail.net"
]


function digitsOnly(value) {

    return String(value || "")
        .replace(/\D/g, "")
}


function syncBusinessNumberValue() {

    editForm.businessNumber = [
        businessNumberParts.first,
        businessNumberParts.middle,
        businessNumberParts.last
    ].join("-")
}


function setBusinessNumberParts(value) {

    const digits =
        digitsOnly(value).slice(0, 10)

    businessNumberParts.first =
        digits.slice(0, 3)

    businessNumberParts.middle =
        digits.slice(3, 5)

    businessNumberParts.last =
        digits.slice(5, 10)

    syncBusinessNumberValue()
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


function setEmailParts(value) {

    const emailAddress =
        String(value || "").trim()

    const separatorIndex =
        emailAddress.lastIndexOf("@")

    emailId.value = separatorIndex > 0
        ? emailAddress.slice(0, separatorIndex)
        : emailAddress

    const domain = separatorIndex > 0
        ? emailAddress.slice(separatorIndex + 1)
        : ""

    if (presetEmailDomains.includes(domain)) {
        emailDomainOption.value = domain
        emailDomain.value = ""
        return
    }

    emailDomainOption.value = "direct"
    emailDomain.value = domain
}


function selectedEmailAddress() {

    const domain = emailDomainOption.value === "direct"
        ? emailDomain.value.trim()
        : emailDomainOption.value

    return `${emailId.value.trim()}@${domain}`
}


function handleSegmentInput(
    parts,
    part,
    maxLength,
    event,
    syncValue,
    nextInput = null
) {

    const value =
        digitsOnly(event.target.value)
            .slice(0, maxLength)

    parts[part] = value

    event.target.value = value

    syncValue()

    if (
        value.length === maxLength
        && nextInput
    ) {
        nextInput.focus()
    }
}


function handleBusinessNumberPartInput(
    part,
    maxLength,
    event,
    nextInput = null
) {

    handleSegmentInput(
        businessNumberParts,
        part,
        maxLength,
        event,
        syncBusinessNumberValue,
        nextInput
    )
}


function handlePhonePartInput(
    part,
    maxLength,
    event,
    nextInput = null
) {

    handleSegmentInput(
        phoneParts,
        part,
        maxLength,
        event,
        syncPhoneValue,
        nextInput
    )
}


const statusText = computed(() => {

    if (!myPage.value) {
        return ""
    }


    switch (myPage.value.status) {

        case "ACTIVE":
            return "활성"

        case "INACTIVE":
            return "비활성"

        default:
            return myPage.value.status
    }
})


async function loadMyPage() {

    loading.value = true

    loadError.value = ""


    try {

        myPage.value =
            await ownerApi.getMyPage()


    } catch (e) {

        console.error(e)

        loadError.value =
            "업주 정보를 불러오지 못했습니다."


    } finally {

        loading.value = false
    }
}


function startEditing() {

    editForm.ownerName =
        myPage.value.ownerName

    setBusinessNumberParts(
        myPage.value.businessNumber
    )

    setPhoneParts(myPage.value.phone)

    setEmailParts(myPage.value.emailAddress)

    editForm.businessAddress =
        myPage.value.businessAddress


    saveError.value = ""

    isEditing.value = true
}


function cancelEditing() {

    setBusinessNumberParts(
        myPage.value.businessNumber
    )

    setPhoneParts(myPage.value.phone)

    setEmailParts(myPage.value.emailAddress)

    saveError.value = ""

    isEditing.value = false
}


async function saveMyPage() {

    if (!/^\d{3}-\d{2}-\d{5}$/.test(
        editForm.businessNumber
    )) {
        saveError.value =
            "사업자번호 10자리를 입력해주세요."
        return
    }

    if (!/^\d{11}$/.test(editForm.phone)) {
        saveError.value =
            "전화번호 11자리를 입력해주세요."
        return
    }

    editForm.emailAddress =
        selectedEmailAddress()

    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(
        editForm.emailAddress
    )) {
        saveError.value =
            "이메일을 올바르게 입력해주세요."
        return
    }

    saving.value = true

    saveError.value = ""


    try {

        myPage.value =
            await ownerApi.updateMyPage({
                ...editForm
            })


        isEditing.value = false


    } catch (e) {

        console.error(e)

        saveError.value =
            "업주 정보 수정에 실패했습니다."


    } finally {

        saving.value = false
    }
}


onMounted(() => {

    loadMyPage()
})

</script>


<style scoped>

* {
    box-sizing: border-box;
}

.mypage {
    max-width: 900px;
    margin: 50px auto;
    padding: 0 20px;
    font-family: "Pretendard", sans-serif;
}

.mypage h1 {
    font-size: 30px;
    color: #1e3a5f;
    margin-bottom: 35px;
    font-weight: 700;
}

.mypage-card {
    background: #ffffff;
    border-radius: 18px;
    padding: 35px;
    box-shadow: 0 10px 25px rgba(0,0,0,0.08);
    border: 1px solid #e7ecf3;
}

.info-row {
    display: flex;
    align-items: center;
    padding: 18px 0;
    border-bottom: 1px solid #eef2f7;
}

.info-row:last-child {
    border-bottom: none;
}

.info-row strong {
    width: 180px;
    color: #1e3a5f;
    font-weight: 700;
    flex-shrink: 0;
}

.info-row span {
    color: #444;
    font-size: 15px;
    flex: 1;
}

.info-row input {
    flex: 1;
    height: 46px;
    padding: 0 14px;
    border: 1px solid #ccd5e0;
    border-radius: 10px;
    font-size: 15px;
    transition: .25s;
    background: #fafbfd;
}

.info-row input:focus {
    outline: none;
    border-color: #1e3a5f;
    box-shadow: 0 0 0 3px rgba(30,58,95,.12);
    background: white;
}

.business-number-inputs,
.phone-number-inputs,
.email-inputs {
    flex: 1;
    min-width: 0;
}

.business-number-inputs,
.phone-number-inputs {
    display: grid;
    align-items: center;
    gap: 6px;
}

.business-number-inputs {
    grid-template-columns: minmax(0, 3fr) 12px minmax(0, 2fr) 12px minmax(0, 5fr);
}

.phone-number-inputs {
    grid-template-columns: minmax(0, 3fr) 12px minmax(0, 4fr) 12px minmax(0, 4fr);
}

.business-number-inputs span,
.phone-number-inputs span,
.email-inputs span {
    text-align: center;
}

.email-inputs {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 18px minmax(0, 1fr) 130px;
    align-items: center;
    gap: 8px;
}

.email-inputs select {
    width: 100%;
    height: 46px;
    padding: 0 10px;
    border: 1px solid #ccd5e0;
    border-radius: 10px;
    background: #fafbfd;
    color: #444;
    font-size: 14px;
}

.email-inputs select:focus {
    outline: none;
    border-color: #1e3a5f;
    box-shadow: 0 0 0 3px rgba(30,58,95,.12);
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
    font-weight: 800;
    font-size: 15px;
    background: #f0f6fc;
    border: 1px solid #c9dcef;
    border-radius: 999px;
    box-shadow: 0 4px 12px rgba(23, 77, 130, 0.08);
    transition: background 0.2s ease, border-color 0.2s ease,
        box-shadow 0.2s ease, transform 0.2s ease;
}

.password-change-link:hover {
    color: #0f3d6b;
    text-decoration: none;
    background: #e5f0fa;
    border-color: #9fbedc;
    box-shadow: 0 7px 16px rgba(23, 77, 130, 0.14);
    transform: translateY(-1px);
}

.password-action-cell {
    display: flex;
    justify-content: center;
    width: 100%;
}

button {
    margin-top: 30px;
    height: 48px;
    padding: 0 28px;
    border: none;
    border-radius: 10px;
    background: #1e3a5f;
    color: white;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: .25s;
}

button:hover:not(:disabled) {
    background: #284d77;
    transform: translateY(-2px);
}

button:disabled {
    background: #8ca1ba;
    cursor: not-allowed;
}

.button-row {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 35px;
}

.button-row button:first-child {
    background: #1e3a5f;
}

.button-row button:first-child:hover:not(:disabled) {
    background: #284d77;
}

.button-row button:last-child {
    background: #d9dee6;
    color: #333;
}

.button-row button:last-child:hover {
    background: #c8d0da;
}

.error-message {
    margin-top: 20px;
    color: #d93025;
    font-weight: 600;
    text-align: center;
}

@media (max-width: 768px) {

    .mypage-card {
        padding: 24px;
    }

    .info-row {
        flex-direction: column;
        align-items: flex-start;
        gap: 10px;
    }

    .info-row strong {
        width: 100%;
    }

    .info-row input {
        width: 100%;
    }

    .business-number-inputs,
    .phone-number-inputs,
    .email-inputs {
        width: 100%;
    }

    .email-inputs {
        grid-template-columns: 1fr;
    }

    .email-inputs > span {
        display: none;
    }

    .button-row {
        flex-direction: column;
    }

    .button-row button {
        width: 100%;
    }

}

</style>
