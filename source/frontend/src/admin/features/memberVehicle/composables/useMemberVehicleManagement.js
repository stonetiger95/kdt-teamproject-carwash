import { reactive, ref } from 'vue'
import * as api from '@/admin/features/memberVehicle/api/memberVehicleApi'

function pageInfo(result, fallbackSize = 10) {
  return { page: Number(result?.page || 1), size: Number(result?.size || fallbackSize), totalElements: Number(result?.totalElements || 0), totalPages: Number(result?.totalPages || 0), hasNext: Boolean(result?.hasNext), hasPrevious: Boolean(result?.hasPrevious) }
}
function errorMessage(error, fallback) {
  const data = error?.response?.data
  const raw = (typeof data === 'string' && data) || data?.message || data?.detail || fallback
  if (data?.code === 'DUPLICATE_VALUE' || /customer_phone_key/i.test(raw)) {
    return '이미 사용 중인 전화번호입니다. 다른 전화번호를 입력해 주세요.'
  }
  if (/duplicate key|중복된 키|고유 제약|SQLSTATE|PSQLException|org\.postgresql/i.test(raw)) {
    return '이미 등록된 정보와 중복됩니다. 입력값을 확인해 주세요.'
  }
  return raw
}

export function useMemberVehicleManagement() {
  const members = ref([])
  const vehicles = ref([])
  const memberDetail = ref(null)
  const vehicleDetail = ref(null)
  const memberPage = reactive(pageInfo(null))
  const vehiclePage = reactive(pageInfo(null))
  const loading = ref(false)
  const detailLoading = ref(false)
  const actionLoading = ref(false)
  const error = ref('')
  const successMessage = ref('')

  function clearMessages() {
    error.value = ''
    successMessage.value = ''
  }
  async function loadMembers(params = {}) {
    loading.value = true
    clearMessages()
    try {
      const result = await api.getMembers({ page: memberPage.page, size: memberPage.size, ...params })
      members.value = Array.isArray(result?.content) ? result.content : []
      Object.assign(memberPage, pageInfo(result, memberPage.size))
    } catch (requestError) {
      members.value = []
      error.value = errorMessage(requestError, '회원 목록을 불러오지 못했습니다.')
    } finally { loading.value = false }
  }

  async function loadVehicles(params = {}) {
    loading.value = true
    clearMessages()
    try {
      const result = await api.getVehicles({ page: vehiclePage.page, size: vehiclePage.size, ...params })
      vehicles.value = Array.isArray(result?.content) ? result.content : []
      Object.assign(vehiclePage, pageInfo(result, vehiclePage.size))
    } catch (requestError) {
      vehicles.value = []
      error.value = errorMessage(requestError, '차량 목록을 불러오지 못했습니다.')
    } finally { loading.value = false }
  }

  async function loadMemberDetail(customerId) {
    detailLoading.value = true
    clearMessages()
    try { memberDetail.value = await api.getMember(customerId); return true }
    catch (requestError) { memberDetail.value = null; error.value = errorMessage(requestError, '회원 상세 정보를 불러오지 못했습니다.'); return false }
    finally { detailLoading.value = false }
  }

  async function loadVehicleDetail(plateNumber) {
    detailLoading.value = true
    clearMessages()
    try { vehicleDetail.value = await api.getVehicle(plateNumber); return true }
    catch (requestError) { vehicleDetail.value = null; error.value = errorMessage(requestError, '차량 상세 정보를 불러오지 못했습니다.'); return false }
    finally { detailLoading.value = false }
  }

  async function saveMember(payload, searchParams) {
    if (!memberDetail.value?.customerId) return false
    actionLoading.value = true
    clearMessages()
    try {
      memberDetail.value = await api.updateMember(memberDetail.value.customerId, payload)
      await loadMembers(searchParams)
      successMessage.value = '회원 정보가 저장되었습니다.'
      return true
    } catch (requestError) { error.value = errorMessage(requestError, '회원 정보를 저장하지 못했습니다.'); return false }
    finally { actionLoading.value = false }
  }

  async function registerBlacklist(plateNumber, reason, vehicleSearch) {
    actionLoading.value = true
    clearMessages()
    try {
      await api.addVehicleBlacklist(plateNumber, reason.trim())
      if (vehicleDetail.value?.plateNumber === plateNumber) vehicleDetail.value = await api.getVehicle(plateNumber)
      if (memberDetail.value?.customerId) memberDetail.value = await api.getMember(memberDetail.value.customerId)
      await loadVehicles(vehicleSearch)
      successMessage.value = `${plateNumber} 차량을 블랙리스트에 등록했습니다.`
      return true
    } catch (requestError) { error.value = errorMessage(requestError, '차량 블랙리스트 등록에 실패했습니다.'); return false }
    finally { actionLoading.value = false }
  }

  async function releaseBlacklist(plateNumber, vehicleSearch) {
    actionLoading.value = true
    clearMessages()
    try {
      await api.releaseVehicleBlacklist(plateNumber)
      if (vehicleDetail.value?.plateNumber === plateNumber) vehicleDetail.value = await api.getVehicle(plateNumber)
      if (memberDetail.value?.customerId) memberDetail.value = await api.getMember(memberDetail.value.customerId)
      await loadVehicles(vehicleSearch)
      successMessage.value = `${plateNumber} 차량의 수동 차단을 해제했습니다.`
      return true
    } catch (requestError) { error.value = errorMessage(requestError, '차량 블랙리스트 해제에 실패했습니다.'); return false }
    finally { actionLoading.value = false }
  }

  return { members, vehicles, memberDetail, vehicleDetail, memberPage, vehiclePage, loading, detailLoading, actionLoading, error, successMessage, clearMessages, loadMembers, loadVehicles, loadMemberDetail, loadVehicleDetail, saveMember, registerBlacklist, releaseBlacklist }
}

