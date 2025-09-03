#!/bin/bash
#
# 快速启动脚本（使用默认配置）
# @author: sumo
# @since: 2025/8/11
#

echo "================================================"
echo "🚀 快速启动游戏Agent框架"
echo "================================================"
echo "使用默认配置："
echo "  - RAG存储: 内存"
echo "  - 消息持久化: 关闭"
echo "================================================"

# 设置默认配置
export AGENT_RAG_TYPE=memory
export AGENT_RAG_ENABLED=true
export MESSAGE_PERSISTENCE_ENABLED=false

# 加载.env文件（如果存在）
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# 检查必要的API密钥
if [ -z "$OPENAI_API_KEY" ]; then
    echo "⚠️  未找到API密钥配置"
    echo "请先运行 ./configure.sh 进行配置"
    exit 1
fi

# 启动后端
echo "🚀 启动后端服务..."
cd game-agent-backend
mvn spring-boot:run &
BACKEND_PID=$!

# 等待后端启动
echo "⏳ 等待后端服务启动..."
sleep 15

# 启动前端
echo "🎨 启动前端服务..."
cd ../game-agent-frontend
npm run dev &
FRONTEND_PID=$!

sleep 5

echo ""
echo "================================================"
echo "✨ 框架启动成功！"
echo "📍 访问地址: http://localhost:5173"
echo "🛑 按 Ctrl+C 停止所有服务"
echo "================================================"

trap "kill $BACKEND_PID $FRONTEND_PID; exit" INT
wait $BACKEND_PID $FRONTEND_PID