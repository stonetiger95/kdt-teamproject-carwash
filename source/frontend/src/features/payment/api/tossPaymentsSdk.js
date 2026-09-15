const TOSS_SDK_URL = "https://js.tosspayments.com/v2/standard"

let sdkPromise = null

export async function requestTossPayment(paymentInfo) {
    await loadTossPaymentsSdk()

    const tossPayments = window.TossPayments(
        paymentInfo.providerClientKey
    )
    const payment = tossPayments.payment({
        customerKey: window.TossPayments.ANONYMOUS || "ANONYMOUS"
    })

    await payment.requestPayment({
        method: "CARD",
        amount: {
            currency: "KRW",
            value: Number(paymentInfo.amount)
        },
        orderId: paymentInfo.orderId,
        orderName: paymentInfo.orderName,
        successUrl: paymentInfo.successUrl,
        failUrl: paymentInfo.failUrl,
        customerEmail: paymentInfo.customerEmail || undefined,
        customerName: paymentInfo.customerName || undefined,
        customerMobilePhone: paymentInfo.recvPhone || undefined
    })
}

function loadTossPaymentsSdk() {
    if (window.TossPayments) {
        return Promise.resolve()
    }
    if (sdkPromise) {
        return sdkPromise
    }

    sdkPromise = new Promise((resolve, reject) => {
        const script = document.createElement("script")
        script.src = TOSS_SDK_URL
        script.async = true
        script.onload = resolve
        script.onerror = () => {
            sdkPromise = null
            reject(new Error("토스페이먼츠 결제 모듈을 불러오지 못했습니다."))
        }
        document.head.appendChild(script)
    })

    return sdkPromise
}
