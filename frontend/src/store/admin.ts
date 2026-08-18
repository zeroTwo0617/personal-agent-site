import { defineStore } from 'pinia'
import { ref } from 'vue'

const TOKEN_KEY = 'admin_token'

export const useAdminStore = defineStore('admin', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')

  function setToken(t: string) {
    token.value = t
    localStorage.setItem(TOKEN_KEY, t)
  }

  function logout() {
    token.value = ''
    localStorage.removeItem(TOKEN_KEY)
  }

  return { token, setToken, logout }
})
