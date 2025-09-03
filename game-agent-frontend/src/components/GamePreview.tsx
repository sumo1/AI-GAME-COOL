/*
 * @since: 2025/8/11
 * @author: sumo
 */
import React from 'react'
import GameContainer from './GameContainer'

interface GamePreviewProps {
  gameData: any
}

const GamePreview: React.FC<GamePreviewProps> = ({ gameData }) => {
  return <GameContainer gameData={gameData} />
}

export default GamePreview