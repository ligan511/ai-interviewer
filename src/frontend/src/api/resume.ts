import api from './request'

export interface Resume {
  id: number
  userId: number
  fileName: string
  fileUrl: string
  summary: string | null
  parsedJson: Record<string, any> | null
  createdAt: string
}

export interface ResumeUploadResponse {
  id: number
  fileName: string
  fileUrl: string
  createdAt: string
}

export const resumeApi = {
  upload(formData: FormData) {
    return api.post<any, { code: number; data: ResumeUploadResponse; message: string }>(
      '/resumes', formData, { headers: { 'Content-Type': 'multipart/form-data' } }
    )
  },
  list() {
    return api.get<any, { code: number; data: Resume[]; message: string }>('/resumes')
  },
  get(id: number) {
    return api.get<any, { code: number; data: Resume; message: string }>(`/resumes/${id}`)
  },
  delete(id: number) {
    return api.delete<any, { code: number; message: string }>(`/resumes/${id}`)
  }
}
