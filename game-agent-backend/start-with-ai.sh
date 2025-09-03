#!/bin/bash

echo "🎮 儿童游戏生成Agent - AI模式启动"
echo "================================"

# 检查是否已设置OPENAI_API_KEY
if [ -z "$OPENAI_API_KEY" ]; then
    echo "⚠️  未检测到OPENAI_API_KEY环境变量"
    echo ""
    echo "请输入您的OpenAI API Key："
    echo "（如果没有，请访问 https://platform.openai.com/api-keys 获取）"
    read -s OPENAI_API_KEY
    export OPENAI_API_KEY
    echo ""
fi

# 询问是否使用自定义API端点
echo "是否使用自定义API端点？(y/N)"
read -r USE_CUSTOM_ENDPOINT

if [[ "$USE_CUSTOM_ENDPOINT" == "y" ]] || [[ "$USE_CUSTOM_ENDPOINT" == "Y" ]]; then
    echo "请输入API端点URL (例如: https://api.openai-proxy.com):"
    read OPENAI_BASE_URL
    export OPENAI_BASE_URL
fi

echo ""
echo "✅ 配置完成，正在启动服务..."
echo "   API Key: ${OPENAI_API_KEY:0:10}..."
echo "   Base URL: ${OPENAI_BASE_URL:-https://api.openai.com}"
echo ""

# 编译项目
echo "📦 编译项目..."
mvn compile -q

# 启动服务
echo "🚀 启动后端服务..."
mvn spring-boot:run