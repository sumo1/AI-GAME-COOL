/*
 * @since: 2025/8/11
 * @author: sumo
 */
import React, { useState, useEffect } from 'react'
import { Layout, Typography, ConfigProvider, theme, Button, Drawer } from 'antd'
import { MenuFoldOutlined, MenuUnfoldOutlined, MessageOutlined, CloseOutlined, HistoryOutlined, CloudServerOutlined } from '@ant-design/icons'
import ChatInterface from './components/ChatInterface'
import GamePreview from './components/GamePreview'
import GameHistory from './components/GameHistory'
import ServerGameHistory from './components/ServerGameHistory'
import './styles/App.css'

const { Header, Content, Sider } = Layout
const { Title } = Typography

const App: React.FC = () => {
  const [gameData, setGameData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [gameKey, setGameKey] = useState(0)
  const [chatCollapsed, setChatCollapsed] = useState(false)
  const [isMobile, setIsMobile] = useState(false)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [historyVisible, setHistoryVisible] = useState(false)
  const [serverHistoryVisible, setServerHistoryVisible] = useState(false)

  // 检测是否为移动设备
  useEffect(() => {
    const checkMobile = () => {
      const mobile = window.innerWidth <= 768
      setIsMobile(mobile)
      // 移动端默认收起聊天框
      if (mobile && gameData) {
        setChatCollapsed(true)
      }
    }

    checkMobile()
    window.addEventListener('resize', checkMobile)
    return () => window.removeEventListener('resize', checkMobile)
  }, [gameData])

  const handleGameGenerated = (data: any) => {
    console.log('App.tsx received data:', data)
    // 强制重新渲染GamePreview组件
    setGameKey(prev => prev + 1)
    setGameData(data)

    // 移动端生成游戏后自动收起聊天框
    if (isMobile) {
      setChatCollapsed(true)
      setDrawerVisible(false)
    }
  }

  // 从历史记录加载游戏
  const handleLoadGame = (data: any) => {
    console.log('Loading game from history:', data)
    setGameKey(prev => prev + 1)
    setGameData(data)
    setHistoryVisible(false)
  }

  // 移动端使用Drawer，桌面端使用折叠
  const renderChatSection = () => {
    const chatComponent = (
      <ChatInterface
        onGameGenerated={handleGameGenerated}
        setLoading={setLoading}
      />
    )

    if (isMobile) {
      return (
        <>
          <Button
            className="mobile-chat-toggle"
            icon={<MessageOutlined />}
            onClick={() => setDrawerVisible(true)}
            type="primary"
            size="large"
            style={{
              position: 'fixed',
              bottom: 20,
              right: 20,
              zIndex: 1000,
              borderRadius: '50%',
              width: 56,
              height: 56,
              display: gameData ? 'flex' : 'none',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          />
          <Drawer
            title="对话助手"
            placement="bottom"
            onClose={() => setDrawerVisible(false)}
            open={drawerVisible}
            height="70%"
            className="mobile-chat-drawer"
            closeIcon={<CloseOutlined />}
          >
            {chatComponent}
          </Drawer>
        </>
      )
    }

    return (
      <div className={`chat-section ${chatCollapsed ? 'collapsed' : ''}`}>
        {!chatCollapsed ? (
          chatComponent
        ) : (
          <div className="chat-collapsed-bar">
            <Button
              icon={<MenuUnfoldOutlined />}
              onClick={() => setChatCollapsed(false)}
              type="text"
              size="large"
            />
            <span className="collapsed-text">对话</span>
          </div>
        )}
      </div>
    )
  }

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#5e72e4',
          borderRadius: 8,
        },
      }}
    >
      <Layout className="app-layout">
        <Header className="app-header">
          <div className="header-content">
            <Title level={2} style={{ color: 'white', margin: 0 }}>
              🎮 儿童游戏生成助手
            </Title>
            <span className="header-subtitle">
              通过对话快速创建有趣的教育游戏
            </span>
            <div style={{ marginLeft: 'auto', display: 'flex', gap: '8px' }}>
              <Button
                icon={<HistoryOutlined />}
                onClick={() => setHistoryVisible(true)}
                type="text"
                style={{ color: 'white' }}
                title="本地历史"
              >
                {!isMobile && '本地'}
              </Button>
              <Button
                icon={<CloudServerOutlined />}
                onClick={() => setServerHistoryVisible(true)}
                type="text"
                style={{ color: 'white' }}
                title="服务器存储"
              >
                {!isMobile && '服务器'}
              </Button>
              {!isMobile && gameData && (
                <Button
                  className="chat-toggle-btn"
                  icon={chatCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                  onClick={() => setChatCollapsed(!chatCollapsed)}
                  type="text"
                  style={{ color: 'white' }}
                />
              )}
            </div>
          </div>
        </Header>

        <Layout>
          <Content className="main-content">
            <div className="content-wrapper">
              {!isMobile && renderChatSection()}

              {isMobile && !gameData && (
                <div className="chat-section full-width">
                  <ChatInterface
                    onGameGenerated={handleGameGenerated}
                    setLoading={setLoading}
                  />
                </div>
              )}

              {gameData && (
                <div className={`preview-section ${isMobile || chatCollapsed ? 'expanded' : ''}`}>
                  <GamePreview key={gameKey} gameData={gameData} />
                </div>
              )}

              {isMobile && renderChatSection()}
            </div>
          </Content>
        </Layout>
      </Layout>

      {/* 本地游戏历史记录模态框 */}
      <GameHistory
        visible={historyVisible}
        onClose={() => setHistoryVisible(false)}
        onLoadGame={handleLoadGame}
      />

      {/* 服务器游戏历史记录模态框 */}
      <ServerGameHistory
        visible={serverHistoryVisible}
        onClose={() => setServerHistoryVisible(false)}
        onLoadGame={handleLoadGame}
      />
    </ConfigProvider>
  )
}

export default App