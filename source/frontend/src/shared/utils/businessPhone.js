export function businessPhoneDigits(value) {
  return String(value ?? "").replace(/\D/g, "").slice(0, 11)
}

export function isValidBusinessPhone(value) {
  const digits = businessPhoneDigits(value)
  return /^(?:02\d{7,8}|0\d{9,10})$/.test(digits)
}

export function formatBusinessPhone(value) {
  const digits = businessPhoneDigits(value)

  if (!digits) return ""

  const firstLength = digits.startsWith("02") ? 2 : 3
  if (digits.length <= firstLength) return digits

  const remainingLength = digits.length - firstLength
  const middleLength = remainingLength > 7 ? 4 : 3
  const first = digits.slice(0, firstLength)
  const middle = digits.slice(firstLength, firstLength + middleLength)
  const last = digits.slice(firstLength + middleLength)

  return [first, middle, last].filter(Boolean).join("-")
}
