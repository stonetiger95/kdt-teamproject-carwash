import { defineConfig } from "vite"
import vue from "@vitejs/plugin-vue"

import {
    fileURLToPath,
    URL
} from "node:url"

import {
    randomUUID
} from "node:crypto"


/*
    Vite frontend server가 시작될 때
    새로운 ID 생성

    브라우저 새로고침:
    → Vite 서버 재시작 아님
    → 같은 ID 유지

    Vite 서버 재시작:
    → vite.config.js 다시 실행
    → 새로운 ID 생성
*/
const FRONTEND_INSTANCE_ID =
    randomUUID()


export default defineConfig({

    plugins: [
        vue()
    ],


    /*
        frontend/src 경로를
        @ 로 사용할 수 있게 설정

        예:
        @/app/router
        @/features/auth/stores/authStore
    */
    resolve: {

        alias: {

            "@": fileURLToPath(
                new URL(
                    "./src",
                    import.meta.url
                )
            )
        }
    },


    /*
        main.js에서 사용할 수 있는
        frontend server instance ID

        main.js:

        __FRONTEND_INSTANCE_ID__
    */
    define: {

        __FRONTEND_INSTANCE_ID__:
            JSON.stringify(
                FRONTEND_INSTANCE_ID
            )
    }

})
