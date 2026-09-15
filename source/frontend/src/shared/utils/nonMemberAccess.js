const ACCESS_TOKEN_KEY = "nonMemberAccessToken"
const ACCESS_TOKEN_HEADER = "X-Non-Member-Access-Token"

export function saveNonMemberAccessToken(accessToken) {
    if (!accessToken) {
        clearNonMemberAccessToken()
        return
    }

    sessionStorage.setItem(
        ACCESS_TOKEN_KEY,
        accessToken
    )
}

export function clearNonMemberAccessToken() {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY)
}

export function nonMemberAccessConfig() {
    const accessToken = sessionStorage.getItem(
        ACCESS_TOKEN_KEY
    )

    return accessToken
        ? {
            headers: {
                [ACCESS_TOKEN_HEADER]: accessToken
            }
        }
        : {}
}
