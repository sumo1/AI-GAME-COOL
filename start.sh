#!/bin/bash
# 
# 游戏Agent框架一键启动脚本
# @author: sumo
# @since: 2025/8/11
#

echo "================================================"
echo "🎮 儿童游戏生成Agent框架启动脚本"
echo "================================================"

# 检查Java环境
# 加载环境变量
if [ -f .env ]; then
    echo "📄 加载环境变量..."
    export $(cat .env | xargs)
fi

echo "📋 检查环境..."
if ! command -v java &> /dev/null; then
    echo "❌ 未找到Java，请先安装Java 17或更高版本"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "❌ 未找到Maven，请先安装Maven"
    exit 1
fi

if ! command -v npm &> /dev/null; then
    echo "❌ 未找到npm，请先安装Node.js"
    exit 1
fi

echo "✅ 环境检查通过"

# 选择存储方式
echo ""
echo "请选择RAG存储方式："
echo "1) Elasticsearch (推荐，需要Docker)"
echo "2) 内存存储 (简单，重启后数据丢失)"
echo "3) 不使用RAG"
read -p "请输入选项 (1-3) [默认: 2]: " storage_choice

# 默认选择内存存储
if [ -z "$storage_choice" ]; then
    storage_choice="2"
fi

case $storage_choice in
    1)
        echo ""
        echo "📦 使用Elasticsearch存储"
        
        # 检查Docker
        if ! command -v docker &> /dev/null; then
            echo "❌ 未找到Docker，无法使用Elasticsearch"
            echo "请安装Docker后重试，或选择其他存储方式"
            exit 1
        fi
        
        # 启动Elasticsearch
        echo "🔧 启动Elasticsearch..."
        docker-compose up -d elasticsearch
        
        echo "⏳ 等待Elasticsearch启动..."
        for i in {1..30}; do
            if curl -s http://localhost:9200 > /dev/null 2>&1; then
                echo "✅ Elasticsearch启动成功"
                export AGENT_RAG_TYPE=elasticsearch
                export AGENT_RAG_ENABLED=true
                break
            fi
            echo -n "."
            sleep 2
        done
        
        if [ "$AGENT_RAG_TYPE" != "elasticsearch" ]; then
            echo ""
            echo "❌ Elasticsearch启动失败"
            echo "请检查Docker服务或选择其他存储方式"
            exit 1
        fi
        ;;
    2)
        echo ""
        echo "💾 使用内存存储"
        export AGENT_RAG_TYPE=memory
        export AGENT_RAG_ENABLED=true
        ;;
    3)
        echo ""
        echo "⚡ 不使用RAG，仅使用LLM基础能力"
        export AGENT_RAG_TYPE=none
        export AGENT_RAG_ENABLED=false
        ;;
    *)
        echo "❌ 无效选择"
        exit 1
        ;;
esac

echo ""
# 代理设置（可选）
read -p "是否为模型访问启用代理? (y/N): " enable_proxy
if [[ "$enable_proxy" == "y" || "$enable_proxy" == "Y" ]]; then
  read -p "代理类型 [http/socks5] (默认 http): " proxy_type_input
  read -p "代理主机 (默认 127.0.0.1): " proxy_host_input
  read -p "代理端口 (默认 8001): " proxy_port_input

  export PROXY_ENABLED=true
  export PROXY_TYPE=${proxy_type_input:-http}
  export PROXY_HOST=${proxy_host_input:-127.0.0.1}
  export PROXY_PORT=${proxy_port_input:-8001}

  echo "🌐 已启用代理: $PROXY_TYPE://$PROXY_HOST:$PROXY_PORT"
else
  export PROXY_ENABLED=false
  echo "🌐 未启用代理，直连访问模型服务"
fi

find_free_port() {
  local start_port=${1:-8088}
  local end_port=${2:-8098}
  for p in $(seq $start_port $end_port); do
    if ! lsof -nP -iTCP:$p -sTCP:LISTEN >/dev/null 2>&1; then
      echo $p
      return 0
    fi
  done
  return 1
}

# 选择后端端口（优先8088，不可用则向后查找）
BACKEND_PORT=$(find_free_port 8088 8098)
if [ -z "$BACKEND_PORT" ]; then
  echo "❌ 未找到可用端口(8088-8098)，请释放端口后重试"
  exit 1
fi

# 启动后端
echo ""
echo "🚀 启动后端服务 (端口: $BACKEND_PORT)..."
cd game-agent-backend

# 检查是否需要编译
if [ ! -d "target" ]; then
    echo "📦 首次运行，正在编译后端..."
    mvn clean package -DskipTests
fi

# 启动Spring Boot（通过环境变量指定端口）
echo "▶️ 启动Spring Boot..."
SERVER_PORT=$BACKEND_PORT mvn spring-boot:run &
BACKEND_PID=$!

# 等待后端启动
echo "⏳ 等待后端服务启动..."
for i in {1..30}; do
  if curl -s http://localhost:$BACKEND_PORT/api/game/agents >/dev/null 2>&1; then
    echo "✅ 后端服务启动成功"
    break
  fi
  sleep 1
done
if ! curl -s http://localhost:$BACKEND_PORT/api/game/agents >/dev/null 2>&1; then
  echo "❌ 后端服务启动失败，请检查日志"
  exit 1
fi

# 启动前端
echo ""
echo "🎨 启动前端服务..."
cd ../game-agent-frontend

# 安装依赖（如果需要）
if [ ! -d "node_modules" ]; then
    echo "📦 首次运行，正在安装前端依赖..."
    npm install
fi

# 启动前端开发服务器（同步后端地址给Vite代理）
echo "▶️ 启动前端开发服务器..."
BACKEND_URL=http://localhost:$BACKEND_PORT npm run dev &
FRONTEND_PID=$!

# 等待前端启动
sleep 5

echo ""
echo "================================================"
echo "✨ 游戏Agent框架启动成功！"
echo "================================================"
echo "📍 前端地址: http://localhost:5173"
echo "📍 后端API: http://localhost:$BACKEND_PORT/api/game"
if [ "$AGENT_RAG_TYPE" = "elasticsearch" ]; then
    echo "📍 Elasticsearch: http://localhost:9200"
    echo "📍 ES管理工具: ./es-manage.sh"
fi
echo ""
echo "📌 使用说明："
echo "   1. 打开浏览器访问 http://localhost:5173"
echo "   2. 在对话框中描述你想要的游戏"
echo "   3. 等待AI生成游戏"
echo "   4. 在右侧预览区查看游戏效果"
echo ""
echo "🛑 按 Ctrl+C 停止所有服务"
echo "================================================"

# 等待用户中断
trap "echo '正在停止服务...'; kill $BACKEND_PID $FRONTEND_PID; docker-compose down; exit" INT

# 保持脚本运行
wait $BACKEND_PID $FRONTEND_PID
