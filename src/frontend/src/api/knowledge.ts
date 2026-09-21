import api from './request'

export interface KnowledgeDocument {
  id: number
  jobId: number
  title: string
  fileUrl: string
  version: string
  status: string
  createdAt: string
}

export const knowledgeApi = {
  upload(formData: FormData) {
    return api.post<any, { code: number; data: KnowledgeDocument; message: string }>(
      '/knowledge', formData, { headers: { 'Content-Type': 'multipart/form-data' } }
    )
  },
  list(jobId?: number) {
    const params = jobId ? { jobId } : {}
    return api.get<any, { code: number; data: KnowledgeDocument[]; message: string }>(
      '/knowledge', { params }
    )
  },
  delete(id: number) {
    return api.delete<any, { code: number; message: string }>(`/knowledge/${id}`)
  }
}
