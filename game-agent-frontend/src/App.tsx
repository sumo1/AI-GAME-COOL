/*
 * @since: 2025/8/11
 * @author: sumo
 */
import React, { useState } from 'react'
import { Layout, Typography, ConfigProvider, theme } from 'antd'
import ChatInterface from './components/ChatInterface'
import GamePreview from './components/GamePreview'
import './styles/App.css'

const { Header, Content, Sider } = Layout
const { Title } = Typography

const App: React.FC = () => {
  const [gameData, setGameData] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [gameKey, setGameKey] = useState(0)

  const handleGameGenerated = (data: any) => {
    console.log('App.tsx received data:', data)
    // 强制重新渲染GamePreview组件
    setGameKey(prev => prev + 1)
    setGameData(data)
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
          </div>
        </Header>
        
        <Layout>
          <Content className="main-content">
            <div className="content-wrapper">
              <div className="chat-section">
                <ChatInterface 
                  onGameGenerated={handleGameGenerated}
                  setLoading={setLoading}
                />
              </div>
              
              {gameData && (
                <div className="preview-section">
                  <GamePreview key={gameKey} gameData={gameData} />
                </div>
              )}
            </div>
          </Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  )
}

export default App