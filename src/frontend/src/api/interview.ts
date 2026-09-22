import api from './request'

export interface CreateInterviewRequest {
  jobId: number
  resumeId?: number
  type?: string
  difficulty?: string
  questionLimit?: number
  durationLimitSeconds?: number
}

export interface InterviewSession {
  id: number
  userId: number
  jobId: number
  resumeId: number | null
  interviewType: string
  difficulty: string
  questionLimit: number
  durationLimitSeconds: number
  status: string
  startedAt: string | null
  endedAt: string | null
  createdAt: string
}

export interface NextQuestionResponse {
  questionId: number
  parentQuestionId: number | null
  content: string
  type: string
  difficulty: string
  targetSkill: string
  reason: string
}

export interface SubmitAnswerRequest {
  questionId: number
  answerText: string
  clientDurationSeconds?: number
}

export interface AnswerResponse {
  answerId: number
  evaluationStatus: string
  nextAction: string
}

export interface Evaluation {
  totalScore: number
  professionalScore: number
  logicScore: number
  completenessScore: number
  analysisScore: number
  expressionScore: number
  jobMatchScore: number
  strengths: string[]
  weaknesses: string[]
  suggestions: string[]
  referenceAnswer: string
}

export interface InterviewReport {
  id: number
  sessionId: number
  overallScore: number
  dimensionScores: Record<string, number>
  summary: string
  strengths: string[]
  weaknesses: string[]
  suggestions: string[]
  createdAt: string
}

export const interviewApi = {
  create(data: CreateInterviewRequest) {
    return api.post<any, { code: number; data: { sessionId: number; status: string }; message: string }>('/interviews', data)
  },
  get(id: number) {
    return api.get<any, { code: number; data: InterviewSession; message: string }>(`/interviews/${id}`)
  },
  complete(id: number) {
    return api.post<any, { code: number; data: any; message: string }>(`/interviews/${id}/complete`)
  },
  history(params?: { page?: number; pageSize?: number }) {
    return api.get<any, { code: number; data: any; message: string }>('/interviews/history', { params })
  },
  nextQuestion(sessionId: number, lastQuestionId?: number) {
    return api.post<any, { code: number; data: NextQuestionResponse; message: string }>(
      `/interviews/${sessionId}/questions/next`,
      undefined,
      { params: lastQuestionId ? { lastQuestionId } : {}, timeout: 150000 }
    )
  },
  submitAnswer(sessionId: number, data: SubmitAnswerRequest) {
    return api.post<any, { code: number; data: AnswerResponse; message: string }>(
      `/interviews/${sessionId}/answers`,
      data
    )
  },
  getEvaluation(answerId: number) {
    return api.get<any, { code: number; data: Evaluation; message: string }>(`/answers/${answerId}/evaluation`)
  },
  getReport(sessionId: number) {
    return api.get<any, { code: number; data: InterviewReport; message: string }>(`/interviews/${sessionId}/report`)
  },
  submitAudioAnswer(sessionId: number, audioBlob: Blob, questionId: number) {
    const formData = new FormData()
    formData.append('audio', audioBlob, 'recording.webm')
    formData.append('questionId', String(questionId))
    return api.post<any, { code: number; data: any; message: string }>(
      `/interviews/${sessionId}/answers/audio`, formData
    )
  }
}
