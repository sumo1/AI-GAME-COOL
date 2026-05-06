/*
 * @since: 2025/8/11
 * @author: sumo
 */
import axios from 'axios'

const API_BASE_URL = '/api/game'
const PRIMARY_GENERATE_ROUTE = `${API_BASE_URL}/v2/generate`

type GameRecord = Record<string, any>

export interface GameInfo {
  title?: string
  description?: string
  type?: string
  ageGroup?: string
  difficulty?: string
  theme?: string
  iterations?: number
  evalScore?: number
}

export interface GameRequest {
  userInput: string
  sessionId?: string
  options?: Record<string, any>
}

export interface GameResponse {
  sessionId: string
  success: boolean
  message: string
  gameData: GameRecord | string | null
  config?: GameRecord | null
  agentName: string
  agentSource?: 'system' | 'llm'
  modelName?: string
  generatedByLLM?: boolean
  error?: string
}

const isRecord = (value: unknown): value is GameRecord =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const getNestedGameData = (gameData: GameResponse['gameData']): GameRecord => {
  if (!isRecord(gameData) || !isRecord(gameData.gameData)) {
    return {}
  }

  return gameData.gameData
}

const hasDefinedValue = (record: GameRecord) =>
  Object.values(record).some(value => value !== undefined && value !== null && value !== '')

const normalizeGameResponse = (response: GameResponse): GameResponse => {
  const nestedGameData = getNestedGameData(response.gameData)
  const topLevelGameData = isRecord(response.gameData) ? response.gameData : {}
  const mergedConfig: GameRecord = isRecord(response.config) ? { ...response.config } : {}

  if (!mergedConfig.gameType) {
    mergedConfig.gameType = nestedGameData.type ?? topLevelGameData.type
  }
  if (!mergedConfig.ageGroup && nestedGameData.ageGroup) {
    mergedConfig.ageGroup = nestedGameData.ageGroup
  }
  if (!mergedConfig.difficulty && nestedGameData.difficulty) {
    mergedConfig.difficulty = nestedGameData.difficulty
  }
  if (!mergedConfig.theme && nestedGameData.theme) {
    mergedConfig.theme = nestedGameData.theme
  }

  const generatedByLLM =
    typeof response.generatedByLLM === 'boolean'
      ? response.generatedByLLM
      : typeof topLevelGameData.generatedByLLM === 'boolean'
        ? topLevelGameData.generatedByLLM
        : undefined

  return {
    ...response,
    config: hasDefinedValue(mergedConfig) ? mergedConfig : response.config,
    generatedByLLM,
    agentSource:
      response.agentSource || (typeof generatedByLLM === 'boolean' ? (generatedByLLM ? 'llm' : 'system') : undefined)
  }
}

export const extractGameInfo = (response: Pick<GameResponse, 'gameData' | 'config'>): GameInfo => {
  const nestedGameData = getNestedGameData(response.gameData)
  const topLevelGameData = isRecord(response.gameData) ? response.gameData : {}
  const config = isRecord(response.config) ? response.config : {}

  return {
    title: nestedGameData.title ?? config.title,
    description: nestedGameData.description ?? config.description,
    type: nestedGameData.type ?? config.gameType ?? topLevelGameData.type,
    ageGroup: nestedGameData.ageGroup ?? config.ageGroup,
    difficulty: nestedGameData.difficulty ?? config.difficulty,
    theme: nestedGameData.theme ?? config.theme,
    iterations: typeof nestedGameData.iterations === 'number' ? nestedGameData.iterations : undefined,
    evalScore: typeof nestedGameData.evalScore === 'number' ? nestedGameData.evalScore : undefined
  }
}

/**
 * 生成游戏
 * 前端主路径默认走 V2，旧 V1 路由仍保留在后端用于兼容。
 */
export const generateGame = async (userInput: string, options?: Record<string, any>): Promise<GameResponse> => {
  try {
    const response = await axios.post<GameResponse>(PRIMARY_GENERATE_ROUTE, {
      userInput,
      sessionId: localStorage.getItem('sessionId') || undefined,
      options: options || undefined
    })
    
    // 保存sessionId
    if (response.data.sessionId) {
      localStorage.setItem('sessionId', response.data.sessionId)
    }
    
    return normalizeGameResponse(response.data)
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
