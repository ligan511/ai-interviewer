import api from './request'

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  userId: number
  email: string
  username: string
}

export const authApi = {
  register(data: RegisterRequest) {
    return api.post<any, { code: number; data: any; message: string }>('/auth/register', data)
  },
  login(data: LoginRequest) {
    return api.post<any, { code: number; data: LoginResponse; message: string }>('/auth/login', data)
  }
}
