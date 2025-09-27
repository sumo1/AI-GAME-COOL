/*
 * 服务器游戏历史记录组件
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
  Tabs,
  Statistic,
  Row,
  Col,
  Spin
} from 'antd'
import {
  DeleteOutlined,
  PlayCircleOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  FileOutlined,
  ReloadOutlined
} from '@ant-design/icons'
import { serverStorage, ServerSavedGame } from '../services/serverStorage'

const { Title, Text, Paragraph } = Typography

interface ServerGameHistoryProps {
  visible: boolean
  onClose: () => void
  onLoadGame: (gameData: any) => void
}

const ServerGameHistory: React.FC<ServerGameHistoryProps> = ({
  visible,
  onClose,
  onLoadGame
}) => {
  const [games, setGames] = useState<ServerSavedGame[]>([])
  const [loading, setLoading] = useState(false)
  const [stats, setStats] = useState<any>(null)
  const [selectedGames, setSelectedGames] = useState<string[]>([])

  // 加载游戏列表
  const loadGames = async () => {
    setLoading(true)
    try {
      const serverGames = await serverStorage.listGames()
      setGames(serverGames)

      // 获取统计信息
      const serverStats = await serverStorage.getStorageStats()
      setStats(serverStats)
    } catch (error) {
      message.error('加载服务器游戏列表失败')
    } finally {
      setLoading(false)
    }
  }

  // 组件显示时加载数据
  useEffect(() => {
    if (visible) {
      loadGames()
    }
  }, [visible])

  // 加载游戏
  const handleLoadGame = async (game: ServerSavedGame) => {
    if (!game.id) return

    setLoading(true)
    try {
      const fullGame = await serverStorage.getGame(game.id)

      if (fullGame && fullGame.html) {
        const gameData = {
          html: fullGame.html,
          gameData: fullGame.config ? JSON.parse(fullGame.config) : {
            title: fullGame.title,
            type: fullGame.type,
            ageGroup: fullGame.ageGroup,
            difficulty: fullGame.difficulty,
            theme: fullGame.theme
          },
          config: fullGame.config ? JSON.parse(fullGame.config) : undefined
        }

        onLoadGame(gameData)
        onClose()
        message.success(`已加载游戏: ${fullGame.title}`)
      } else {
        message.error('加载游戏失败')
      }
    } catch (error) {
      message.error('加载游戏失败')
    } finally {
      setLoading(false)
    }
  }

  // 删除游戏
  const handleDeleteGame = async (gameId: string) => {
    if (!gameId) return

    setLoading(true)
    try {
      const success = await serverStorage.deleteGame(gameId)

      if (success) {
        message.success('游戏已从服务器删除')
        loadGames()
      } else {
        message.error('删除失败')
      }
    } catch (error) {
      message.error('删除失败')
    } finally {
      setLoading(false)
    }
  }

  // 批量删除
  const handleBatchDelete = async () => {
    if (selectedGames.length === 0) {
      message.warning('请选择要删除的游戏')
      return
    }

    setLoading(true)
    try {
      const result = await serverStorage.deleteGames(selectedGames)
      message.success(`成功删除 ${result.successCount} 个游戏`)
      setSelectedGames([])
      loadGames()
    } catch (error) {
      message.error('批量删除失败')
    } finally {
      setLoading(false)
    }
  }

  // 格式化时间
  const formatTime = (dateStr?: string) => {
    if (!dateStr) return '未知'
    return serverStorage.formatDateTime(dateStr)
  }

  // 格式化文件大小
  const formatSize = (size?: number) => {
    if (!size) return '0 KB'
    return serverStorage.formatFileSize(size)
  }

  return (
    <Modal
      title={
        <Space>
          <CloudServerOutlined />
          <span>服务器游戏存储</span>
          {stats && (
            <Tag color="blue">{stats.totalGames} 个游戏</Tag>
          )}
        </Space>
      }
      open={visible}
      onCancel={onClose}
      width={900}
      footer={[
        <Button
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={loadGames}
          loading={loading}
        >
          刷新
        </Button>,
        selectedGames.length > 0 && (
          <Popconfirm
            key="batch-delete"
            title={`确定要删除选中的 ${selectedGames.length} 个游戏吗？`}
            onConfirm={handleBatchDelete}
            okText="确定"
            cancelText="取消"
          >
            <Button danger loading={loading}>
              批量删除 ({selectedGames.length})
            </Button>
          </Popconfirm>
        ),
        <Button key="close" type="primary" onClick={onClose}>
          关闭
        </Button>
      ]}
    >
      <Spin spinning={loading}>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          {/* 统计信息 */}
          {stats && (
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={8}>
                <Card size="small">
                  <Statistic
                    title="游戏总数"
                    value={stats.totalGames}
                    prefix={<DatabaseOutlined />}
                  />
                </Card>
              </Col>
              <Col span={8}>
                <Card size="small">
                  <Statistic
                    title="占用空间"
                    value={formatSize(stats.totalSize)}
                    prefix={<FileOutlined />}
                  />
                </Card>
              </Col>
              <Col span={8}>
                <Card size="small">
                  <Statistic
                    title="存储路径"
                    value={stats.storagePath}
                    valueStyle={{ fontSize: 14 }}
                  />
                </Card>
              </Col>
            </Row>
          )}

          {/* 游戏列表 */}
          {games.length === 0 ? (
            <Empty
              description="服务器上还没有保存的游戏"
              style={{ padding: '40px 0' }}
            />
          ) : (
            <List
              grid={{ gutter: 16, xs: 1, sm: 1, md: 2, lg: 2, xl: 3, xxl: 3 }}
              dataSource={games}
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
                      <Space direction="vertical" size={0}>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {formatTime(game.updatedAt)}
                        </Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {formatSize(game.fileSize)}
                        </Text>
                      </Space>
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
                        title="确定要从服务器删除这个游戏吗？"
                        onConfirm={() => game.id && handleDeleteGame(game.id)}
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
                      {game.fileName && (
                        <Text type="secondary" style={{ fontSize: 11 }} ellipsis>
                          文件: {game.fileName}
                        </Text>
                      )}
                    </Space>
                  </Card>
                </List.Item>
              )}
            />
          )}
        </Space>
      </Spin>
    </Modal>
  )
}

export default ServerGameHistory