import { onBeforeUnmount, onMounted, ref } from 'vue'

const ADDRESS_SELECTED_MESSAGE = 'STRAFFIC_JUSO_ADDRESS_SELECTED'
const ADDRESS_ERROR_MESSAGE = 'STRAFFIC_JUSO_ADDRESS_ERROR'
const POPUP_NAME = 'strafficJusoAddressSearch'

function backendBaseUrl() {
  return String(import.meta.env.VITE_BACKEND_URL || window.location.origin).replace(/\/+$/, '')
}

export function useJusoAddressPopup(onAddressSelected) {
  const errorMessage = ref('')
  const baseUrl = backendBaseUrl()
  const expectedOrigin = new URL(baseUrl, window.location.origin).origin
  let popupWindow = null

  function handleMessage(event) {
    if (event.origin !== expectedOrigin || !event.data || typeof event.data !== 'object') return
    const message = event.data

    if (message.type === ADDRESS_ERROR_MESSAGE) {
      errorMessage.value = message.payload?.message || '선택한 주소를 확인할 수 없습니다.'
      return
    }
    if (message.type !== ADDRESS_SELECTED_MESSAGE) return

    const address = message.payload || {}
    const roadAddress = String(address.roadAddrPart1 || address.roadFullAddr || '').trim()
    if (!roadAddress) {
      errorMessage.value = '선택한 도로명주소가 없습니다.'
      return
    }

    errorMessage.value = ''
    onAddressSelected?.({ ...address, roadAddrPart1: roadAddress })
  }

  function openJusoAddressSearch() {
    errorMessage.value = ''
    popupWindow = window.open(
      `${baseUrl}/api/public/address/juso/popup`,
      POPUP_NAME,
      'width=570,height=720,scrollbars=yes,resizable=yes',
    )
    if (!popupWindow) {
      errorMessage.value = '주소 검색 팝업이 차단되었습니다. 팝업을 허용해 주세요.'
      return
    }
    popupWindow.focus()
  }

  onMounted(() => window.addEventListener('message', handleMessage))
  onBeforeUnmount(() => {
    window.removeEventListener('message', handleMessage)
    if (popupWindow && !popupWindow.closed) popupWindow.close()
  })

  return { errorMessage, openJusoAddressSearch }
}

