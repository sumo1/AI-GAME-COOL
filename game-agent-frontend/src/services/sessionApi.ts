/*
 * 会话/消息/游戏相关 API（对齐 Step 4a 后端契约）
 * 路径前缀：/api/sessions
 * 时间字段一律 ms epoch（number）
 */

import axios, { AxiosError } from 'axios'

// 与 services/api.ts 一致：使用相对路径，由 Vite dev server / 反代转发
const SESSIONS_BASE = '/api/sessions'

export interface SessionSummary {
  id: string
  title: string
  modelKey: string | null
  createdAt: number
  updatedAt: number
  messageCount: number
  gameCount: number
}

export interface SessionMessage {
  id: string
  sessionId: string
  role: 'user' | 'assistant' | 'system'
  content: string
  iterations: number | null
  evalScore: number | null
  createdAt: number
}

export interface GameSummary {
  id: string
  sessionId: string
  messageId: string
  title: string | null
  evalScore: number
  iterations: number
  favorited: boolean
  createdAt: number
}

export interface CloneSessionResult {
  newSessionId: string
  copiedMessages: number
}

interface ApiEnvelope<T> {
  success: boolean
  data?: T
  count?: number
  error?: string
}

const extractErrorMessage = (err: unknown, fallback: string): string => {
  if (err instanceof AxiosError) {
    const data = err.response?.data as { error?: string; message?: string } | undefined
    if (data?.error) return data.error
    if (data?.message) return data.message
    if (err.message) return err.message
  }
  if (err instanceof Error) return err.message
  return fallback
}

const unwrap = <T>(envelope: ApiEnvelope<T>, fallbackError: string): T => {
  if (!envelope.success || envelope.data === undefined) {
    throw new Error(envelope.error || fallbackError)
  }
  return envelope.data
}

export const listSessions = async (limit: number = 20): Promise<SessionSummary[]> => {
  try {
    const response = await axios.get<ApiEnvelope<SessionSummary[]>>(`${SESSIONS_BASE}`, {
      params: { limit }
    })
    return unwrap(response.data, '获取会话列表失败') ?? []
  } catch (err) {
    console.error('获取会话列表失败:', err)
    throw new Error(extractErrorMessage(err, '获取会话列表失败'))
  }
}

export const getSessionMessages = async (sessionId: string): Promise<SessionMessage[]> => {
  try {
    const response = await axios.get<ApiEnvelope<SessionMessage[]>>(
      `${SESSIONS_BASE}/${encodeURIComponent(sessionId)}/messages`
    )
    return unwrap(response.data, '获取消息列表失败') ?? []
  } catch (err) {
    console.error('获取消息列表失败:', err)
    throw new Error(extractErrorMessage(err, '获取消息列表失败'))
  }
}

export const getSessionGames = async (sessionId: string): Promise<GameSummary[]> => {
  try {
    const response = await axios.get<ApiEnvelope<GameSummary[]>>(
      `${SESSIONS_BASE}/${encodeURIComponent(sessionId)}/games`
    )
    return unwrap(response.data, '获取会话游戏失败') ?? []
  } catch (err) {
    console.error('获取会话游戏失败:', err)
    throw new Error(extractErrorMessage(err, '获取会话游戏失败'))
  }
}

export const cloneSession = async (sessionId: string): Promise<CloneSessionResult> => {
  try {
    const response = await axios.post<ApiEnvelope<CloneSessionResult>>(
      `${SESSIONS_BASE}/${encodeURIComponent(sessionId)}/clone`
    )
    return unwrap(response.data, '复制会话失败')
  } catch (err) {
    console.error('复制会话失败:', err)
    throw new Error(extractErrorMessage(err, '复制会话失败'))
  }
}

export const deleteSession = async (sessionId: string): Promise<void> => {
  try {
    const response = await axios.delete<ApiEnvelope<unknown>>(
      `${SESSIONS_BASE}/${encodeURIComponent(sessionId)}`
    )
    if (!response.data.success) {
      throw new Error(response.data.error || '删除会话失败')
    }
  } catch (err) {
    console.error('删除会话失败:', err)
    throw new Error(extractErrorMessage(err, '删除会话失败'))
  }
}

interface GameHtmlPayload {
  id: string
  html: string
}

export const getGameHtml = async (gameId: string): Promise<string> => {
  try {
    const response = await axios.get<ApiEnvelope<GameHtmlPayload>>(
      `${SESSIONS_BASE}/games/${encodeURIComponent(gameId)}/html`
    )
    const data = unwrap(response.data, '获取游戏内容失败')
    return data.html
  } catch (err) {
    console.error('获取游戏内容失败:', err)
    throw new Error(extractErrorMessage(err, '获取游戏内容失败'))
  }
}

interface FavoriteToggleResult {
  id: string
  favorited: boolean
}

export const favoriteGame = async (gameId: string): Promise<void> => {
  try {
    const response = await axios.post<ApiEnvelope<FavoriteToggleResult>>(
      `${SESSIONS_BASE}/games/${encodeURIComponent(gameId)}/favorite`
    )
    if (!response.data.success) {
      throw new Error(response.data.error || '收藏失败')
    }
  } catch (err) {
    console.error('收藏游戏失败:', err)
    throw new Error(extractErrorMessage(err, '收藏失败'))
  }
}

export const unfavoriteGame = async (gameId: string): Promise<void> => {
  try {
    const response = await axios.post<ApiEnvelope<FavoriteToggleResult>>(
      `${SESSIONS_BASE}/games/${encodeURIComponent(gameId)}/unfavorite`
    )
    if (!response.data.success) {
      throw new Error(response.data.error || '取消收藏失败')
    }
  } catch (err) {
    console.error('取消收藏游戏失败:', err)
    throw new Error(extractErrorMessage(err, '取消收藏失败'))
  }
}

export const listFavoriteGames = async (limit: number = 50): Promise<GameSummary[]> => {
  try {
    const response = await axios.get<ApiEnvelope<GameSummary[]>>(
      `${SESSIONS_BASE}/games/favorites`,
      { params: { limit } }
    )
    return unwrap(response.data, '获取收藏列表失败') ?? []
  } catch (err) {
    console.error('获取收藏列表失败:', err)
    throw new Error(extractErrorMessage(err, '获取收藏列表失败'))
  }
}
