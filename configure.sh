#!/bin/bash
#
# 配置助手脚本
# @author: sumo
# @since: 2025/8/11
#

echo "================================================"
echo "🔧 游戏Agent框架配置助手"
echo "================================================"

# 创建.env文件
ENV_FILE=".env"

echo "请选择AI服务提供商："
echo "1) OpenAI"
echo "2) 阿里云通义千问"
echo "3) Azure OpenAI"
echo "4) 自定义兼容端点"
read -p "请输入选项 (1-4): " provider_choice

case $provider_choice in
  1)
    echo "配置OpenAI..."
    read -p "请输入OpenAI API Key: " api_key
    echo "ALIYUN_API_KEY=$api_key" > $ENV_FILE
    echo "OPENAI_BASE_URL=https://api.openai.com" >> $ENV_FILE
    echo "AI_MODEL=gpt-3.5-turbo" >> $ENV_FILE
    ;;
  2)
    echo "配置阿里云通义千问..."
    read -p "请输入API Key: " api_key
    echo "ALIYUN_API_KEY=$api_key" > $ENV_FILE
    echo "OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1" >> $ENV_FILE
    echo "AI_MODEL=qwen-plus" >> $ENV_FILE
    ;;
  3)
    echo "配置Azure OpenAI..."
    read -p "请输入Azure API Key: " api_key
    read -p "请输入Azure Endpoint: " endpoint
    echo "ALIYUN_API_KEY=$api_key" > $ENV_FILE
    echo "OPENAI_BASE_URL=$endpoint" >> $ENV_FILE
    echo "AI_MODEL=gpt-35-turbo" >> $ENV_FILE
    ;;
  4)
    echo "配置自定义端点..."
    read -p "请输入API Key: " api_key
    read -p "请输入API Base URL: " base_url
    read -p "请输入模型名称: " model
    echo "ALIYUN_API_KEY=$api_key" > $ENV_FILE
    echo "OPENAI_BASE_URL=$base_url" >> $ENV_FILE
    echo "AI_MODEL=$model" >> $ENV_FILE
    ;;
esac

# RAG存储配置
echo ""
echo "选择RAG存储方式："
echo "1) Elasticsearch (需要Docker，推荐生产环境)"
echo "2) 内存存储 (无需依赖，适合开发测试)"
echo "3) 不使用RAG (仅使用LLM)"
read -p "请输入选项 (1-3) [默认: 2]: " rag_choice

if [ -z "$rag_choice" ]; then
    rag_choice="2"
fi

case $rag_choice in
  1)
    echo "AGENT_RAG_ENABLED=true" >> $ENV_FILE
    echo "AGENT_RAG_TYPE=elasticsearch" >> $ENV_FILE
    
    # 询问Elasticsearch配置
    read -p "Elasticsearch地址 [localhost]: " es_host
    es_host=${es_host:-localhost}
    read -p "Elasticsearch端口 [9200]: " es_port
    es_port=${es_port:-9200}
    
    echo "ES_HOST=$es_host" >> $ENV_FILE
    echo "ES_PORT=$es_port" >> $ENV_FILE
    ;;
  2)
    echo "AGENT_RAG_ENABLED=true" >> $ENV_FILE
    echo "AGENT_RAG_TYPE=memory" >> $ENV_FILE
    ;;
  3)
    echo "AGENT_RAG_ENABLED=false" >> $ENV_FILE
    echo "AGENT_RAG_TYPE=none" >> $ENV_FILE
    ;;
esac

# 消息持久化配置
echo ""
read -p "是否启用消息持久化 (y/n)? " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "MESSAGE_PERSISTENCE_ENABLED=true" >> $ENV_FILE
else
    echo "MESSAGE_PERSISTENCE_ENABLED=false" >> $ENV_FILE
fi

# 生成配置文件
echo ""
echo "正在生成配置文件..."

# 更新application.yml
cat > game-agent-backend/src/main/resources/application-custom.yml << EOF
server:
  port: 8088

spring:
  application:
    name: game-agent-backend
  
  ai:
    openai:
      api-key: \${ALIYUN_API_KEY}
      base-url: \${OPENAI_BASE_URL}
      model: \${AI_MODEL:gpt-3.5-turbo}
  
  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
    mode: HTML
    encoding: UTF-8
    cache: false

agent:
  message:
    persistence:
      enabled: \${MESSAGE_PERSISTENCE_ENABLED:false}
  
  game:
    default-age-group: 6-8
    generation-timeout: 30
    template-path: classpath:/game-templates/

logging:
  level:
    com.sumo.agent: INFO
    org.springframework.ai: INFO
EOF

echo "✅ 配置完成！"
echo ""
echo "配置已保存到："
echo "  - .env (环境变量)"
echo "  - application-custom.yml (Spring配置)"
echo ""
echo "启动时会自动加载这些配置。"
echo ""
echo "现在可以运行 ./start.sh 启动项目了！"
echo "================================================"
