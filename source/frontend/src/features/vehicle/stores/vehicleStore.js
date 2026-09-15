import { defineStore } from "pinia"
import { ref } from "vue"
import { useAuthStore } from "@/features/auth/stores/authStore"
import * as vehicleApi from "../api/vehicleApi"

export const useVehicleStore = defineStore("vehicle", () => {
    const authStore = useAuthStore()
    const plateNumber = ref("")
    const vehicleType = ref("")
    const vehicles = ref([])

    const loading = ref(false)
    const errorMessage = ref("")

    const vehicleTypeOptions = [
        { label: "세단", value: "SEDAN" },
        { label: "SUV", value: "SUV" },
        { label: "트럭", value: "TRUCK" },
        { label: "해치백", value: "HATCHBACK" },
        { label: "승합차", value: "VAN" }
    ]

    function normalizePlateNumber(value) {
        return String(value || "")
            .replaceAll(" ", "")
            .replaceAll("-", "")
            .trim()
    }

    function validatePlateNumber(value) {
        const plateRegex = /^\d{2,3}[가-힣]\d{4}$/
        return plateRegex.test(value)
    }

    function normalizeVehicleList(response) {
        const list = Array.isArray(response)
            ? response
            : response?.content || response?.data || response?.vehicles || []

        return list.map(vehicle => ({
            ...vehicle,
            vehicleId: vehicle.vehicleId || vehicle.vehicle_id,
            plateNumber: vehicle.plateNumber || vehicle.plate_number || "",
            vehicleType: vehicle.vehicleType || vehicle.vehicle_type || "",
            vehicleStatus: vehicle.vehicleStatus || vehicle.vehicle_status || "",
            usageStatus: vehicle.usageStatus || vehicle.usage_status || "ACTIVE",
            lastWashAt: vehicle.lastWashAt || vehicle.last_wash_at || null
        }))
    }

    async function vehicleRegister() {
        errorMessage.value = ""

        if (!authStore.canUseFeature("vehicle")) {
            errorMessage.value = "정지회원은 차량을 등록할 수 없습니다."
            return false
        }

        const normalizedPlateNumber = normalizePlateNumber(plateNumber.value)

        if (!normalizedPlateNumber) {
            errorMessage.value = "차량번호를 입력하세요."
            return false
        }

        if (!validatePlateNumber(normalizedPlateNumber)) {
            errorMessage.value = "차량번호 형식이 올바르지 않습니다. 예: 127가3456"
            return false
        }

        if (!vehicleType.value) {
            errorMessage.value = "차량종류를 선택하세요."
            return false
        }

        const dto = {
            plateNumber: normalizedPlateNumber,
            vehicleType: vehicleType.value
        }

        try {
            loading.value = true
            await vehicleApi.vehicleRegister(dto)
            plateNumber.value = ""
            vehicleType.value = ""
            return true
        } catch (e) {
            errorMessage.value =
                e?.response?.data?.message || "차량 등록에 실패했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    async function vehicleList(options = {}) {
        const { silent = false } = options;

        if (!silent) {
            errorMessage.value = "";
        }

        try {
            loading.value = true;
            const res = await vehicleApi.vehicleList();
            vehicles.value = normalizeVehicleList(res);
            return vehicles.value;
        } catch (e) {
            if (!silent) {
                errorMessage.value = "차량을 불러오는 데 실패했습니다.";
            }

            return null;
        } finally {
            loading.value = false
        }
    }

    async function hasRegisteredVehicle() {
        const vehicleListResult = await vehicleList({ silent: true });

        if (!Array.isArray(vehicleListResult)) {
            return null;
        }

        return vehicleListResult.length > 0;
    }

    async function vehicleDelete(vehicleId) {
        errorMessage.value = ""

        if (!authStore.canUseFeature("vehicle")) {
            errorMessage.value = "정지회원은 차량을 삭제할 수 없습니다."
            return false
        }

        try {
            loading.value = true
            await vehicleApi.vehicleDelete(vehicleId)
            await vehicleList()
            return true
        } catch (e) {
            errorMessage.value =
                e?.response?.data?.message || "차량을 삭제하는 데 실패했습니다."
            return false
        } finally {
            loading.value = false
        }
    }

    return {
        plateNumber,
        vehicleType,
        vehicleTypeOptions,
        loading,
        errorMessage,
        vehicles,

        vehicleRegister,
        vehicleList,
        hasRegisteredVehicle,
        vehicleDelete
    }
})
