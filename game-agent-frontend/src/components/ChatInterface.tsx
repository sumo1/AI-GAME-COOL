/*
 * @since: 2025/8/11
 * @author: sumo
 */
import React, { useState, useRef, useEffect } from 'react'
import { Input, Button, Card, List, Avatar, Spin, message, Select, Tooltip } from 'antd'
import { SendOutlined, RobotOutlined, UserOutlined } from '@ant-design/icons'
import { generateGame } from '../services/api'
import ReactMarkdown from 'react-markdown'

const { TextArea } = Input

interface Message {
  id: string
  type: 'user' | 'assistant' | 'system'
  content: string
  timestamp: Date
}

interface ChatInterfaceProps {
  onGameGenerated: (data: any) => void
  setLoading: (loading: boolean) => void
}

const ChatInterface: React.FC<ChatInterfaceProps> = ({ onGameGenerated, setLoading }) => {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '1',
      type: 'assistant',
      content: '👋 你好！我是儿童游戏生成助手。请告诉我你想创建什么样的游戏？\n\n例如：\n- "给6岁孩子做一个10以内加法的游戏"\n- "创建一个动物主题的记忆游戏"\n- "做一个学习英语单词的游戏"',
      timestamp: new Date()
    }
  ])
  const [inputValue, setInputValue] = useState('')
  const [generating, setGenerating] = useState(false)
  const [model, setModel] = useState<string>('dashscope')
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const handleSend = async () => {
    if (!inputValue.trim()) return

    const userMessage: Message = {
      id: Date.now().toString(),
      type: 'user',
      content: inputValue,
      timestamp: new Date()
    }

    setMessages(prev => [...prev, userMessage])
    setInputValue('')
    setGenerating(true)
    setLoading(true)

    try {
      // 添加思考中消息
      const thinkingMessage: Message = {
        id: Date.now().toString() + '-thinking',
        type: 'assistant',
        content: '🤔 正在分析您的需求...',
        timestamp: new Date()
      }
      setMessages(prev => [...prev, thinkingMessage])

      // 调用API生成游戏（带模型选择）
      const response = await generateGame(inputValue, { model })
      
      // 移除思考中消息
      setMessages(prev => prev.filter(m => m.id !== thinkingMessage.id))

      if (response.success) {
        console.log('API Response:', response)
        console.log('gameData type:', typeof response.gameData)
        
        // 组装响应卡片内容：包含Agent来源与模型名
        const sourceLabel = response.agentSource === 'llm' ? '大模型实时生成' : '系统内置'
        const modelLabel = response.agentSource === 'llm' ? (response.modelName || model) : undefined

        const agentInfoLines = [
          `- Agent：${response.agentName || '未知'}（${sourceLabel}）`,
          ...(modelLabel ? [`- 模型：${modelLabel}`] : [])
        ].join('\n')

        const assistantMessage: Message = {
          id: Date.now().toString() + '-response',
          type: 'assistant',
          content: `✨ 游戏生成成功！\n\n**游戏信息：**\n- 类型：${response.config?.gameType}\n- 年龄组：${response.config?.ageGroup}\n- 难度：${response.config?.difficulty}\n- 主题：${response.config?.theme}\n${agentInfoLines}\n\n点击右侧预览区域查看游戏效果！`,
          timestamp: new Date()
        }
        setMessages(prev => [...prev, assistantMessage])
        
        // gameData已经包含html字段，直接传递
        if (response.gameData && response.gameData.html) {
          console.log('Passing gameData with html to onGameGenerated')
          onGameGenerated(response.gameData)
        } else if (typeof response.gameData === 'string') {
          // 如果gameData是字符串，包装成对象
          console.log('Wrapping string gameData')
          onGameGenerated({ html: response.gameData })
        } else {
          console.error('Unexpected gameData format:', response.gameData)
          onGameGenerated(response.gameData)
        }
      } else {
        throw new Error(response.error || '生成失败')
      }
    } catch (error: any) {
      message.error('游戏生成失败：' + error.message)
      const errorMessage: Message = {
        id: Date.now().toString() + '-error',
        type: 'assistant',
        content: '❌ 抱歉，游戏生成失败了。请稍后重试或换个描述试试。',
        timestamp: new Date()
      }
      setMessages(prev => prev.filter(m => !m.id.includes('thinking')).concat(errorMessage))
    } finally {
      setGenerating(false)
      setLoading(false)
    }
  }

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const suggestedPrompts = [
    "给5岁孩子做一个认识数字的游戏",
    "创建一个学习颜色的记忆游戏",
    "做一个简单的英语单词拼写游戏",
    "生成一个动物主题的数学游戏"
  ]

  return (
    <Card
      className="chat-container"
      title="对话交互"
      bordered={false}
      extra={
        <Tooltip title="选择生成模型">
          <Select
            size="small"
            value={model}
            onChange={setModel}
            style={{ width: 200 }}
            options={[
              { label: '通义千问（DashScope）', value: 'dashscope' },
              { label: 'Moonshot-Kimi-K2-Instruct（百炼）', value: 'kimi-k2' },
              { label: 'Qwen3 Coder Plus（百炼）', value: 'qwen3-coder-plus' },
              { label: 'DeepSeek（百炼）', value: 'deepseek' }
            ]}
          />
        </Tooltip>
      }
    >
      <div className="messages-list">
        <List
          dataSource={messages}
          renderItem={item => (
            <List.Item className={`message-item ${item.type}`}>
              <div className="message-content">
                <Avatar 
                  icon={item.type === 'user' ? <UserOutlined /> : <RobotOutlined />}
                  style={{ 
                    backgroundColor: item.type === 'user' ? '#87d068' : '#5e72e4' 
                  }}
                />
                <div className="message-bubble">
                  <ReactMarkdown>{item.content}</ReactMarkdown>
                </div>
              </div>
            </List.Item>
          )}
        />
        {generating && (
          <div className="generating-indicator">
            <Spin size="small" /> 正在生成游戏...
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <div className="suggested-prompts">
        {messages.length === 1 && (
          <>
            <div className="prompts-label">快速开始：</div>
            <div className="prompts-list">
              {suggestedPrompts.map((prompt, index) => (
                <Button
                  key={index}
                  size="small"
                  onClick={() => setInputValue(prompt)}
                  className="prompt-chip"
                >
                  {prompt}
                </Button>
              ))}
            </div>
          </>
        )}
      </div>

      <div className="input-area">
        <TextArea
          value={inputValue}
          onChange={e => setInputValue(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder="描述你想要的游戏..."
          autoSize={{ minRows: 2, maxRows: 4 }}
          disabled={generating}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={generating}
          disabled={!inputValue.trim()}
          className="send-button"
        >
          发送
        </Button>
      </div>
    </Card>
  )
}

export default ChatInterface
