/*
 * 游戏本地存储服务
 * 使用localStorage保存游戏数据
 */

export interface SavedGame {
  id: string
  title: string
  type: string
  ageGroup?: string
  difficulty?: string
  theme?: string
  html: string
  config?: any
  timestamp: number
  thumbnail?: string
}

const STORAGE_KEY = 'saved_games'
const MAX_SAVED_GAMES = 20 // 最多保存20个游戏

class GameStorageService {
  // 获取所有保存的游戏
  getAllGames(): SavedGame[] {
    try {
      const data = localStorage.getItem(STORAGE_KEY)
      if (!data) return []
      return JSON.parse(data) as SavedGame[]
    } catch (error) {
      console.error('获取游戏列表失败:', error)
      return []
    }
  }

  // 保存游戏
  saveGame(gameData: any): string {
    try {
      const games = this.getAllGames()

      // 生成唯一ID
      const gameId = `game_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`

      // 创建保存对象
      const savedGame: SavedGame = {
        id: gameId,
        title: gameData.gameData?.title || '未命名游戏',
        type: gameData.gameData?.type || gameData.config?.gameType || '未知',
        ageGroup: gameData.gameData?.ageGroup || gameData.config?.ageGroup,
        difficulty: gameData.gameData?.difficulty || gameData.config?.difficulty,
        theme: gameData.gameData?.theme || gameData.config?.theme,
        html: gameData.html,
        config: gameData.config || gameData.gameData,
        timestamp: Date.now(),
        thumbnail: this.generateThumbnail(gameData.html)
      }

      // 添加到列表开头
      games.unshift(savedGame)

      // 限制保存数量
      if (games.length > MAX_SAVED_GAMES) {
        games.splice(MAX_SAVED_GAMES)
      }

      // 保存到localStorage
      localStorage.setItem(STORAGE_KEY, JSON.stringify(games))

      return gameId
    } catch (error) {
      console.error('保存游戏失败:', error)
      throw new Error('保存失败，请检查浏览器存储空间')
    }
  }

  // 获取单个游戏
  getGame(gameId: string): SavedGame | null {
    const games = this.getAllGames()
    return games.find(game => game.id === gameId) || null
  }

  // 删除游戏
  deleteGame(gameId: string): boolean {
    try {
      const games = this.getAllGames()
      const filteredGames = games.filter(game => game.id !== gameId)

      if (filteredGames.length === games.length) {
        return false // 没有找到要删除的游戏
      }

      localStorage.setItem(STORAGE_KEY, JSON.stringify(filteredGames))
      return true
    } catch (error) {
      console.error('删除游戏失败:', error)
      return false
    }
  }

  // 清空所有游戏
  clearAll(): void {
    localStorage.removeItem(STORAGE_KEY)
  }

  // 生成游戏缩略图（从HTML中提取标题或描述）
  private generateThumbnail(html: string): string {
    // 尝试从HTML中提取游戏标题作为缩略图文本
    const titleMatch = html.match(/<h[1-6][^>]*>(.*?)<\/h[1-6]>/i)
    if (titleMatch) {
      return titleMatch[1].replace(/<[^>]*>/g, '').substring(0, 50)
    }
    return '游戏预览'
  }

  // 检查存储空间
  getStorageInfo(): { used: number; available: boolean } {
    try {
      const data = localStorage.getItem(STORAGE_KEY) || '[]'
      const used = new Blob([data]).size
      const estimatedMax = 5 * 1024 * 1024 // 5MB估计值

      return {
        used,
        available: used < estimatedMax * 0.9 // 使用90%作为警告阈值
      }
    } catch (error) {
      return { used: 0, available: true }
    }
  }

  // 导出所有游戏数据
  exportGames(): string {
    const games = this.getAllGames()
    return JSON.stringify(games, null, 2)
  }

  // 导入游戏数据
  importGames(jsonData: string): number {
    try {
      const importedGames = JSON.parse(jsonData) as SavedGame[]
      const existingGames = this.getAllGames()

      // 合并数据，避免重复
      const gameIds = new Set(existingGames.map(g => g.id))
      const newGames = importedGames.filter(g => !gameIds.has(g.id))

      const mergedGames = [...existingGames, ...newGames]

      // 按时间排序并限制数量
      mergedGames.sort((a, b) => b.timestamp - a.timestamp)
      if (mergedGames.length > MAX_SAVED_GAMES) {
        mergedGames.splice(MAX_SAVED_GAMES)
      }

      localStorage.setItem(STORAGE_KEY, JSON.stringify(mergedGames))

      return newGames.length
    } catch (error) {
      console.error('导入游戏失败:', error)
      throw new Error('导入失败，请检查文件格式')
    }
  }
}

// 导出单例
export const gameStorage = new GameStorageService()