<template>
    <div
        class="plate-lookup"
        :class="{ compact }"
    >
        <label for="non-member-plate-last4">
            차량번호 뒤 4자리
        </label>

        <div
            class="lookup-combobox"
            @focusout="closePlateOptions"
        >
            <input
                id="non-member-plate-last4"
                v-model="plateLast4"
                type="text"
                inputmode="numeric"
                pattern="[0-9]{4}"
                maxlength="4"
                autocomplete="off"
                placeholder="예: 3456"
                aria-label="차량번호 뒤 4자리"
                aria-autocomplete="list"
                aria-controls="plate-option-list"
                :aria-expanded="plateOptionsOpen"
                @input="normalizePlateLast4Input"
                @focus="openPlateOptions"
            >

            <ul
                v-if="plateOptionsOpen"
                id="plate-option-list"
                class="plate-option-list"
                role="listbox"
            >
                <li
                    v-for="plateNumber in plateOptions"
                    :key="plateNumber"
                    role="presentation"
                >
                    <button
                        type="button"
                        role="option"
                        :aria-selected="plateNumber === selectedPlateNumber"
                        @click="selectPlateNumber(plateNumber)"
                    >
                        {{ plateNumber }}
                    </button>
                </li>
            </ul>
        </div>

        <p
            v-if="loading"
            class="lookup-status"
        >
            차량번호 확인 중
        </p>

        <p
            v-else-if="errorMessage"
            class="lookup-status error"
        >
            {{ errorMessage }}
        </p>

        <p
            v-else-if="
                searched
                && plateOptions.length === 0
                && !selectedPlateNumber
            "
            class="lookup-status"
        >
            조회 가능한 비회원 세차 내역이 없습니다.
        </p>
    </div>
</template>

<script setup>
import {
    computed,
    onMounted,
    ref
} from "vue"

import * as washHistoryApi
    from "../api/washHistoryApi"
import {
    clearNonMemberAccessToken,
    saveNonMemberAccessToken
} from "@/shared/utils/nonMemberAccess"


const props = defineProps({
    compact: {
        type: Boolean,
        default: false
    },
    initialPlateLast4: {
        type: String,
        default: ""
    },
    initialPlateNumber: {
        type: String,
        default: ""
    }
})

const emit = defineEmits([
    "selected",
    "cleared"
])

const plateLast4 = ref("")
const washHistories = ref([])
const selectedPlateNumber = ref("")
const plateOptionsOpen = ref(false)
const loading = ref(false)
const searched = ref(false)
const errorMessage = ref("")

let lookupRequestId = 0


const plateOptions = computed(() => [
    ...new Set(
        washHistories.value
            .map(history => history.plateNumber)
            .filter(Boolean)
    )
])


function digitsOnly(value) {
    return String(value ?? "")
        .replace(/\D/g, "")
        .slice(0, 4)
}


function resetLookupResult() {
    clearNonMemberAccessToken()
    selectedPlateNumber.value = ""
    plateOptionsOpen.value = false
    washHistories.value = []
    searched.value = false
    errorMessage.value = ""
    emit("cleared")
}


function normalizePlateLast4Input(event) {
    plateLast4.value = digitsOnly(
        event.target.value
    )

    resetLookupResult()

    const requestId = ++lookupRequestId

    if (plateLast4.value.length === 4) {
        searchWashHistory(
            requestId,
            plateLast4.value
        )
        return
    }

    loading.value = false
}


async function searchWashHistory(
    requestId,
    requestedPlateLast4,
    preferredPlateNumber = ""
) {
    loading.value = true

    try {
        const normalizedPreferredPlateNumber = String(preferredPlateNumber || "")
            .replace(/[\s-]/g, "")

        if (
            normalizedPreferredPlateNumber.endsWith(requestedPlateLast4)
            && /^[0-9가-힣]{3,8}\d{4}$/.test(normalizedPreferredPlateNumber)
        ) {
            searched.value = true
            await selectPlateNumber(normalizedPreferredPlateNumber)
            return
        }

        const result = await washHistoryApi
            .getNonMemberWashHistoryList(
                requestedPlateLast4
            )

        if (requestId !== lookupRequestId) {
            return
        }

        washHistories.value =
            Array.isArray(result)
                ? result
                : []

        searched.value = true

        if (
            preferredPlateNumber
            && plateOptions.value.includes(
                preferredPlateNumber
            )
        ) {
            await selectPlateNumber(
                preferredPlateNumber
            )
            return
        }

        plateOptionsOpen.value =
            plateOptions.value.length > 0

    } catch (error) {
        if (requestId !== lookupRequestId) {
            return
        }

        console.error(
            "비회원 세차 내역 조회 실패:",
            error
        )

        errorMessage.value =
            "비회원 세차 내역을 불러오지 못했습니다."

    } finally {
        if (requestId === lookupRequestId) {
            loading.value = false
        }
    }
}


async function selectPlateNumber(plateNumber) {
    loading.value = true
    errorMessage.value = ""

    try {
        const access = await washHistoryApi
            .issueNonMemberAccessToken(plateNumber)

        if (!access?.accessToken) {
            throw new Error(
                "비회원 접근 토큰을 받지 못했습니다."
            )
        }

        saveNonMemberAccessToken(access.accessToken)
        selectedPlateNumber.value = plateNumber
        plateOptionsOpen.value = false

        emit("selected", {
            plateLast4: plateLast4.value,
            plateNumber,
            washHistories: washHistories.value.filter(
                history =>
                    history.plateNumber === plateNumber
            )
        })
    } catch (error) {
        clearNonMemberAccessToken()
        selectedPlateNumber.value = ""
        plateOptionsOpen.value = false
        errorMessage.value =
            "차량번호 확인에 실패했습니다. 다시 조회해주세요."

        console.error(
            "비회원 접근 권한 발급 실패:",
            error
        )
    } finally {
        loading.value = false
    }
}


function openPlateOptions() {
    if (plateOptions.value.length > 0) {
        plateOptionsOpen.value = true
    }
}


function closePlateOptions(event) {
    if (
        event.currentTarget.contains(
            event.relatedTarget
        )
    ) {
        return
    }

    plateOptionsOpen.value = false
}


onMounted(() => {
    plateLast4.value =
        digitsOnly(props.initialPlateLast4)

    if (plateLast4.value.length !== 4) {
        return
    }

    const requestId = ++lookupRequestId

    searchWashHistory(
        requestId,
        plateLast4.value,
        props.initialPlateNumber
    )
})
</script>

<style scoped>
.plate-lookup {
    width: 100%;
}

.plate-lookup.compact {
    width: 50%;
    margin: 24px auto 0;
}

label {
    display: block;
    margin-bottom: 7px;
    color: #1e3354;
    font-size: 14px;
    font-weight: 800;
    text-align: left;
}

.lookup-combobox {
    position: relative;
}

input {
    width: 100%;
    min-height: 46px;
    padding: 0 13px;
    color: #071b3a;
    background: #ffffff;
    border: 1px solid #cbd6e6;
    border-radius: 8px;
    font: inherit;
    outline: none;
    box-sizing: border-box;
}

input:focus {
    border-color: #0f766e;
    box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}

.plate-option-list {
    position: absolute;
    z-index: 10;
    top: calc(100% + 6px);
    right: 0;
    left: 0;
    max-height: 220px;
    padding: 6px;
    margin: 0;
    overflow-y: auto;
    list-style: none;
    background: #ffffff;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    box-shadow: 0 14px 28px rgba(15, 47, 95, 0.16);
}

.plate-option-list button {
    width: 100%;
    min-height: 42px;
    padding: 0 12px;
    color: #061a3a;
    font-size: 14px;
    font-weight: 700;
    text-align: left;
    background: #ffffff;
    border: 0;
    border-radius: 6px;
    cursor: pointer;
}

.plate-option-list button:hover,
.plate-option-list button:focus {
    background: #eaf5f3;
    outline: none;
}

.lookup-status {
    margin: 10px 0 0;
    color: #52637d;
    font-size: 13px;
    line-height: 1.5;
    text-align: left;
}

.lookup-status.error {
    color: #b42318;
}

@media (max-width: 520px) {
    .plate-lookup.compact {
        width: 100%;
    }
}
</style>
