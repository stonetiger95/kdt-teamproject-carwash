import { defineStore } from "pinia"
import { ref } from "vue"
import * as deviceApi from "../api/deviceApi"

export const useDeviceStore = defineStore("device", () => {

    const devices = ref([])
    const device = ref(null)

    const loading = ref(false)
    const error = ref("")

    async function fetchDevices() {

        loading.value = true
        error.value = ""

        try {

            devices.value =
                await deviceApi.getDeviceList()

        } catch {

            error.value =
                "장치 목록을 불러오지 못했습니다."

        } finally {

            loading.value = false
        }
    }

    async function fetchDevice(deviceId) {

        loading.value = true
        error.value = ""

        try {

            device.value =
                await deviceApi.getDevice(deviceId)

        } catch {

            error.value =
                "장치 정보를 불러오지 못했습니다."

        } finally {

            loading.value = false
        }
    }

    async function createDevice(dto){

        await deviceApi.createDevice(dto)

    }

    async function removeDevice(deviceId) {

        await deviceApi.removeDevice(deviceId)

    }

    return {

        devices,
        device,
        loading,
        error,

        fetchDevices,
        fetchDevice,

        createDevice,
        
        removeDevice
    }

})