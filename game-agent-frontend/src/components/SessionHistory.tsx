/*
 * 会话历史抽屉
 * 显示后端 sessions / messages / clone / delete
 */
import React, { useState, useEffect, useCallback } from 'react'
import {
  Drawer,
  List,
  Card,
  Button,
  Space,
  Typography,
  Tag,
  Modal,
  message,
  Empty,
  Spin,
  Switch
} from 'antd'
import {
  ReloadOutlined,
  CopyOutlined,
  DeleteOutlined,
  UnorderedListOutlined,
  HistoryOutlined,
  UserOutlined,
  RobotOutlined
} from '@ant-design/icons'
import {
  listSessions,
  getSessionMessages,
  cloneSession,
  deleteSession,
  SessionSummary,
  SessionMessage
} from '../services/sessionApi'

const { Text, Paragraph } = Typography

interface SessionHistoryProps {
  visible: boolean
  onClose: () => void
  onCloneSession: (newSessionId: string) => void
}

const formatTime = (ms: number): string => {
  if (!ms) return '未知'
  return new Date(ms).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const roleLabel = (role: SessionMessage['role']): { color: string; text: string; icon: React.ReactNode } => {
  if (role === 'user') return { color: 'blue', text: '用户', icon: <UserOutlined /> }
  if (role === 'assistant') return { color: 'green', text: '助手', icon: <RobotOutlined /> }
  return { color: 'default', text: role, icon: <HistoryOutlined /> }
}

const SessionHistory: React.FC<SessionHistoryProps> = ({ visible, onClose, onCloneSession }) => {
  const [sessions, setSessions] = useState<SessionSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [onlyWithGames, setOnlyWithGames] = useState(false)
  const [expandedSessionId, setExpandedSessionId] = useState<string | null>(null)
  const [messagesBySession, setMessagesBySession] = useState<Record<string, SessionMessage[]>>({})
  const [messagesLoading, setMessagesLoading] = useState<string | null>(null)
  const [actionPendingId, setActionPendingId] = useState<string | null>(null)

  const loadSessions = useCallback(async () => {
    setLoading(true)
    try {
      const list = await listSessions(50)
      setSessions(list)
    } catch (err) {
      console.error('加载会话失败:', err)
      message.error('加载会话失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (visible) {
      loadSessions()
    } else {
      // 抽屉关闭时清掉展开态，避免下次打开还残留
      setExpandedSessionId(null)
    }
  }, [visible, loadSessions])

  const handleToggleMessages = async (sessionId: string) => {
    if (expandedSessionId === sessionId) {
      setExpandedSessionId(null)
      return
    }
    setExpandedSessionId(sessionId)
    if (!messagesBySession[sessionId]) {
      setMessagesLoading(sessionId)
      try {
        const msgs = await getSessionMessages(sessionId)
        setMessagesBySession(prev => ({ ...prev, [sessionId]: msgs }))
      } catch (err) {
        console.error('加载消息失败:', err)
        message.error('加载消息失败')
        setExpandedSessionId(null)
      } finally {
        setMessagesLoading(null)
      }
    }
  }

  const handleClone = async (sessionId: string) => {
    setActionPendingId(sessionId)
    try {
      const result = await cloneSession(sessionId)
      message.success(`已复制为新会话（${result.copiedMessages} 条消息）`)
      onCloneSession(result.newSessionId)
      await loadSessions()
    } catch (err) {
      console.error('复制会话失败:', err)
      message.error(err instanceof Error ? err.message : '复制会话失败')
    } finally {
      setActionPendingId(null)
    }
  }

  const handleDelete = (sessionId: string) => {
    Modal.confirm({
      title: '确定删除这个会话吗？',
      content: '删除后该会话的所有消息和游戏也会一起被清理（不可恢复）。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        setActionPendingId(sessionId)
        try {
          await deleteSession(sessionId)
          message.success('会话已删除')
          // 删完局部刷新，避免幽灵条目
          setSessions(prev => prev.filter(s => s.id !== sessionId))
          if (expandedSessionId === sessionId) {
            setExpandedSessionId(null)
          }
          setMessagesBySession(prev => {
            const next = { ...prev }
            delete next[sessionId]
            return next
          })
        } catch (err) {
          console.error('删除会话失败:', err)
          message.error(err instanceof Error ? err.message : '删除会话失败')
          // 删除失败时刷新列表，恢复真值
          await loadSessions()
        } finally {
          setActionPendingId(null)
        }
      }
    })
  }

  const visibleSessions = onlyWithGames
    ? sessions.filter(s => s.gameCount > 0)
    : sessions

  return (
    <Drawer
      title={
        <Space>
          <UnorderedListOutlined />
          <span>会话历史</span>
          <Tag color="blue">{sessions.length} 个会话</Tag>
        </Space>
      }
      placement="right"
      width={720}
      onClose={onClose}
      open={visible}
      extra={
        <Space>
          <Text type="secondary" style={{ fontSize: 12 }}>仅含游戏</Text>
          <Switch
            data-testid="session-filter-with-games"
            size="small"
            checked={onlyWithGames}
            onChange={setOnlyWithGames}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={loadSessions}
            loading={loading}
            size="small"
          >
            刷新
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        {visibleSessions.length === 0 ? (
          <Empty description="暂无会话" style={{ padding: '40px 0' }} />
        ) : (
          <List
            dataSource={visibleSessions}
            rowKey={item => item.id}
            renderItem={session => {
              const isExpanded = expandedSessionId === session.id
              const msgs = messagesBySession[session.id] || []
              const isMsgLoading = messagesLoading === session.id
              const isActionPending = actionPendingId === session.id

              return (
                <List.Item key={session.id} data-testid="session-item">
                  <Card
                    size="small"
                    style={{ width: '100%' }}
                    title={
                      <Space>
                        <Text strong>{session.title || '未命名会话'}</Text>
                        {session.modelKey && (
                          <Tag color="purple">{session.modelKey}</Tag>
                        )}
                      </Space>
                    }
                    extra={
                      <Space size="small">
                        <Tag color="blue">{session.messageCount} 条消息</Tag>
                        <Tag color="green">{session.gameCount} 个游戏</Tag>
                      </Space>
                    }
                    actions={[
                      <Button
                        key="messages"
                        type="link"
                        icon={<HistoryOutlined />}
                        onClick={() => handleToggleMessages(session.id)}
                      >
                        {isExpanded ? '收起消息' : '查看消息'}
                      </Button>,
                      <Button
                        key="clone"
                        type="link"
                        icon={<CopyOutlined />}
                        loading={isActionPending}
                        onClick={() => handleClone(session.id)}
                      >
                        复制并新会话
                      </Button>,
                      <Button
                        key="delete"
                        type="link"
                        danger
                        icon={<DeleteOutlined />}
                        loading={isActionPending}
                        onClick={() => handleDelete(session.id)}
                      >
                        删除
                      </Button>
                    ]}
                  >
                    <Space direction="vertical" size="small" style={{ width: '100%' }}>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        创建：{formatTime(session.createdAt)} ｜ 更新：{formatTime(session.updatedAt)}
                      </Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        ID: {session.id}
                      </Text>
                      {isExpanded && (
                        <div style={{ marginTop: 8, borderTop: '1px solid #f0f0f0', paddingTop: 8 }}>
                          {isMsgLoading ? (
                            <Spin size="small" />
                          ) : msgs.length === 0 ? (
                            <Text type="secondary">暂无消息</Text>
                          ) : (
                            <List
                              dataSource={msgs}
                              size="small"
                              rowKey={item => item.id}
                              renderItem={msg => {
                                const role = roleLabel(msg.role)
                                return (
                                  <List.Item key={msg.id} data-testid="session-message">
                                    <List.Item.Meta
                                      avatar={role.icon}
                                      title={
                                        <Space size="small">
                                          <Tag color={role.color}>{role.text}</Tag>
                                          {msg.evalScore !== null && (
                                            <Tag color="gold">评分 {msg.evalScore}</Tag>
                                          )}
                                          {msg.iterations !== null && (
                                            <Tag>迭代 {msg.iterations}</Tag>
                                          )}
                                          <Text type="secondary" style={{ fontSize: 11 }}>
                                            {formatTime(msg.createdAt)}
                                          </Text>
                                        </Space>
                                      }
                                      description={
                                        <Paragraph
                                          ellipsis={{ rows: 4, expandable: true, symbol: '展开' }}
                                          style={{ marginBottom: 0, fontSize: 12 }}
                                        >
                                          {msg.content}
                                        </Paragraph>
                                      }
                                    />
                                  </List.Item>
                                )
                              }}
                            />
                          )}
                        </div>
                      )}
                    </Space>
                  </Card>
                </List.Item>
              )
            }}
          />
        )}
      </Spin>
    </Drawer>
  )
}

export default SessionHistory
