import axiosClient from "@/shared/api/axiosClient"


export function getMyPage() {

    return axiosClient.get(
        "/api/owner/mypage"
    )
}

export function updateMyPage(myPageInfo) {

    return axiosClient.put(
        "/api/owner/mypage",
        myPageInfo
    )
}

export function changePassword(passwordInfo) {

    return axiosClient.put(
        "/api/owner/password",
        passwordInfo
    )
}