/*
 * 游戏历史记录组件
 * 显示已保存的游戏列表，支持加载和删除
 */
import React, { useState, useEffect } from 'react'
import {
  Modal,
  List,
  Card,
  Button,
  Space,
  Typography,
  Tag,
  Popconfirm,
  message,
  Empty,
  Input,
  Upload
} from 'antd'
import {
  DeleteOutlined,
  PlayCircleOutlined,
  DownloadOutlined,
  UploadOutlined,
  SearchOutlined,
  ClockCircleOutlined,
  ClearOutlined
} from '@ant-design/icons'
import { gameStorage, SavedGame } from '../services/gameStorage'
import type { UploadProps } from 'antd'

const { Title, Text, Paragraph } = Typography
const { Search } = Input

interface GameHistoryProps {
  visible: boolean
  onClose: () => void
  onLoadGame: (gameData: any) => void
}

const GameHistory: React.FC<GameHistoryProps> = ({
  visible,
  onClose,
  onLoadGame
}) => {
  const [games, setGames] = useState<SavedGame[]>([])
  const [filteredGames, setFilteredGames] = useState<SavedGame[]>([])
  const [loading, setLoading] = useState(false)
  const [searchText, setSearchText] = useState('')

  // 加载游戏列表
  const loadGames = () => {
    setLoading(true)
    try {
      const savedGames = gameStorage.getAllGames()
      setGames(savedGames)
      setFilteredGames(savedGames)
    } catch (error) {
      message.error('加载游戏列表失败')
    } finally {
      setLoading(false)
    }
  }

  // 组件挂载和visible变化时重新加载
  useEffect(() => {
    if (visible) {
      loadGames()
    }
  }, [visible])

  // 搜索过滤
  useEffect(() => {
    if (searchText) {
      const filtered = games.filter(game =>
        game.title.toLowerCase().includes(searchText.toLowerCase()) ||
        game.type.toLowerCase().includes(searchText.toLowerCase()) ||
        (game.theme && game.theme.toLowerCase().includes(searchText.toLowerCase()))
      )
      setFilteredGames(filtered)
    } else {
      setFilteredGames(games)
    }
  }, [searchText, games])

  // 加载游戏
  const handleLoadGame = (game: SavedGame) => {
    const gameData = {
      html: game.html,
      gameData: game.config || {
        title: game.title,
        type: game.type,
        ageGroup: game.ageGroup,
        difficulty: game.difficulty,
        theme: game.theme
      },
      config: game.config
    }
    onLoadGame(gameData)
    onClose()
    message.success(`已加载游戏: ${game.title}`)
  }

  // 删除游戏
  const handleDeleteGame = (gameId: string) => {
    if (gameStorage.deleteGame(gameId)) {
      message.success('游戏已删除')
      loadGames()
    } else {
      message.error('删除失败')
    }
  }

  // 清空所有游戏
  const handleClearAll = () => {
    gameStorage.clearAll()
    setGames([])
    setFilteredGames([])
    message.success('已清空所有保存的游戏')
  }

  // 导出所有游戏
  const handleExportAll = () => {
    try {
      const jsonData = gameStorage.exportGames()
      const blob = new Blob([jsonData], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `games_backup_${new Date().toISOString().slice(0, 10)}.json`
      a.click()
      URL.revokeObjectURL(url)
      message.success('游戏数据已导出')
    } catch (error) {
      message.error('导出失败')
    }
  }

  // 导入游戏配置
  const uploadProps: UploadProps = {
    accept: '.json',
    showUploadList: false,
    beforeUpload: (file) => {
      const reader = new FileReader()
      reader.onload = (e) => {
        try {
          const jsonData = e.target?.result as string
          const count = gameStorage.importGames(jsonData)
          message.success(`成功导入 ${count} 个游戏`)
          loadGames()
        } catch (error) {
          message.error(error instanceof Error ? error.message : '导入失败')
        }
      }
      reader.readAsText(file)
      return false // 阻止自动上传
    }
  }

  // 格式化时间
  const formatTime = (timestamp: number) => {
    const date = new Date(timestamp)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    const days = Math.floor(diff / (1000 * 60 * 60 * 24))

    if (days === 0) {
      const hours = Math.floor(diff / (1000 * 60 * 60))
      if (hours === 0) {
        const minutes = Math.floor(diff / (1000 * 60))
        return minutes <= 1 ? '刚刚' : `${minutes}分钟前`
      }
      return `${hours}小时前`
    } else if (days === 1) {
      return '昨天'
    } else if (days < 7) {
      return `${days}天前`
    } else {
      return date.toLocaleDateString('zh-CN')
    }
  }

  return (
    <Modal
      title={
        <Space>
          <ClockCircleOutlined />
          <span>游戏历史记录</span>
          <Tag color="blue">{games.length} 个游戏</Tag>
        </Space>
      }
      open={visible}
      onCancel={onClose}
      width={800}
      footer={[
        <Button key="import" icon={<UploadOutlined />}>
          <Upload {...uploadProps}>导入备份</Upload>
        </Button>,
        <Button
          key="export"
          icon={<DownloadOutlined />}
          onClick={handleExportAll}
          disabled={games.length === 0}
        >
          导出备份
        </Button>,
        <Popconfirm
          key="clear"
          title="确定要清空所有保存的游戏吗？"
          onConfirm={handleClearAll}
          okText="确定"
          cancelText="取消"
        >
          <Button
            danger
            icon={<ClearOutlined />}
            disabled={games.length === 0}
          >
            清空所有
          </Button>
        </Popconfirm>,
        <Button key="close" type="primary" onClick={onClose}>
          关闭
        </Button>
      ]}
    >
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        {/* 搜索框 */}
        <Search
          placeholder="搜索游戏标题、类型或主题"
          allowClear
          enterButton={<SearchOutlined />}
          size="middle"
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
        />

        {/* 游戏列表 */}
        {filteredGames.length === 0 ? (
          <Empty
            description={
              searchText ? "没有找到匹配的游戏" : "还没有保存的游戏"
            }
            style={{ padding: '40px 0' }}
          />
        ) : (
          <List
            loading={loading}
            grid={{ gutter: 16, xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 3 }}
            dataSource={filteredGames}
            renderItem={(game) => (
              <List.Item>
                <Card
                  hoverable
                  size="small"
                  title={
                    <Space>
                      <Text strong ellipsis style={{ maxWidth: 200 }}>
                        {game.title}
                      </Text>
                    </Space>
                  }
                  extra={
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {formatTime(game.timestamp)}
                    </Text>
                  }
                  actions={[
                    <Button
                      key="load"
                      type="link"
                      icon={<PlayCircleOutlined />}
                      onClick={() => handleLoadGame(game)}
                    >
                      加载
                    </Button>,
                    <Popconfirm
                      key="delete"
                      title="确定要删除这个游戏吗？"
                      onConfirm={() => handleDeleteGame(game.id)}
                      okText="确定"
                      cancelText="取消"
                    >
                      <Button
                        type="link"
                        danger
                        icon={<DeleteOutlined />}
                      >
                        删除
                      </Button>
                    </Popconfirm>
                  ]}
                >
                  <Space direction="vertical" size="small" style={{ width: '100%' }}>
                    <Space wrap>
                      {game.type && (
                        <Tag color="blue">{game.type}</Tag>
                      )}
                      {game.ageGroup && (
                        <Tag color="green">{game.ageGroup}</Tag>
                      )}
                      {game.difficulty && (
                        <Tag color="orange">{game.difficulty}</Tag>
                      )}
                      {game.theme && (
                        <Tag color="purple">{game.theme}</Tag>
                      )}
                    </Space>
                    {game.thumbnail && (
                      <Paragraph
                        ellipsis={{ rows: 2 }}
                        style={{ marginBottom: 0, fontSize: 12, color: '#666' }}
                      >
                        {game.thumbnail}
                      </Paragraph>
                    )}
                  </Space>
                </Card>
              </List.Item>
            )}
          />
        )}
      </Space>
    </Modal>
  )
}

export default GameHistory