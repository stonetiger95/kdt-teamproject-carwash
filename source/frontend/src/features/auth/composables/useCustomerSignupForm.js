import {
    computed,
    reactive,
    ref
} from "vue"
import {
    isValidEmailAddress,
    isValidGender,
    isValidLoginId,
    isValidPassword,
    sanitizeLoginId
} from "../utils/signupValidators"
import * as authApi from "../api/authApi"

const SIGNUP_FORM_REDIRECT_SESSION_KEY =
    "straffic.signupForm.redirect"

function consumeRedirectedGender() {
    const savedGender = sessionStorage.getItem(
        SIGNUP_FORM_REDIRECT_SESSION_KEY
    )

    sessionStorage.removeItem(
        SIGNUP_FORM_REDIRECT_SESSION_KEY
    )

    return isValidGender(savedGender) ? savedGender : ""
}

export function useCustomerSignupForm() {
    const form = reactive({
        loginId: "",
        password: "",
        gender: consumeRedirectedGender()
    })

    const confirmPassword = ref("")
    const passwordCheckAttempted = ref(false)
    const checkingLoginId = ref(false)
    const loginIdAvailable = ref(false)
    const checkedLoginId = ref("")
    const loginIdMessage = ref("")
    const emailMessage = ref("")
    const emailId = ref("")
    const emailDomainOption = ref("naver.com")
    const emailDomain = ref("")

    const selectedEmailAddress = computed(() => {
        const localPart = emailId.value.trim()
        const selectedDomain =
            emailDomainOption.value === "direct"
                ? emailDomain.value.trim()
                : emailDomainOption.value

        if (!localPart || !selectedDomain) {
            return ""
        }

        return `${localPart}@${selectedDomain}`.toLowerCase()
    })

    const passwordsMismatch = computed(() =>
        (
            confirmPassword.value.length > 0
            || passwordCheckAttempted.value
        ) && form.password !== confirmPassword.value
    )

    const isComplete = computed(() =>
        isValidLoginId(form.loginId)
        && loginIdAvailable.value
        && checkedLoginId.value === form.loginId
        && isValidPassword(form.password)
        && confirmPassword.value === form.password
        && isValidGender(form.gender)
        && isValidEmailAddress(selectedEmailAddress.value)
    )

    function updateLoginId(value) {
        const rawValue = String(value ?? "")
        const sanitizedValue = sanitizeLoginId(rawValue)

        form.loginId = sanitizedValue
        loginIdAvailable.value = false
        checkedLoginId.value = ""
        loginIdMessage.value =
            rawValue === sanitizedValue
                ? ""
                : "아이디는 영문과 숫자만 입력할 수 있습니다."
    }

    async function checkLoginId() {
        const loginId = form.loginId

        loginIdAvailable.value = false
        checkedLoginId.value = ""

        if (!isValidLoginId(loginId)) {
            loginIdMessage.value =
                "아이디는 영문과 숫자로 입력해주세요."
            return
        }

        checkingLoginId.value = true

        try {
            const available = await authApi.checkLoginId(loginId)

            if (form.loginId !== loginId) {
                return
            }

            loginIdAvailable.value = available === true
            checkedLoginId.value = available === true ? loginId : ""
            loginIdMessage.value = available === true
                ? "사용 가능한 아이디입니다."
                : "이미 사용 중인 아이디입니다."
        } catch {
            if (form.loginId === loginId) {
                loginIdMessage.value =
                    "아이디 중복 확인에 실패했습니다."
            }
        } finally {
            checkingLoginId.value = false
        }
    }

    function updatePassword(value) {
        form.password = String(value ?? "")
    }

    function updateConfirmPassword(value) {
        confirmPassword.value = String(value ?? "")
    }

    function updateGender(value) {
        form.gender = String(value ?? "")
    }

    function updateEmailId(value) {
        emailId.value = String(value ?? "")
        emailMessage.value = ""
    }

    function updateEmailDomainOption(value) {
        emailDomainOption.value = String(value ?? "")
        emailMessage.value = ""
    }

    function updateEmailDomain(value) {
        emailDomain.value = String(value ?? "")
        emailMessage.value = ""
    }

    function saveRedirectState() {
        if (isValidGender(form.gender)) {
            sessionStorage.setItem(
                SIGNUP_FORM_REDIRECT_SESSION_KEY,
                form.gender
            )
            return
        }

        clearRedirectState()
    }

    function clearRedirectState() {
        sessionStorage.removeItem(
            SIGNUP_FORM_REDIRECT_SESSION_KEY
        )
    }

    function validateEmail() {
        if (!selectedEmailAddress.value) {
            emailMessage.value = "이메일을 입력해주세요."
            return false
        }

        if (!isValidEmailAddress(selectedEmailAddress.value)) {
            emailMessage.value = "이메일 형식을 확인해주세요."
            return false
        }

        emailMessage.value = ""
        return true
    }

    function validateForSubmit() {
        passwordCheckAttempted.value = true

        if (passwordsMismatch.value) {
            return {
                valid: false,
                message: ""
            }
        }

        if (!isValidLoginId(form.loginId)) {
            return {
                valid: false,
                message: "아이디는 영문과 숫자로 입력해주세요."
            }
        }

        if (
            !loginIdAvailable.value
            || checkedLoginId.value !== form.loginId
        ) {
            return {
                valid: false,
                message: "아이디 중복 확인을 해주세요."
            }
        }

        if (!isValidPassword(form.password)) {
            return {
                valid: false,
                message: "비밀번호는 4자 이상 72자 이하로 입력해주세요."
            }
        }

        if (!isValidGender(form.gender)) {
            return {
                valid: false,
                message: "성별을 선택해주세요."
            }
        }

        if (!validateEmail()) {
            return {
                valid: false,
                message: "입력한 정보를 확인해주세요."
            }
        }

        return {
            valid: true,
            message: ""
        }
    }

    function buildSignupPayload(identityProof) {
        return {
            loginId: form.loginId,
            password: form.password,
            gender: form.gender,
            emailAddress: selectedEmailAddress.value,
            identityVerificationId:
                identityProof.identityVerificationId,
            identityProof: identityProof.identityProof,
            identityStateToken: identityProof.identityStateToken
        }
    }

    return {
        form,
        confirmPassword,
        checkingLoginId,
        loginIdAvailable,
        loginIdMessage,
        emailMessage,
        emailId,
        emailDomainOption,
        emailDomain,
        passwordsMismatch,
        isComplete,
        updateLoginId,
        checkLoginId,
        updatePassword,
        updateConfirmPassword,
        updateGender,
        updateEmailId,
        updateEmailDomainOption,
        updateEmailDomain,
        saveRedirectState,
        clearRedirectState,
        validateForSubmit,
        buildSignupPayload
    }
}
