import api from './request'

export interface Job {
  id: number
  name: string
  description: string
  level: string
  status: number
  createdAt: string
  updatedAt: string
}

export interface JobSkill {
  id: number
  jobId: number
  skillName: string
  weight: number
}

export interface JobDetail {
  job: Job
  skills: JobSkill[]
}

export const jobApi = {
  list(params?: { page?: number; pageSize?: number }) {
    return api.get<any, { code: number; data: Job[]; message: string }>('/jobs', { params })
  },
  detail(id: number) {
    return api.get<any, { code: number; data: JobDetail; message: string }>(`/jobs/${id}`)
  }
}
