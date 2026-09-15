import { reactive } from "vue"

export function digitsOnly(value) {
    return String(value ?? "").replace(/\D/g, "")
}

export function usePhoneParts(onChange, lastInput) {
    const phoneParts = reactive({
        first: "",
        middle: "",
        last: ""
    })

    function syncPhoneValue() {
        onChange(
            phoneParts.first
            + phoneParts.middle
            + phoneParts.last
        )
    }

    function setPhoneParts(value) {
        const digits = digitsOnly(value).slice(0, 11)
        const middleEnd = digits.length === 10 ? 6 : 7

        phoneParts.first = digits.slice(0, 3)
        phoneParts.middle = digits.slice(3, middleEnd)
        phoneParts.last = digits.slice(middleEnd)
        syncPhoneValue()
    }

    function handlePhonePartInput(
        part,
        maxLength,
        event,
        nextInput = null
    ) {
        const value = digitsOnly(event.target.value).slice(0, maxLength)

        phoneParts[part] = value
        event.target.value = value
        syncPhoneValue()

        if (value.length === maxLength) {
            nextInput?.focus()
        }
    }

    function handlePhoneBackspace(part, previousInput) {
        if (!phoneParts[part]) {
            previousInput?.focus()
        }
    }

    function handlePhonePaste(event) {
        const digits = digitsOnly(
            event.clipboardData?.getData("text")
        )

        if (digits.length < 10) {
            return
        }

        event.preventDefault()
        setPhoneParts(digits)
        lastInput.value?.focus()
    }

    return {
        phoneParts,
        setPhoneParts,
        handlePhonePartInput,
        handlePhoneBackspace,
        handlePhonePaste
    }
}
