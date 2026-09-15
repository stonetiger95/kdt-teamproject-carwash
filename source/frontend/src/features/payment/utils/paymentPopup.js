const POPUP_WIDTH = 520
const POPUP_HEIGHT = 760

export function openPaymentPopup() {
    const left = Math.max(
        0,
        Math.round(window.screenX + (window.outerWidth - POPUP_WIDTH) / 2)
    )
    const top = Math.max(
        0,
        Math.round(window.screenY + (window.outerHeight - POPUP_HEIGHT) / 2)
    )
    const features = [
        "popup=yes",
        "resizable=yes",
        "scrollbars=yes",
        `width=${POPUP_WIDTH}`,
        `height=${POPUP_HEIGHT}`,
        `left=${left}`,
        `top=${top}`
    ].join(",")

    const popup = window.open("about:blank", `payment-${Date.now()}`, features)
    popup?.focus()
    return popup
}

export function navigatePaymentPopup(popup, payUrl) {
    if (!popup || popup.closed) {
        return false
    }

    popup.location.replace(payUrl)
    popup.focus()
    return true
}

export function closePaymentPopup(popup) {
    if (popup && !popup.closed) {
        popup.close()
    }
}

export function observePaymentPopupClose(popup, onClosed) {
    let stopped = false
    let closeHandled = false
    let timer = null

    const stop = () => {
        if (stopped) {
            return
        }
        stopped = true
        if (timer) {
            window.clearInterval(timer)
        }
        window.removeEventListener("focus", checkClosed)
        document.removeEventListener("visibilitychange", checkClosed)
    }

    const checkClosed = () => {
        if (stopped || closeHandled || !popup?.closed) {
            return
        }

        closeHandled = true
        stop()
        Promise.resolve(onClosed()).catch(() => {})
    }

    timer = window.setInterval(checkClosed, 700)
    window.addEventListener("focus", checkClosed)
    document.addEventListener("visibilitychange", checkClosed)

    return stop
}
