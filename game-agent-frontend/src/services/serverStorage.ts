/*
 * 服务器端游戏存储服务
 */

import axios from 'axios'

// 使用相对路径，这样会自动使用当前页面的host和端口
const API_BASE_URL = import.meta.env.VITE_API_URL || ''

export interface ServerSavedGame {
  id?: string
  title: string
  type?: string
  ageGroup?: string
  difficulty?: string
  theme?: string
  html: string
  config?: string
  createdAt?: string
  updatedAt?: string
  fileName?: string
  fileSize?: number
}

export interface StorageStats {
  totalGames: number
  totalSize: number
  storagePath: string
}

class ServerStorageService {
  private baseUrl: string

  constructor() {
    this.baseUrl = `${API_BASE_URL}/api/game/storage`
  }

  /**
   * 保存游戏到服务器
   */
  async saveGame(gameData: any): Promise<{ success: boolean; gameId?: string; message: string }> {
    try {
      const saveData: ServerSavedGame = {
        title: gameData.gameData?.title || gameData.title || '未命名游戏',
        type: gameData.gameData?.type || gameData.config?.gameType,
        ageGroup: gameData.gameData?.ageGroup || gameData.config?.ageGroup,
        difficulty: gameData.gameData?.difficulty || gameData.config?.difficulty,
        theme: gameData.gameData?.theme || gameData.config?.theme,
        html: gameData.html,
        config: JSON.stringify(gameData.config || gameData.gameData || {})
      }

      const response = await axios.post(`${this.baseUrl}/save`, saveData)

      if (response.data.success) {
        return {
          success: true,
          gameId: response.data.gameId,
          message: response.data.message || '游戏已保存到服务器'
        }
      }

      throw new Error(response.data.error || '保存失败')
    } catch (error) {
      console.error('保存游戏到服务器失败:', error)
      return {
        success: false,
        message: error instanceof Error ? error.message : '保存失败'
      }
    }
  }

  /**
   * 获取服务器上的游戏列表
   */
  async listGames(): Promise<ServerSavedGame[]> {
    try {
      const response = await axios.get(`${this.baseUrl}/list`)

      if (response.data.success) {
        return response.data.data || []
      }

      throw new Error(response.data.error || '获取列表失败')
    } catch (error) {
      console.error('获取服务器游戏列表失败:', error)
      return []
    }
  }

  /**
   * 根据ID获取游戏详情
   */
  async getGame(gameId: string): Promise<ServerSavedGame | null> {
    try {
      const response = await axios.get(`${this.baseUrl}/${gameId}`)

      if (response.data.success) {
        return response.data.data
      }

      return null
    } catch (error) {
      console.error('获取游戏详情失败:', error)
      return null
    }
  }

  /**
   * 删除服务器上的游戏
   */
  async deleteGame(gameId: string): Promise<boolean> {
    try {
      const response = await axios.delete(`${this.baseUrl}/${gameId}`)
      return response.data.success || false
    } catch (error) {
      console.error('删除游戏失败:', error)
      return false
    }
  }

  /**
   * 批量删除游戏
   */
  async deleteGames(gameIds: string[]): Promise<{ successCount: number; failCount: number }> {
    try {
      const response = await axios.delete(`${this.baseUrl}/batch`, {
        data: gameIds
      })

      if (response.data.success) {
        return {
          successCount: response.data.successCount || 0,
          failCount: response.data.failCount || 0
        }
      }

      return { successCount: 0, failCount: gameIds.length }
    } catch (error) {
      console.error('批量删除游戏失败:', error)
      return { successCount: 0, failCount: gameIds.length }
    }
  }

  /**
   * 获取存储统计信息
   */
  async getStorageStats(): Promise<StorageStats | null> {
    try {
      const response = await axios.get(`${this.baseUrl}/stats`)

      if (response.data.success) {
        return response.data.data
      }

      return null
    } catch (error) {
      console.error('获取存储统计失败:', error)
      return null
    }
  }

  /**
   * 格式化文件大小
   */
  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes'

    const k = 1024
    const sizes = ['Bytes', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))

    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
  }

  /**
   * 格式化日期时间
   */
  formatDateTime(dateStr?: string): string {
    if (!dateStr) return ''

    const date = new Date(dateStr)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  }
}

// 导出单例
export const serverStorage = new ServerStorageService()