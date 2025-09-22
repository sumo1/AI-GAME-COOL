/*
 * @since: 2025/8/28
 * @author: sumo
 * @description: 优化的游戏容器组件，提供自适应和美观的UI
 */
import React, { useEffect, useRef, useState } from 'react'
import { Card, message, Spin, Typography, Space, Tag, Button, Tooltip, Popover, Alert } from 'antd'
import {
  ReloadOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  SoundOutlined,
  CodeOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  SaveOutlined,
  HistoryOutlined
} from '@ant-design/icons'
import { gameStorage } from '../services/gameStorage'
import { serverStorage } from '../services/serverStorage'
import './GameContainer.css'

const { Title, Text } = Typography

interface GameContainerProps {
  gameData: any
  onRestart?: () => void
}

const GameContainer: React.FC<GameContainerProps> = ({ gameData, onRestart }) => {
  const containerRef = useRef<HTMLDivElement>(null)
  const iframeRef = useRef<HTMLIFrameElement>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [gameStatus, setGameStatus] = useState<'loading' | 'ready' | 'error' | 'empty'>('empty')
  const [gameScore, setGameScore] = useState({ correct: 0, wrong: 0, progress: 0 })
  const [suggestions, setSuggestions] = useState<{ level: 'info'|'warning'|'error'; text: string }[]>([])

  // 监听全屏变化
  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement)
    }
    document.addEventListener('fullscreenchange', handleFullscreenChange)
    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange)
    }
  }, [])

  // 加载游戏内容
  useEffect(() => {
    if (gameData?.html) {
      setIsLoading(true)
      setGameStatus('loading')
      
      // 处理游戏HTML，注入消息通信和自适应代码
      const enhancedHtml = injectGameEnhancements(gameData.html)
      // 自动分析改进建议（无需按钮触发）
      setSuggestions(analyzeGameHtml(gameData.html))
      
      // 使用srcdoc加载内容，避免弹窗问题
      if (iframeRef.current) {
        iframeRef.current.srcdoc = enhancedHtml
        
        // 监听iframe加载完成
        iframeRef.current.onload = () => {
          setIsLoading(false)
          setGameStatus('ready')
          setupMessageChannel()
        }
      }
    } else {
      setGameStatus('empty')
    }
  }, [gameData])

  // 基于静态规则的轻量建议生成
  const analyzeGameHtml = (html: string) => {
    const s: { level: 'info'|'warning'|'error'; text: string }[] = []
    const lower = html.toLowerCase()

    // 1) 说明与控件一致性
    const mentionsButtons = /左右按钮|点击左右|按下左右|left\s*button|right\s*button/i.test(html)
    const hasLeftRightButtons = /<button[^>]*>[^<]*左|<button[^>]*>[^<]*右/i.test(html)
    if (mentionsButtons && !hasLeftRightButtons) {
      s.push({ level: 'warning', text: '说明提到“左右按钮”，但页面未检测到对应的可点击按钮。建议补充可视化的左/右按钮，并保留键盘方向键支持。' })
    }

    // 2) 键盘方向键支持
    const hasArrowKeys = /ArrowLeft|ArrowRight/.test(html)
    if (!hasArrowKeys) {
      s.push({ level: 'info', text: '未检测到键盘方向键(ArrowLeft/ArrowRight)事件，建议同时支持键盘操作以提升可玩性。' })
    }

    // 3) 碰撞检测方式
    const usesAABB = /getBoundingClientRect\(\)/.test(html) && /(left|right|top|bottom)/i.test(html)
    const usesDistance = /Math\.abs\([^)]*\)\s*<\s*\d+/.test(html)
    if (!usesAABB && usesDistance) {
      s.push({ level: 'warning', text: '碰撞检测疑似使用固定距离阈值。建议改为轴对齐矩形相交（AABB）以获得更稳定的判定。' })
    }

    // 4) 尺寸比例与容器
    const setsBodyLayout = /body\s*\{[^}]*?(display\s*:\s*flex|overflow\s*:\s*hidden)/i.test(html)
    if (setsBodyLayout) {
      s.push({ level: 'info', text: '检测到对 <body> 设置了全局布局（flex/overflow）。建议将布局限制在游戏容器内（如 .game-area/#game-container），避免影响宿主页面。' })
    }

    // 5) 统一尺寸提示
    const hasGameArea = /(class=\"game-area\")|(id=\"game-container\")/i.test(html)
    if (!hasGameArea) {
      s.push({ level: 'info', text: '未检测到标准的游戏容器(.game-area 或 #game-container)。建议添加统一容器，便于自适应与样式隔离。' })
    }

    // 6) 元信息与编码
    if (!/charset=.*utf-8/i.test(html)) {
      s.push({ level: 'warning', text: '未检测到 UTF-8 编码声明，建议在 <head> 中加入 <meta charset="UTF-8">。' })
    }

    return s
  }

  // 注入游戏增强代码 - 最小化干扰
  const injectGameEnhancements = (html: string) => {
    // 只添加必要的viewport和消息通信
    const enhancements = `
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <meta charset="UTF-8">
      <script>
        // 简单的消息通信
        window.alert = function(msg) {
          window.parent.postMessage({
            type: 'game-alert',
            message: msg
          }, '*');
        };

        window.confirm = function(msg) {
          window.parent.postMessage({
            type: 'game-confirm',
            message: msg
          }, '*');
          return true;
        };

        // 最小化的响应式处理
        document.addEventListener('DOMContentLoaded', function() {
          // 只在iOS设备上做基础调整
          if (/iPad|iPhone|iPod/.test(navigator.userAgent)) {
            document.body.style.margin = '0';
            document.body.style.padding = '0';

            // 如果有canvas，稍微调整大小
            const canvas = document.querySelector('canvas');
            if (canvas) {
              canvas.style.maxWidth = '100%';
              canvas.style.height = 'auto';
            }
          }
        });
      </script>
      <style>
        /* 最小化样式 - 只做基础重置 */
        body {
          margin: 0;
          padding: 20px;
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        }

        /* 确保游戏居中 */
        .game-container, #game-container {
          max-width: 800px;
          margin: 0 auto;
        }
      </style>
    `
    
    // 在</head>前插入增强代码
    if (html.includes('</head>')) {
      return html.replace('</head>', enhancements + '</head>')
    } else if (html.includes('<body>')) {
      return html.replace('<body>', '<head>' + enhancements + '</head><body>')
    }
    
    return enhancements + html
  }

  // 设置消息通道
  const setupMessageChannel = () => {
    const handleMessage = (event: MessageEvent) => {
      if (event.data?.type === 'game-alert') {
        // 使用Ant Design的message替代alert
        message.info(event.data.message)
      } else if (event.data?.type === 'game-confirm') {
        // 使用Ant Design的message替代confirm
        message.warning(event.data.message)
      } else if (event.data?.type === 'game-status') {
        // 处理游戏状态更新
        if (event.data.status === 'score-update') {
          handleScoreUpdate(event.data.data)
        }
      }
    }
    
    window.addEventListener('message', handleMessage)
    
    return () => {
      window.removeEventListener('message', handleMessage)
    }
  }

  // 处理分数更新
  const handleScoreUpdate = (data: any) => {
    // 解析分数数据
    if (data?.score) {
      const scoreText = data.score.toString()
      const correctMatch = scoreText.match(/正确[:\s]*(\d+)/i)
      const wrongMatch = scoreText.match(/错误[:\s]*(\d+)/i)
      const progressMatch = scoreText.match(/进度[:\s]*(\d+)/i)
      
      setGameScore({
        correct: correctMatch ? parseInt(correctMatch[1]) : gameScore.correct,
        wrong: wrongMatch ? parseInt(wrongMatch[1]) : gameScore.wrong,
        progress: progressMatch ? parseInt(progressMatch[1]) : gameScore.progress
      })
    }
  }

  // 刷新游戏
  const handleRefresh = () => {
    if (iframeRef.current && gameData?.html) {
      setIsLoading(true)
      const enhancedHtml = injectGameEnhancements(gameData.html)
      iframeRef.current.srcdoc = enhancedHtml
    }
  }

  // 全屏切换
  const toggleFullscreen = () => {
    if (!isFullscreen) {
      containerRef.current?.requestFullscreen()
    } else {
      document.exitFullscreen()
    }
  }

  // 导出HTML
  const handleExport = () => {
    if (gameData?.html) {
      const blob = new Blob([gameData.html], { type: 'text/html;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${gameData.gameData?.title || '游戏'}.html`
      a.click()
      URL.revokeObjectURL(url)
      message.success('游戏已导出')
    }
  }

  // 保存游戏到本地存储
  const handleSaveGame = () => {
    if (!gameData?.html) {
      message.warning('没有可保存的游戏')
      return
    }

    try {
      const gameId = gameStorage.saveGame(gameData)
      message.success('游戏已保存到浏览器')

      // 可选：保存成功后显示游戏ID或其他信息
      console.log('保存的游戏ID:', gameId)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败')
    }
  }

  // 保存游戏到服务器
  const handleSaveToServer = async () => {
    if (!gameData?.html) {
      message.warning('没有可保存的游戏')
      return
    }

    const hide = message.loading('正在保存到服务器...', 0)

    try {
      const result = await serverStorage.saveGame(gameData)

      hide()

      if (result.success) {
        message.success(result.message || '游戏已保存到服务器')
        console.log('服务器保存的游戏ID:', result.gameId)
      } else {
        message.error(result.message || '保存到服务器失败')
      }
    } catch (error) {
      hide()
      message.error('保存到服务器失败')
    }
  }

  // 获取游戏信息
  const gameInfo = gameData?.gameData || {}
  const isAIGenerated = gameInfo.generated === true

  return (
    <div className="game-container" ref={containerRef}>
      <Card
        className="game-card"
        title={
          <div className="game-header">
            <Title level={4} className="game-title">
              🎮 {gameInfo.title || '游戏加载中'}
            </Title>
            <div className="game-meta">
              <Space>
                {isAIGenerated && (
                  <Tag color="purple" icon={<CheckCircleOutlined />}>
                    AI生成
                  </Tag>
                )}
                {gameInfo.type && (
                  <Tag color="blue">{gameInfo.type}</Tag>
                )}
                {gameStatus === 'ready' && (
                  <Tag color="green" icon={<PlayCircleOutlined />}>
                    就绪
                  </Tag>
                )}
              </Space>
            </div>
          </div>
        }
        extra={
          <Space className="game-controls">
            {/* 分数显示 */}
            {(gameScore.correct > 0 || gameScore.wrong > 0) && (
              <div className="score-display">
                <Space>
                  <Tag color="green">✓ 正确: {gameScore.correct}</Tag>
                  <Tag color="red">✗ 错误: {gameScore.wrong}</Tag>
                  <Tag color="blue">📊 进度: {gameScore.progress}/10</Tag>
                </Space>
              </div>
            )}
            
            {/* 控制按钮 */}
            <Tooltip title="刷新游戏">
              <Button 
                icon={<ReloadOutlined />}
                onClick={handleRefresh}
                loading={isLoading}
              />
            </Tooltip>
            
            <Tooltip title={isFullscreen ? "退出全屏" : "全屏"}>
              <Button
                icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
                onClick={toggleFullscreen}
              />
            </Tooltip>

            <Tooltip title="保存到浏览器">
              <Button
                icon={<SaveOutlined />}
                onClick={handleSaveGame}
                type="default"
              />
            </Tooltip>

            <Tooltip title="保存到服务器">
              <Button
                icon={<SaveOutlined />}
                onClick={handleSaveToServer}
                type="primary"
                style={{ background: '#52c41a', borderColor: '#52c41a' }}
              >
                服务器
              </Button>
            </Tooltip>

            <Tooltip title="导出HTML">
              <Button
                icon={<CodeOutlined />}
                onClick={handleExport}
                type="primary"
              />
            </Tooltip>

            {/* 改进建议（不显眼的小入口） */}
            {suggestions.length > 0 && (
              <Popover
                placement="bottomRight"
                content={
                  <Space direction="vertical" style={{ maxWidth: 360 }}>
                    {suggestions.map((sg, idx) => (
                      <Alert key={idx} type={sg.level === 'error' ? 'error' : sg.level === 'warning' ? 'warning' : 'info'} showIcon message={sg.text} />
                    ))}
                  </Space>
                }
                trigger="click"
              >
                <Button size="small" type="text">改进建议（{suggestions.length}）</Button>
              </Popover>
            )}
          </Space>
        }
      >
        <div className="game-content">
          {/* 建议入口已移到头部控件区域，避免抢占视线 */}
          {gameStatus === 'empty' && (
            <div className="game-empty">
              <Title level={3}>🎮 奇妙游戏世界</Title>
              <Text type="secondary">欢迎来到贪吃蛇冒险之旅！吃苹果，别撞墙或撞自己哦！</Text>
              <Button 
                type="primary" 
                size="large"
                icon={<PlayCircleOutlined />}
                onClick={onRestart}
                style={{ marginTop: 20 }}
              >
                开始游戏
              </Button>
            </div>
          )}
          
          {gameStatus === 'loading' && (
            <div className="game-loading">
              <Spin size="large" tip="游戏加载中..." />
            </div>
          )}
          
          {gameData?.html && (
            <div className="game-iframe-wrapper">
              <iframe
                ref={iframeRef}
                className="game-iframe"
                title="Game"
                sandbox="allow-scripts allow-same-origin allow-forms allow-modals allow-popups allow-presentation allow-top-navigation-by-user-activation"
                allow="accelerometer; gyroscope; autoplay"
                style={{
                  display: isLoading ? 'none' : 'block'
                }}
              />
            </div>
          )}
        </div>
      </Card>
    </div>
  )
}

export default GameContainer
