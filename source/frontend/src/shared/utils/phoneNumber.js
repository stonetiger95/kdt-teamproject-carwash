export function formatPhoneNumber(value) {
    const original = String(value ?? "").trim()
    const digits = original.replace(/\D/g, "")

    if (digits.length === 11) {
        return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
    }

    if (digits.length === 10 && digits.startsWith("02")) {
        return `${digits.slice(0, 2)}-${digits.slice(2, 6)}-${digits.slice(6)}`
    }

    if (digits.length === 10) {
        return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`
    }

    return original || "-"
}
