import axiosClient from "@/shared/api/axiosClient"
import {
    nonMemberAccessConfig
} from "@/shared/utils/nonMemberAccess"

export function getCheckoutDefault() {
    return axiosClient.get("/api/payments/checkout-default")
}

export function waitingPayment(paymentInfo) {
    return axiosClient.post("/api/payments/waiting", paymentInfo)
}

export function abandonPrepaidCharge(paymentId) {
    return axiosClient.post(`/api/prepaid/charges/${paymentId}/abandon`)
}

export function getPaymentList(searchCondition = {}) {
    return axiosClient.get("/api/payments", {
        params: searchCondition
    })
}

export function getPaymentProviders() {
    return axiosClient.get("/api/public/payments/providers")
}

export function getPayment(paymentId) {
    return axiosClient.get(`/api/payments/${paymentId}`)
}

export function getWallet() {
    return axiosClient.get("/api/prepaid/wallet")
}

export function getBillingCards() {
    return axiosClient.get("/api/prepaid/billing-cards")
}

export function registerBillingCard(cardInfo) {
    return axiosClient.post("/api/prepaid/billing-cards", cardInfo)
}

export function registerTestBillingCard(cardInfo) {
    return axiosClient.post("/api/prepaid/billing-cards/test", cardInfo)
}

export function createPayAppBillingAuthRequest(cardInfo) {
    return axiosClient.post(
        "/api/prepaid/billing-cards/payapp/request",
        cardInfo
    )
}

export function deleteBillingCard(payAppCardId) {
    return axiosClient.delete(`/api/prepaid/billing-cards/${payAppCardId}`)
}

export function cancelPayment(paymentId, cancelInfo) {
    return axiosClient.post(`/api/payments/${paymentId}/cancel`, cancelInfo)
}

export function getNonMemberCheckoutDefault(
    washHistoryId
) {
    return axiosClient.get(
        `/api/public/payments/checkout-default/${washHistoryId}`,
        nonMemberAccessConfig()
    )
}

export function waitingNonMemberPayment(
    paymentInfo
) {
    return axiosClient.post(
        "/api/public/payments/waiting",
        paymentInfo,
        nonMemberAccessConfig()
    )
}

export function abandonNonMemberPayment(
    paymentId,
    washHistoryId
) {
    const accessConfig = nonMemberAccessConfig()

    return axiosClient.post(
        `/api/public/payments/${paymentId}/abandon`,
        null,
        {
            ...accessConfig,
            params: {
                washHistoryId
            }
        }
    )
}

export function getNonMemberPaymentList(
    washHistoryId
) {
    return axiosClient.get(
        `/api/public/payments/wash-history/${washHistoryId}`,
        nonMemberAccessConfig()
    )
}
