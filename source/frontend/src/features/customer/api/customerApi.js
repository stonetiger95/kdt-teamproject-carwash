import axiosClient from "@/shared/api/axiosClient"


export function getMyPage() {

    return axiosClient.get(
        "/api/customer/mypage"
    )
}

export function updateMyPage(myPageInfo) {

    return axiosClient.put(
        "/api/customer/mypage",
        myPageInfo
    )
}

export function startEmailChange(emailAddress) {

    return axiosClient.post(
        "/api/customer/mypage/email-change/start",
        {
            emailAddress
        }
    )
}

export function verifyEmailChange(challengeId, code) {

    return axiosClient.post(
        "/api/customer/mypage/email-change/verify",
        {
            challengeId,
            code
        }
    )
}

export function changePassword(passwordInfo) {

    return axiosClient.put(
        "/api/customer/password",
        passwordInfo
    )
}

export function withdrawAccount(currentPassword) {
    return axiosClient.patch(
        "/api/customer/withdraw",
        {
            currentPassword
        }
    )
}
