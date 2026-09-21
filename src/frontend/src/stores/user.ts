import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface UserInfo {
  userId: number
  email: string
  username: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(
    localStorage.getItem('userInfo') ? JSON.parse(localStorage.getItem('userInfo')!) : null
  )

  function setAuth(tokenValue: string, info: UserInfo) {
    token.value = tokenValue
    userInfo.value = info
    localStorage.setItem('token', tokenValue)
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return { token, userInfo, setAuth, logout }
})
