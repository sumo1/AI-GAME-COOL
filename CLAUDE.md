# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Children's Game Generation Agent Framework (儿童游戏生成Agent框架) that uses AI to generate educational games for children through natural language conversations. The project follows a plugin-based architecture with Spring Boot backend and React frontend.

## Build and Run Commands

### Backend (Spring Boot)
```bash
# Compile backend
cd game-agent-backend
mvn clean compile

# Run tests
mvn test

# Run backend server
mvn spring-boot:run

# Build JAR
mvn clean package -DskipTests

# Run specific test
mvn test -Dtest=TestClassName
mvn test -Dtest=TestClassName#methodName
```

### Frontend (React + Vite)
```bash
# Install dependencies
cd game-agent-frontend
npm install

# Run development server (port 5173)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Type checking
tsc
```

### Full Project Startup
```bash
# Interactive startup (recommended) - prompts for storage type
./start.sh

# Quick start with memory storage (no Docker required)
./quick-start.sh

# With specific RAG configuration
AGENT_RAG_TYPE=elasticsearch ./start.sh  # Use Elasticsearch (requires Docker)
AGENT_RAG_TYPE=memory ./start.sh         # Use memory storage (no Docker)
AGENT_RAG_ENABLED=false ./start.sh       # Disable RAG completely

# Configuration setup
./configure.sh  # Interactive environment setup
```

### Elasticsearch Management
```bash
# Using management script (interactive menu)
./es-manage.sh

# Using docker-compose directly
docker-compose up -d elasticsearch       # Start
docker-compose stop elasticsearch        # Stop
docker-compose logs -f elasticsearch     # View logs
docker-compose down                      # Stop and remove

# Check Elasticsearch status
curl -s http://localhost:9200/_cluster/health
```

## Architecture and Key Components

### Agent Framework Architecture

The system uses a **plugin-based Agent architecture** with three core layers:

#### 1. Agent Lifecycle (Template Method Pattern)
- `BaseAgent` abstract class defines the lifecycle: `run()` → `preHandle()` → `execute()` → `postHandle()` → `handleError()`
- All agents extend `BaseAgent` and implement `execute()`, `getName()`, `getDescription()`
- Agents auto-register via Spring's `@Component` annotation - no manual registration needed

#### 2. Agent Context Pipeline
- `AgentContext` carries all execution data through the pipeline
- Key fields: `sessionId`, `userInput`, `gameConfig`, `result`, `attributes` (extensible map)
- Context flows: Controller → GameGeneratorAgent → Specific Game Agent → Response

#### 3. Agent Selection Strategy
- `GameGeneratorAgent` is the main orchestrator that:
  1. Uses `IntentAnalyzer` to extract `GameIntent` from user input
  2. Selects appropriate game agent based on `GameType` enum
  3. Delegates execution to selected agent (e.g., `MathGameAgent`)
  4. Returns `GameGenerationResult` with generated HTML5 game

### Core Data Models

#### Records (Java 17 Records with accessor methods, not getters)
```java
// In GameGeneratorAgent.java:160
GameIntent(
    GameType gameType,      // Access via: intent.gameType()
    String ageGroup,        // Access via: intent.ageGroup()
    DifficultyLevel difficulty,
    String theme,
    String title,
    boolean timerEnabled,
    int duration
)
```

#### Enums (in GameConfig.java)
- `GameType`: MATH, WORD, MEMORY, PUZZLE, DRAWING
- `DifficultyLevel`: EASY, MEDIUM, HARD
- `Theme`: ANIMALS, SPACE, FAIRY_TALE, OCEAN, DINOSAUR, SUPERHERO

### RAG Storage Architecture

**Strategy Pattern Implementation**: `VectorStore` interface
```java
interface VectorStore {
    void save(Document document);
    List<Document> search(String query, int topK);
}
```

**Implementations**:
1. `ElasticsearchVectorStore` - Production: Real vector embeddings, persistent, requires Docker
2. `InMemoryVectorStore` - Development: Keyword matching, data lost on restart
3. `EmbeddedVectorStore` - Local files: File-based storage, no external dependencies

**Document Types**: GAME_TEMPLATE, EDUCATION_THEORY, GAME_ASSET, USER_PROGRESS, SUCCESS_CASE, DESIGN_PATTERN

### Spring AI Integration

- **Version**: Spring AI 1.0.0 with Spring Boot 3.2.2
- **Providers**: 
  - OpenAI (primary): Configure via `OPENAI_API_KEY`, `OPENAI_BASE_URL`
  - Alibaba DashScope (alternative): spring-ai-alibaba-starter-dashscope
- **Chat Model**: Injected via `@Autowired ChatModel` in services

### API Endpoints

```
POST /api/game/generate          # Generate game (JSON body)
GET  /api/game/agents            # List all available agents
GET  /api/game/generate/stream   # SSE streaming generation
```

## Important Technical Notes

### Java Version and Dependencies
- **Java 17 required** (NOT Java 21 due to Spring Boot 3.2.2)
- **Jakarta EE** (not javax): Use `jakarta.annotation.PostConstruct`
- **Maven repositories**: Aliyun mirror, Central, Spring Milestones

### Common Compilation Issues and Solutions
1. **Import errors**: Use `jakarta.annotation.*` not `javax.annotation.*`
2. **Lambda variables**: Must be final or effectively final
3. **Record accessors**: Use `record.field()` not `record.getField()`
4. **Float array streams**: Cannot use `Arrays.stream()` directly, need wrapper methods

### Agent Development Guidelines

To add a new game type:

```java
@Component("newGameAgent")  // Bean name for Spring DI
public class NewGameAgent extends BaseAgent {
    
    @Override
    public void execute(AgentContext context) {
        // 1. Extract game config from context
        GameConfig config = context.getGameConfig();
        
        // 2. Generate game HTML using templates or AI
        String gameHtml = generateGame(config);
        
        // 3. Set result in context
        context.setResult(gameHtml);
    }
    
    @Override
    public String getName() {
        return "新游戏类型Agent";
    }
    
    @Override
    public String getDescription() {
        return "生成XXX类型的教育游戏";
    }
}
```

### Environment Configuration

Required environment variables (set in `.env` file):
```bash
# AI Model Configuration
OPENAI_API_KEY=your-api-key
OPENAI_BASE_URL=https://api.openai.com  # Optional custom endpoint

# RAG Configuration
AGENT_RAG_ENABLED=true                   # Enable/disable RAG
AGENT_RAG_TYPE=memory                    # Options: elasticsearch, memory, embedded

# Elasticsearch (if using)
ES_HOST=localhost
ES_PORT=9200
```

### Character Encoding
- **All files must use UTF-8 encoding**
- Chinese characters in logs and comments are expected
- Set JVM flag if needed: `-Dfile.encoding=UTF-8`

## Project Migration Notes

This project is being migrated to align with newer Spring AI versions. When updating dependencies, reference:
- Target compatibility: ~/workplace/yuntoo/yuntoo-smartcode/pom.xml
- Current Spring AI: 1.0.0 → Consider upgrading when stable