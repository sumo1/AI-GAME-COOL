/*
 * @since: 2025/8/11
 * @author: sumo
 */
import axios from 'axios'

const API_BASE_URL = '/api/game'

export interface GameRequest {
  userInput: string
  sessionId?: string
  options?: Record<string, any>
}

export interface GameResponse {
  sessionId: string
  success: boolean
  message: string
  gameData: any
  config: any
  agentName: string
  agentSource?: 'system' | 'llm'
  modelName?: string
  generatedByLLM?: boolean
  error?: string
}

/**
 * 生成游戏
 */
export const generateGame = async (userInput: string, options?: Record<string, any>): Promise<GameResponse> => {
  try {
    const response = await axios.post<GameResponse>(`${API_BASE_URL}/generate`, {
      userInput,
      sessionId: localStorage.getItem('sessionId') || undefined,
      options: options || undefined
    })
    
    // 保存sessionId
    if (response.data.sessionId) {
      localStorage.setItem('sessionId', response.data.sessionId)
    }
    
    return response.data
  } catch (error: any) {
    console.error('生成游戏失败:', error)
    throw new Error(error.response?.data?.message || '网络错误')
  }
}

/**
 * 获取Agent列表
 */
export const getAgents = async () => {
  try {
    const response = await axios.get(`${API_BASE_URL}/agents`)
    return response.data
  } catch (error) {
    console.error('获取Agent列表失败:', error)
    throw error
  }
}

/**
 * SSE流式生成（用于未来扩展）
 */
export const generateGameStream = (userInput: string, onMessage: (event: any) => void) => {
  const sessionId = localStorage.getItem('sessionId') || ''
  const eventSource = new EventSource(
    `${API_BASE_URL}/generate/stream?userInput=${encodeURIComponent(userInput)}&sessionId=${sessionId}`
  )
  
  eventSource.onmessage = (event) => {
    const data = JSON.parse(event.data)
    onMessage(data)
  }
  
  eventSource.onerror = (error) => {
    console.error('SSE错误:', error)
    eventSource.close()
  }
  
  return eventSource
}
