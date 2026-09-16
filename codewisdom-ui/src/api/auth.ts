import { request } from './http'

export interface AuthResponse {
  token: string
  userId: number
  username: string
  nickname: string
}

export interface RegisterPayload {
  username: string
  password: string
  nickname?: string
}

export interface LoginPayload {
  username: string
  password: string
}

export function register(payload: RegisterPayload) {
  return request<AuthResponse>({
    url: '/project-resource/auth/register',
    method: 'POST',
    data: payload,
  })
}

export function login(payload: LoginPayload) {
  return request<AuthResponse>({
    url: '/project-resource/auth/login',
    method: 'POST',
    data: payload,
  })
}
