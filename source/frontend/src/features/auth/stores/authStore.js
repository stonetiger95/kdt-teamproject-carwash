import { defineStore } from "pinia"
import { computed, ref } from "vue"
import * as authApi from "../api/authApi"

export const useAuthStore = defineStore("auth", () => {
    
    const loginErrorCode = ref("")

    const accessToken = ref(localStorage.getItem("accessToken") || "")

    const loginId = ref(localStorage.getItem("loginId") || "")

    const userName = ref(localStorage.getItem("userName") || "")

    const role = ref(localStorage.getItem("role") || "")

    const customerId = ref(localStorage.getItem("customerId") || "")

    const ownerId = ref(localStorage.getItem("ownerId") || "")

    const customerStatus = ref(localStorage.getItem("customerStatus") || "")

    const needLogin = computed(() => !accessToken.value)

    const isSuspended = computed(() =>
        role.value === "ROLE_CUSTOMER"
        && customerStatus.value === "SUSPENDED"
    )

    const suspendedAllowedFeatures = new Set([
        "inquiry",
        "history",
    ])

    function canUseFeature(feature) {
        if (!isSuspended.value) {
            return true
        }

        return suspendedAllowedFeatures.has(feature)
    }

    function clearAuth() {
        loginErrorCode.value = ""
        accessToken.value = ""
        loginId.value = ""
        userName.value = ""
        role.value = ""
        customerId.value = ""
        ownerId.value = ""
        customerStatus.value = ""


        localStorage.removeItem(
            "accessToken"
        )

        localStorage.removeItem(
            "loginId"
        )

        localStorage.removeItem(
            "userName"
        )

        localStorage.removeItem(
            "role"
        )

        localStorage.removeItem(
            "customerId"
        )

        localStorage.removeItem(
            "ownerId"
        )
        localStorage.removeItem(
            "customerStatus"
        )
    }

    function applyAuthResponse(res) {
        accessToken.value = res.accessToken
        loginId.value = res.loginId
        userName.value = res.userName
        role.value = res.role
        customerId.value = res.customerId || ""
        ownerId.value = res.ownerId || ""
        customerStatus.value = res.customerStatus || ""

        localStorage.setItem("accessToken", accessToken.value)
        localStorage.setItem("loginId", loginId.value)
        localStorage.setItem("userName", userName.value)
        localStorage.setItem("role", role.value)
        localStorage.setItem("customerId", customerId.value)
        localStorage.setItem("ownerId", ownerId.value)
        localStorage.setItem("customerStatus", customerStatus.value)
    }

    function applyLoginResponse(response) {
        applyAuthResponse(response)
    }

    function updateUserName(name) {
        const nextUserName = String(name || "").trim()

        userName.value = nextUserName

        if (nextUserName) {
            localStorage.setItem("userName", nextUserName)
            return
        }

        localStorage.removeItem("userName")
    }

    async function login(loginInfo) {
        loginErrorCode.value = ""
        try {

            const res = await authApi.login(loginInfo)
            applyAuthResponse(res)

            return true

        } catch (e) {
            loginErrorCode.value =
            e.response?.data?.code || ""
            return false
        }
    }

    async function logout() {

        try {
            await authApi.logout()
        } catch (e) {
            // Ignore API errors and continue logout
        }

        clearAuth()
    }

    async function signup(signupInfo) {
        try {
            const res = await authApi.signup(signupInfo)
            applyAuthResponse(res)

            return {
                success: true,
                status: 201,
                message: ""
            }

        } catch (e) {

            const status = e.response?.status || 0

            const responseMessage =
                typeof e.response?.data === "string"
                    ? e.response.data
                    : e.response?.data?.message

            return {
                success: false,
                status,
                message:
                    responseMessage ||
                    (
                        status === 403
                            ? "서버가 재시작 중입니다. 잠시 후 다시 시도해주세요."
                            : "회원가입에 실패했습니다."
                    )
            }
        }
    }
    return {
        accessToken,
        loginId,
        userName,
        role,
        customerId,
        ownerId,
        needLogin,
        loginErrorCode,
        customerStatus,
        isSuspended,
        canUseFeature,
        applyLoginResponse,
        updateUserName,

        clearAuth,

        login,
        logout,
        signup
    }

})
