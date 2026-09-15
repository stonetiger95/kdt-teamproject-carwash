import { defineStore } from "pinia"
import { ref } from "vue"
import { useAuthStore } from "@/features/auth/stores/authStore"
import * as paymentApi from "../api/paymentApi"

export const usePaymentStore = defineStore("payment", () => {

    const authStore = useAuthStore()

    const payments = ref([])
    const payment = ref(null)
    const waitingInfo = ref(null)
    const checkoutDefault = ref(null)
    const wallet = ref(null)
    const billingCards = ref([])
    const loading = ref(false)
    const error = ref("")

    function canUsePayment() {
        if (authStore.canUseFeature("payment")) {
            return true
        }

        error.value = "정지회원은 결제 기능을 이용할 수 없습니다."
        return false
    }

    async function fetchCheckoutDefault() {

        if (!canUsePayment()) {
            return null
        }

        loading.value = true
        error.value = ""

        try {
            checkoutDefault.value = await paymentApi.getCheckoutDefault()
            return checkoutDefault.value
        } catch (e) {
            error.value = "결제 대상 정보를 불러오지 못했습니다."
            return null
        } finally {
            loading.value = false
        }
    }

    async function fetchNonMemberCheckoutDefault(washHistoryId) {
        loading.value = true
        error.value = ""
        checkoutDefault.value = null

        try {
            checkoutDefault.value =
                await paymentApi.getNonMemberCheckoutDefault(
                    washHistoryId
                )

            return checkoutDefault.value

        } catch (e) {
            console.error(
                "비회원 결제 대상 조회 실패:",
                e
            )

            error.value =
                e?.response?.data?.message
                || "결제 대상 정보를 불러오지 못했습니다."

            return null

        } finally {
            loading.value = false
        }
    }

    async function waitingPayment(paymentInfo) {

        if (!canUsePayment()) {
            return null
        }

        loading.value = true
        error.value = ""

        try {
            waitingInfo.value = await paymentApi.waitingPayment(paymentInfo)
            return waitingInfo.value
        } catch (e) {
            error.value = e?.response?.data?.message || "결제 요청에 실패했습니다."
            return null
        } finally {
            loading.value = false
        }
    }

    async function waitingNonMemberPayment(paymentInfo) {
        loading.value = true
        error.value = ""
        waitingInfo.value = null

        try {
            waitingInfo.value =
                await paymentApi.waitingNonMemberPayment(
                    paymentInfo
                )

            return waitingInfo.value

        } catch (e) {
            console.error(
                "비회원 결제 요청 실패:",
                e
            )

            error.value =
                e?.response?.data?.message
                || "결제 요청에 실패했습니다."

            return null

        } finally {
            loading.value = false
        }
    }

    async function fetchPayment(paymentId) {

        loading.value = true
        error.value = ""

        try {
            payment.value = await paymentApi.getPayment(paymentId)
            return payment.value
        } catch (e) {
            error.value = "결제 상태를 불러오지 못했습니다."
            return null
        } finally {
            loading.value = false
        }
    }

    async function fetchWallet(options = {}) {
        if (!options.silent) {
            loading.value = true
        }
        error.value = ""

        try {
            wallet.value = await paymentApi.getWallet()
            return wallet.value
        } catch (e) {
            error.value = "지갑 정보를 불러오지 못했습니다."
            return null
        } finally {
            if (!options.silent) {
                loading.value = false
            }
        }
    }

    async function fetchBillingCards(options = {}) {
        if (!options.silent) {
            loading.value = true
        }
        error.value = ""

        try {
            billingCards.value = await paymentApi.getBillingCards()
            return billingCards.value
        } catch (e) {
            error.value = "등록된 카드 정보를 불러오지 못했습니다."
            return []
        } finally {
            if (!options.silent) {
                loading.value = false
            }
        }
    }

    async function registerBillingCard(cardInfo) {
        if (!canUsePayment()) {
            return null
        }

        loading.value = true
        error.value = ""

        try {
            const billingCard = await paymentApi.registerBillingCard(cardInfo)
            await fetchBillingCards({ silent: true })
            return billingCard
        } catch (e) {
            error.value =
                e?.response?.data?.message
                || "카드 등록에 실패했습니다."
            return null
        } finally {
            loading.value = false
        }
    }

    async function registerTestBillingCard(cardInfo) {
        if (!canUsePayment()) {
            return null
        }

        loading.value = true
        error.value = ""

        try {
            const billingCard = await paymentApi.registerTestBillingCard(cardInfo)
            await fetchBillingCards({ silent: true })
            return billingCard
        } catch (e) {
            error.value =
                e?.response?.data?.message
                || "테스트카드 등록에 실패했습니다."
            return null
        } finally {
            loading.value = false
        }
    }

    async function createPayAppBillingAuthRequest(cardInfo) {
        if (!canUsePayment()) {
            return null
        }

        loading.value = true
        error.value = ""

        try {
            return await paymentApi.createPayAppBillingAuthRequest(cardInfo)
        } catch (e) {
            error.value =
                e?.response?.data?.message
                || "PayApp card auth request failed."
            return null
        } finally {
            loading.value = false
        }
    }

    async function deleteBillingCard(payAppCardId) {
        if (!canUsePayment()) {
            return false
        }

        loading.value = true
        error.value = ""

        try {
            await paymentApi.deleteBillingCard(payAppCardId)
            await fetchBillingCards({ silent: true })
            return true
        } catch (e) {
            error.value =
                e?.response?.data?.message
                || "카드 삭제에 실패했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    async function fetchPaymentList(options = {}) {

        const searchCondition = options.searchCondition || {}

        if (!options.silent) {
            loading.value = true
        }
        error.value = ""

        try {
            payments.value = await paymentApi.getPaymentList(
                searchCondition
            )
        } catch (e) {
            error.value = "결제 내역을 불러오지 못했습니다."
        } finally {
            if (!options.silent) {
                loading.value = false
            }
        }
    }

    async function cancelPayment(paymentId, cancelInfo) {

        if (!canUsePayment()) {
            return false
        }

        try {
            const result = await paymentApi.cancelPayment(paymentId, cancelInfo)

            if (result === 0) {
                error.value = "결제를 취소할 수 없습니다."
                return false
            }

            return true
        } catch (e) {
            error.value = "결제 취소에 실패했습니다."
            return false
        }
    }

    return {
        payments,
        payment,
        waitingInfo,
        checkoutDefault,
        wallet,
        billingCards,
        loading,
        error,
        fetchCheckoutDefault,
        waitingPayment,
        fetchPayment,
        fetchWallet,
        fetchBillingCards,
        registerBillingCard,
        registerTestBillingCard,
        createPayAppBillingAuthRequest,
        deleteBillingCard,
        fetchPaymentList,
        cancelPayment,

        fetchNonMemberCheckoutDefault,
        waitingNonMemberPayment
    }

})
