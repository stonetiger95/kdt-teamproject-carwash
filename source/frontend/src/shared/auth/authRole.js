import { ROLE } from "./roles"

export function getLoginRole() {

    const tokenRole = getTokenRole()

    if (tokenRole) {
        return tokenRole
    }

    return localStorage.getItem("role") || ""
}

export function getInquiryRole() {

    const role = getLoginRole()

    if (role === ROLE.ADMIN) {
        return "ADMIN"
    }

    if (role === ROLE.OWNER) {
        return "OWNER"
    }

    return "CUSTOMER"
}

export function getInquiryRouteNames() {

    const role = getLoginRole()

    if (role === ROLE.ADMIN) {
        return {
            listRouteName: "admin-inquiries",
            detailRouteName: "admin-inquiry-detail",
            detailPageRouteName: "admin-inquiry-detail-page",
            createRouteName: "",
            editRouteName: ""
        }
    }

    if (role === ROLE.OWNER) {
        return {
            listRouteName: "owner-inquiries",
            detailRouteName: "owner-inquiry-detail",
            detailPageRouteName: "owner-inquiry-detail-page",
            createRouteName: "owner-inquiry-create",
            editRouteName: "owner-inquiry-edit"
        }
    }

    return {
        listRouteName: "customer-inquiries",
        detailRouteName: "customer-inquiry-detail",
        detailPageRouteName: "customer-inquiry-detail-page",
        createRouteName: "customer-inquiry-create",
        editRouteName: "customer-inquiry-edit"
    }
}

export function canAnswerInquiry(inquiry) {

    const role = getLoginRole()

    if (!inquiry) {
        return false
    }

    if (role === ROLE.ADMIN) {
        return true
    }

    return role === ROLE.OWNER && inquiry.writerRole === "CUSTOMER"
}

export function canEditInquiry(inquiry) {

    const role = getInquiryRole()

    if (!inquiry) {
        return false
    }

    const hasAnswer = Number(inquiry.answerCount || 0) > 0
        || (Array.isArray(inquiry.answers) && inquiry.answers.length > 0)
        || Boolean(inquiry.answer?.trim())

    return role === inquiry.writerRole && !hasAnswer
}

function getTokenRole() {

    const token = localStorage.getItem("accessToken")

    if (!token) {
        return ""
    }

    try {
        const payload = token.split(".")[1]
        const base64 = payload.replace(/-/g, "+").replace(/_/g, "/")
        const decoded = decodeURIComponent(
            atob(base64)
                .split("")
                .map(char => `%${(`00${char.charCodeAt(0).toString(16)}`).slice(-2)}`)
                .join("")
        )

        return JSON.parse(decoded).role || ""
    } catch (e) {
        return ""
    }
}
