import axiosClient from '@/shared/api/axiosClient'

export function getFaqs() {
  return axiosClient.get('/api/admin/chatbot/faqs')
}

export function getMetrics() {
  return axiosClient.get('/api/admin/chatbot/metrics')
}

export function createFaq(payload) {
  return axiosClient.post('/api/admin/chatbot/faqs', payload)
}

export function updateFaq(faqId, payload) {
  return axiosClient.put(`/api/admin/chatbot/faqs/${faqId}`, payload)
}

export function deleteFaq(faqId) {
  return axiosClient.delete(`/api/admin/chatbot/faqs/${faqId}`)
}

export function getUnresolvedQuestions() {
  return axiosClient.get('/api/admin/chatbot/unresolved-questions')
}

export function resolveQuestion(questionId) {
  return axiosClient.patch(`/api/admin/chatbot/unresolved-questions/${questionId}/resolve`)
}
