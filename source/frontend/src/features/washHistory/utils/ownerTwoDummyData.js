const START_DATE = "2026-06-01"
const END_DATE = "2026-07-24"

const plates = [
    "12가 3456", "38나 7291", "67다 1843", "24라 9087", "91마 5620",
    "45바 1379", "73사 6812", "16아 4528", "82자 3094", "29차 7741",
    "54카 2186", "88타 6035", "31파 9472", "65하 1268", "19거 5384"
]
const sites = [
    { washSiteId: 1, siteName: "오토워시 강남점" },
    { washSiteId: 2, siteName: "오토워시 서초점" }
]

export function createOwnerTwoWashHistories() {
    const histories = []
    const current = parseDate(START_DATE)
    const end = parseDate(END_DATE)
    let id = 1

    while (current <= end) {
        const date = toDateKey(current)
        const seed = seededNumber(date)
        const dailyCount = 8 + seed % 10

        for (let index = 0; index < dailyCount; index += 1) {
            const amount = 12000 + ((seed + index * 3) % 8) * 1000
            const hour = 8 + (seed + index * 2) % 13
            const minute = (seed + index * 17) % 60
            const duration = 18 + (seed + index * 7) % 24
            const entry = new Date(current.getFullYear(), current.getMonth(), current.getDate(), hour, minute)
            const exit = new Date(entry.getTime() + duration * 60 * 1000)
            const site = sites[(seed + index) % sites.length]

            histories.push({
                washHistoryId: `owner-2-dummy-${id}`,
                ...site,
                plateNumber: plates[(seed + index) % plates.length],
                customerType: (seed + index) % 4 === 0 ? "NON_MEMBER" : "MEMBER",
                courseName: amount >= 18000
                    ? "프리미엄 세차"
                    : amount >= 15000 ? "스탠다드 세차" : "기본 세차",
                amount,
                entryTime: toDateTime(entry),
                exitTime: toDateTime(exit),
                washStatus: "WASH_DONE",
                paymentStatus: "SUCCESS"
            })
            id += 1
        }

        current.setDate(current.getDate() + 1)
    }

    return histories.sort((left, right) => right.entryTime.localeCompare(left.entryTime))
}

export function createOwnerTwoSettlementDashboard(params = {}) {
    const startDate = params.startDate || START_DATE
    const endDate = params.endDate || END_DATE
    const histories = filterOwnerTwoWashHistories({ startDate, endDate })
    const groups = new Map()

    histories.forEach(history => {
        const date = history.entryTime.slice(0, 10)
        const periodLabel = params.periodType === "MONTHLY" ? date.slice(0, 7) : date
        const point = groups.get(periodLabel) || {
            periodLabel,
            totalUseCount: 0,
            totalPaidAmount: 0,
            memberUseCount: 0,
            nonMemberUseCount: 0
        }
        point.totalUseCount += 1
        point.totalPaidAmount += history.amount
        point.memberUseCount += history.customerType === "MEMBER" ? 1 : 0
        point.nonMemberUseCount += history.customerType === "NON_MEMBER" ? 1 : 0
        groups.set(periodLabel, point)
    })

    const totalPaidAmount = histories.reduce((sum, history) => sum + history.amount, 0)
    const memberUseCount = histories.filter(history => history.customerType === "MEMBER").length

    return {
        startDate,
        endDate,
        totalUseCount: histories.length,
        uniqueCustomerCount: new Set(histories.map(history => history.plateNumber)).size,
        totalUseAmount: totalPaidAmount,
        totalPaidAmount,
        memberUseCount,
        nonMemberUseCount: histories.length - memberUseCount,
        waitingPaymentCount: 0,
        trends: [...groups.values()].sort((left, right) => left.periodLabel.localeCompare(right.periodLabel))
    }
}

export function filterOwnerTwoWashHistories(params = {}) {
    return createOwnerTwoWashHistories().filter(history => {
        const date = history.entryTime.slice(0, 10)
        return (!params.startDate || date >= params.startDate)
            && (!params.endDate || date <= params.endDate)
            && (!params.washSiteId || Number(history.washSiteId) === Number(params.washSiteId))
            && (!params.plateNumber || history.plateNumber.includes(params.plateNumber))
            && (!params.customerType || history.customerType === params.customerType)
            && (!params.washStatus || history.washStatus === params.washStatus)
            && (!params.paymentStatus || history.paymentStatus === params.paymentStatus)
    })
}

function seededNumber(value) {
    return String(value).split("").reduce((seed, character) => {
        return (seed * 31 + character.charCodeAt(0)) >>> 0
    }, 2166136261)
}

function parseDate(value) {
    const [year, month, day] = value.split("-").map(Number)
    return new Date(year, month - 1, day)
}

function toDateKey(date) {
    return [
        date.getFullYear(),
        String(date.getMonth() + 1).padStart(2, "0"),
        String(date.getDate()).padStart(2, "0")
    ].join("-")
}

function toDateTime(date) {
    return `${toDateKey(date)}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}:00`
}
