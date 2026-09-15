import axiosClient from "@/shared/api/axiosClient"

export function getInquiryList() {
    return axiosClient.get("/api/inquiries")
}

export function getInquiry(inquiryId) {
    return axiosClient.get(`/api/inquiries/${inquiryId}`)
}

export function getUsedWashSites() {
    return axiosClient.get("/api/inquiries/used-wash-sites")
}

export function createInquiry(inquiryInfo) {
    return axiosClient.post("/api/inquiries", inquiryInfo)
}

export function updateInquiry(inquiryId, inquiryInfo) {
    return axiosClient.put(`/api/inquiries/${inquiryId}`, inquiryInfo)
}

export function answerInquiry(inquiryId, answerInfo) {
    return axiosClient.patch(`/api/inquiries/${inquiryId}/answer`, answerInfo)
}

export function refundInquiry(inquiryId, refundInfo) {
    return axiosClient.post(`/api/inquiries/${inquiryId}/refund`, refundInfo)
}

export function deleteInquiry(inquiryId) {
    return axiosClient.delete(`/api/inquiries/${inquiryId}`)
}
