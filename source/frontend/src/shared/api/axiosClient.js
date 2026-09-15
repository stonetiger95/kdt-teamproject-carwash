//axios => interceptor 시스템이 있음
import axios from "axios" //axios 라이브러리 들여옴

//spring에 전송될 url 중 기본 url 생성 및 axios 오브젝트 생성
const axiosClient = axios.create({ //axios object 만들기
    baseURL: import.meta.env.VITE_BACKEND_URL, // .env.VITE_BACKEND_URL : 파일안에 들어있는 주소를 불러옴 // 주소 : http://localhost
    timeout: 10000, // 10초 안에 스프링이 응답안하면 에러보내겠다. 10,000 밀리세컨드
    headers: { // 헤더 : 추가적인 정보(HTTP request와 함께 가는)
        "Content-Type": "application/json" // 스프링부트(백앤드)에 보내는 데이터가 JSON 형태라고 알려줌 
    }
})

function isPublicRequest(url) {
    const requestUrl = String(url || "")
    return requestUrl.startsWith("/api/auth/")
        || requestUrl.startsWith("/api/public/")
}

//spring에 전송될 것들 정리, api에 있는 function 보다 먼저 실행(axiosClient.get, axiosClient.post)
axiosClient.interceptors.request.use( //interceptor : config를 받고 수정(Axios에서 데이터 발송되기 전에 실행)

    config => { //config(Axios에서 스프링으로 보낼 HTTP request(쉽게 말해 토큰 데이터)) 생성

        const token = localStorage.getItem("accessToken") //browser 안에 localStorage를 보고 토큰에 접근 // 로그인 상태 : 토큰 밸류 접근 가능, 비로그인 상태 : 토큰 = null

        const publicRequest = isPublicRequest(config.url)

        if (publicRequest) {
            if (typeof config.headers.delete === "function") { //typeof : check the JavaScript type of a value: // 항상 true
                config.headers.delete("Authorization") //JWT 제거(axios header) -> header에 delete 메서드 있음
            } else {
                delete config.headers.Authorization //JWT 제거(중복이지만 혹시 모를 상황에 대비)
            }
        } else if (token) { //보호 API 요청에만 JWT 추가
            config.headers.Authorization = `Bearer ${token}` // 헤더에 JWT authentication 스프링에서 쓸 토큰 정보를 추가 
        }

        return config //보낼 내용 확정
    },

    error => Promise.reject(error) //request 보내기전에 에러나면 에러를 실행

)

axiosClient.interceptors.response.use(//successHandler or errorHandler (스프링이 보낸 파일 검수 또는 백앤드 에러로 request가 response 받지 못할때)

    response => response.data, // successHandler // Spring 응답 객체에서 실제 응답 데이터(body)만 꺼내서 API 호출한 곳에 전달

    error => { //에러시 실행(1. 백앤드 400, 401, 403, 500)

        const status = error.response?.status //?. 
        const token = localStorage.getItem("accessToken")
        const publicRequest = isPublicRequest(
            error.config?.url
        )
        const authenticationWasLost =
            Boolean(token)
            && !publicRequest
            && status === 401

        if (authenticationWasLost) {

            localStorage.removeItem("accessToken") //스토리지 데이터를 지워라
            localStorage.removeItem("loginId")
            localStorage.removeItem("userName")
            localStorage.removeItem("role")
            localStorage.removeItem("customerId")
            localStorage.removeItem("ownerId")
            localStorage.removeItem("customerStatus")

            window.dispatchEvent(new CustomEvent(
                "auth:unauthorized",
                {
                    detail: {
                        code: error.response?.data?.code || "",
                        message: error.response?.data?.message || ""
                    }
                }
            ))
        }

        return Promise.reject(error) //error를 연결된 vue에 전달
    }

)

export default axiosClient //다른 파일에서 쓸 수 있게 설정
