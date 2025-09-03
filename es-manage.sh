#!/bin/bash
#
# Elasticsearch管理脚本
# @author: sumo
# @since: 2025/8/11
#

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Elasticsearch地址
ES_HOST="localhost"
ES_PORT="9200"
ES_URL="http://${ES_HOST}:${ES_PORT}"

# 显示菜单
show_menu() {
    echo ""
    echo "================================================"
    echo "🔍 Elasticsearch管理工具"
    echo "================================================"
    echo "1. 启动 Elasticsearch"
    echo "2. 停止 Elasticsearch"
    echo "3. 重启 Elasticsearch"
    echo "4. 查看状态"
    echo "5. 查看索引列表"
    echo "6. 查看知识库统计"
    echo "7. 清空知识库"
    echo "8. 测试向量搜索"
    echo "9. 查看日志"
    echo "0. 退出"
    echo "================================================"
}

# 启动Elasticsearch
start_es() {
    echo -e "${GREEN}🚀 启动Elasticsearch...${NC}"
    docker-compose up -d elasticsearch
    
    echo -e "${YELLOW}⏳ 等待Elasticsearch启动...${NC}"
    for i in {1..30}; do
        if curl -s ${ES_URL} > /dev/null; then
            echo -e "${GREEN}✅ Elasticsearch启动成功！${NC}"
            echo "访问地址: ${ES_URL}"
            return 0
        fi
        echo -n "."
        sleep 2
    done
    
    echo -e "${RED}❌ Elasticsearch启动超时${NC}"
    return 1
}

# 停止Elasticsearch
stop_es() {
    echo -e "${YELLOW}🛑 停止Elasticsearch...${NC}"
    docker-compose stop elasticsearch
    echo -e "${GREEN}✅ Elasticsearch已停止${NC}"
}

# 重启Elasticsearch
restart_es() {
    stop_es
    sleep 2
    start_es
}

# 查看状态
check_status() {
    echo -e "${GREEN}📊 Elasticsearch状态：${NC}"
    
    # 检查容器状态
    echo -n "容器状态: "
    if docker ps | grep -q game-agent-es; then
        echo -e "${GREEN}运行中${NC}"
    else
        echo -e "${RED}未运行${NC}"
        return 1
    fi
    
    # 检查健康状态
    echo -n "健康状态: "
    if curl -s ${ES_URL}/_cluster/health | grep -q '"status":"green"\|"status":"yellow"'; then
        echo -e "${GREEN}健康${NC}"
    else
        echo -e "${YELLOW}检查中...${NC}"
    fi
    
    # 显示集群信息
    echo ""
    echo "集群信息:"
    curl -s ${ES_URL} | jq '.'
}

# 查看索引列表
list_indices() {
    echo -e "${GREEN}📚 索引列表：${NC}"
    curl -s ${ES_URL}/_cat/indices?v
}

# 查看知识库统计
show_stats() {
    echo -e "${GREEN}📊 知识库统计：${NC}"
    
    # 文档数量
    echo -n "文档总数: "
    curl -s ${ES_URL}/game_knowledge/_count | jq '.count'
    
    # 按类型统计
    echo ""
    echo "按类型统计:"
    curl -s -X GET "${ES_URL}/game_knowledge/_search" -H 'Content-Type: application/json' -d '{
      "size": 0,
      "aggs": {
        "types": {
          "terms": {
            "field": "type"
          }
        }
      }
    }' | jq '.aggregations.types.buckets'
}

# 清空知识库
clear_knowledge() {
    echo -e "${YELLOW}⚠️  警告：此操作将删除所有知识库数据！${NC}"
    read -p "确定要继续吗？(y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        curl -X DELETE ${ES_URL}/game_knowledge
        echo -e "${GREEN}✅ 知识库已清空${NC}"
    else
        echo "操作已取消"
    fi
}

# 测试向量搜索
test_search() {
    echo -e "${GREEN}🔍 测试向量搜索${NC}"
    echo "请输入搜索内容（如：6岁数学游戏）："
    read query
    
    echo ""
    echo "搜索结果："
    curl -s -X GET "${ES_URL}/game_knowledge/_search" -H 'Content-Type: application/json' -d "{
      \"query\": {
        \"match\": {
          \"content\": \"${query}\"
        }
      },
      \"size\": 3
    }" | jq '.hits.hits[] | {id: ._id, score: ._score, content: ._source.content}'
}

# 查看日志
view_logs() {
    echo -e "${GREEN}📜 Elasticsearch日志（最后50行）：${NC}"
    docker logs --tail 50 game-agent-es
}

# 主循环
while true; do
    show_menu
    read -p "请选择操作 (0-9): " choice
    
    case $choice in
        1) start_es ;;
        2) stop_es ;;
        3) restart_es ;;
        4) check_status ;;
        5) list_indices ;;
        6) show_stats ;;
        7) clear_knowledge ;;
        8) test_search ;;
        9) view_logs ;;
        0) 
            echo "再见！"
            exit 0
            ;;
        *)
            echo -e "${RED}无效选择，请重试${NC}"
            ;;
    esac
    
    echo ""
    read -p "按Enter键继续..."
done