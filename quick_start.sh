#!/bin/bash

#===============================================================================
# 🚀 AI-GAME 超轻量快速启动脚本
# 功能：一键检查环境、安装依赖、配置参数、启动服务
# 特点：零配置、自动修复、优雅降级、实时反馈
#===============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# 配置
BACKEND_PORT=8088
FRONTEND_PORT=5173
BACKEND_DIR="game-agent-backend"
FRONTEND_DIR="game-agent-frontend"
LOG_DIR="/tmp/ai-game-logs"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

# 创建日志目录
mkdir -p "$LOG_DIR"

#===============================================================================
# 工具函数
#===============================================================================

print_header() {
    echo -e "\n${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}${BOLD}  $1${NC}"
    echo -e "${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

print_step() {
    echo -e "${BLUE}▶${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${CYAN}ℹ${NC} $1"
}

spinner() {
    local pid=$1
    local delay=0.1
    local spinstr='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
    while [ "$(ps a | awk '{print $1}' | grep $pid)" ]; do
        local temp=${spinstr#?}
        printf " [%c]  " "$spinstr"
        local spinstr=$temp${spinstr%"$temp"}
        sleep $delay
        printf "\b\b\b\b\b\b"
    done
    printf "    \b\b\b\b"
}

check_command() {
    if command -v $1 &> /dev/null; then
        return 0
    else
        return 1
    fi
}

get_version() {
    local cmd=$1
    case $cmd in
        java)
            java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1
            ;;
        node)
            node -v | cut -d'v' -f2 | cut -d'.' -f1
            ;;
        mvn)
            mvn -v 2>/dev/null | head -n 1 | cut -d' ' -f3
            ;;
        npm)
            npm -v
            ;;
    esac
}

is_port_available() {
    ! lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null 2>&1
}

wait_for_service() {
    local url=$1
    local max_attempts=30
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        local http_code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
        if [[ "$http_code" == "200" || "$http_code" == "302" || "$http_code" == "304" ]]; then
            return 0
        fi
        sleep 1
        ((attempt++))
    done
    return 1
}

cleanup() {
    print_header "🔄 清理服务"

    # 停止后端
    if [ -f "$LOG_DIR/backend.pid" ]; then
        local pid=$(cat "$LOG_DIR/backend.pid")
        if ps -p $pid > /dev/null 2>&1; then
            print_step "停止后端服务 (PID: $pid)"
            kill $pid 2>/dev/null || true
            sleep 2
        fi
        rm -f "$LOG_DIR/backend.pid"
    fi

    # 停止前端
    if [ -f "$LOG_DIR/frontend.pid" ]; then
        local pid=$(cat "$LOG_DIR/frontend.pid")
        if ps -p $pid > /dev/null 2>&1; then
            print_step "停止前端服务 (PID: $pid)"
            kill $pid 2>/dev/null || true
        fi
        rm -f "$LOG_DIR/frontend.pid"
    fi

    print_success "服务已停止"
}

# 捕获退出信号
trap cleanup EXIT INT TERM

#===============================================================================
# 环境检查
#===============================================================================

check_environment() {
    print_header "🔍 环境检查"

    local errors=()
    local warnings=()

    # 检查 Java
    print_step "检查 Java..."
    if check_command java; then
        local java_version=$(get_version java)
        if [ "$java_version" -ge 17 ] 2>/dev/null; then
            print_success "Java $java_version 已安装"
        else
            print_warning "Java 版本 $java_version 低于推荐版本 17"
            warnings+=("Java 版本较低，建议升级到 17+")
        fi
    else
        print_error "Java 未安装"
        errors+=("需要安装 Java 17 或更高版本")
    fi

    # 检查 Maven
    print_step "检查 Maven..."
    if check_command mvn; then
        local mvn_version=$(get_version mvn)
        print_success "Maven $mvn_version 已安装"
    else
        print_error "Maven 未安装"
        errors+=("需要安装 Maven 3.6+")
    fi

    # 检查 Node.js
    print_step "检查 Node.js..."
    if check_command node; then
        local node_version=$(get_version node)
        if [ "$node_version" -ge 18 ] 2>/dev/null; then
            print_success "Node.js v$node_version 已安装"
        else
            print_warning "Node.js 版本较低"
            warnings+=("建议升级 Node.js 到 18+")
        fi
    else
        print_error "Node.js 未安装"
        errors+=("需要安装 Node.js 18+")
    fi

    # 检查 npm
    print_step "检查 npm..."
    if check_command npm; then
        local npm_version=$(get_version npm)
        print_success "npm $npm_version 已安装"
    else
        print_error "npm 未安装"
        errors+=("需要安装 npm")
    fi

    # 检查端口
    print_step "检查端口可用性..."
    if is_port_available $BACKEND_PORT; then
        print_success "后端端口 $BACKEND_PORT 可用"
    else
        print_warning "端口 $BACKEND_PORT 被占用，将尝试其他端口"
        BACKEND_PORT=$((BACKEND_PORT + 10))
    fi

    if is_port_available $FRONTEND_PORT; then
        print_success "前端端口 $FRONTEND_PORT 可用"
    else
        print_warning "端口 $FRONTEND_PORT 被占用，将尝试其他端口"
        FRONTEND_PORT=$((FRONTEND_PORT + 10))
    fi

    # 输出结果
    if [ ${#errors[@]} -gt 0 ]; then
        echo
        print_error "发现以下严重问题："
        for error in "${errors[@]}"; do
            echo "  • $error"
        done
        echo
        print_info "请安装缺失的依赖后重试"

        # 提供安装建议
        if [[ "$OSTYPE" == "darwin"* ]]; then
            print_info "macOS 用户可以使用 Homebrew 安装："
            echo "  brew install openjdk@17 maven node"
        elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
            print_info "Linux 用户可以使用包管理器安装："
            echo "  sudo apt-get install openjdk-17-jdk maven nodejs npm  # Ubuntu/Debian"
            echo "  sudo yum install java-17-openjdk maven nodejs npm      # CentOS/RHEL"
        fi
        exit 1
    fi

    if [ ${#warnings[@]} -gt 0 ]; then
        echo
        print_warning "发现以下警告："
        for warning in "${warnings[@]}"; do
            echo "  • $warning"
        done
    fi

    echo
    print_success "环境检查完成"
}

#===============================================================================
# 配置 API Key
#===============================================================================

configure_api_key() {
    print_header "🔑 配置 API Key"

    # 检查环境变量
    if [ -n "$ALIYUN_API_KEY" ]; then
        print_success "检测到环境变量 ALIYUN_API_KEY"
        return
    fi

    # 检查 .env 文件
    if [ -f ".env" ]; then
        source .env 2>/dev/null || true
        if [ -n "$ALIYUN_API_KEY" ]; then
            print_success "从 .env 文件加载 API Key"
            export ALIYUN_API_KEY
            return
        fi
    fi

    # 交互式输入
    print_info "需要配置阿里云百炼 API Key 才能使用 AI 功能"
    print_info "获取方式：https://bailian.console.aliyun.com/"
    echo

    read -p "$(echo -e ${CYAN}"请输入 API Key (输入 'skip' 跳过): "${NC})" api_key

    if [ "$api_key" = "skip" ] || [ -z "$api_key" ]; then
        print_warning "跳过 API Key 配置，将使用内置游戏模板（功能受限）"
        export ALIYUN_API_KEY="sk-dummy-key"
    else
        export ALIYUN_API_KEY="$api_key"

        # 保存到 .env
        echo "ALIYUN_API_KEY=$api_key" > .env
        echo "AGENT_RAG_TYPE=memory" >> .env
        print_success "API Key 已保存到 .env 文件"
    fi
}

#===============================================================================
# 安装依赖
#===============================================================================

install_dependencies() {
    print_header "📦 安装依赖"

    # 后端依赖
    print_step "检查后端依赖..."
    if [ -d "$BACKEND_DIR/target" ]; then
        print_success "后端已编译"
    else
        print_step "编译后端项目..."
        (
            cd "$BACKEND_DIR"
            mvn clean compile -q &
            spinner $!
            wait $!
        )
        if [ $? -eq 0 ]; then
            print_success "后端编译成功"
        else
            print_error "后端编译失败，查看日志: $BACKEND_LOG"
            exit 1
        fi
    fi

    # 前端依赖
    print_step "检查前端依赖..."
    if [ -d "$FRONTEND_DIR/node_modules" ]; then
        print_success "前端依赖已安装"
    else
        print_step "安装前端依赖..."
        (
            cd "$FRONTEND_DIR"
            # 清理可能的错误状态
            rm -rf node_modules package-lock.json
            npm install --silent 2>&1 | tee -a "$FRONTEND_LOG" &
            spinner $!
            wait $!
        )
        if [ $? -eq 0 ]; then
            print_success "前端依赖安装成功"
        else
            print_error "前端依赖安装失败，查看日志: $FRONTEND_LOG"
            exit 1
        fi
    fi
}

#===============================================================================
# 启动服务
#===============================================================================

start_services() {
    print_header "🚀 启动服务"

    # 启动后端
    print_step "启动后端服务 (端口: $BACKEND_PORT)..."
    (
        cd "$BACKEND_DIR"
        export SERVER_PORT=$BACKEND_PORT
        export ALIYUN_API_KEY=$ALIYUN_API_KEY
        export AGENT_RAG_TYPE=memory
        nohup mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=$BACKEND_PORT" > "$BACKEND_LOG" 2>&1 &
        echo $! > "$LOG_DIR/backend.pid"
    )

    # 等待后端启动
    print_step "等待后端就绪..."
    local backend_ready=false
    for i in {1..40}; do
        if grep -q "Started Application" "$BACKEND_LOG" 2>/dev/null; then
            sleep 2  # 额外等待确保完全启动
            backend_ready=true
            break
        fi
        printf "."
        sleep 1
    done
    echo  # 换行

    if [ "$backend_ready" = true ]; then
        print_success "后端服务已启动"
    else
        print_error "后端启动失败，查看日志: $BACKEND_LOG"
        tail -20 "$BACKEND_LOG"
        exit 1
    fi

    # 启动前端
    print_step "启动前端服务 (端口: $FRONTEND_PORT)..."
    (
        cd "$FRONTEND_DIR"
        # 配置后端代理
        export VITE_BACKEND_URL="http://localhost:$BACKEND_PORT"
        nohup npm run dev -- --port $FRONTEND_PORT --host > "$FRONTEND_LOG" 2>&1 &
        echo $! > "$LOG_DIR/frontend.pid"
    )

    # 等待前端启动
    sleep 3
    if [ -f "$LOG_DIR/frontend.pid" ] && ps -p $(cat "$LOG_DIR/frontend.pid") > /dev/null; then
        print_success "前端服务已启动"
    else
        print_error "前端启动失败，查看日志: $FRONTEND_LOG"
        tail -20 "$FRONTEND_LOG"
        exit 1
    fi
}

#===============================================================================
# 显示访问信息
#===============================================================================

show_access_info() {
    print_header "✨ 启动成功"

    echo -e "${GREEN}${BOLD}服务已就绪，访问地址：${NC}\n"
    echo -e "  ${CYAN}前端界面:${NC} http://localhost:${FRONTEND_PORT}"
    echo -e "  ${CYAN}后端 API:${NC} http://localhost:${BACKEND_PORT}/api/game/agents\n"

    echo -e "${YELLOW}${BOLD}快速测试:${NC}"
    echo -e "  1. 打开浏览器访问 ${CYAN}http://localhost:${FRONTEND_PORT}${NC}"
    echo -e "  2. 在聊天框输入: ${GREEN}\"给6岁孩子做一个10以内加法游戏\"${NC}"
    echo -e "  3. 等待游戏生成，右侧预览区会显示可玩的游戏\n"

    echo -e "${YELLOW}${BOLD}日志文件:${NC}"
    echo -e "  后端日志: ${CYAN}$BACKEND_LOG${NC}"
    echo -e "  前端日志: ${CYAN}$FRONTEND_LOG${NC}\n"

    echo -e "${YELLOW}${BOLD}管理命令:${NC}"
    echo -e "  查看后端日志: ${CYAN}tail -f $BACKEND_LOG${NC}"
    echo -e "  查看前端日志: ${CYAN}tail -f $FRONTEND_LOG${NC}"
    echo -e "  停止所有服务: ${CYAN}按 Ctrl+C${NC}\n"

    print_info "服务运行中，按 Ctrl+C 停止所有服务..."

    # 保持脚本运行
    while true; do
        sleep 1
    done
}

#===============================================================================
# 主流程
#===============================================================================

main() {
    clear
    echo -e "${CYAN}${BOLD}"
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║                                                            ║"
    echo "║     🎮  AI-GAME 儿童游戏生成框架 - 超轻量快速启动  🚀       ║"
    echo "║                                                            ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"

    # 执行步骤
    check_environment
    configure_api_key
    install_dependencies
    start_services
    show_access_info
}

# 运行主程序
main