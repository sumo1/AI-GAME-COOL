/*
 * @since: 2025/8/20
 * @author: sumo
 */
package com.sumo.agent.games;

import com.sumo.agent.core.AgentContext;
import com.sumo.agent.core.BaseAgent;
import com.sumo.agent.core.GameConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * 记忆游戏生成Agent
 * 负责生成各种记忆类游戏，如卡片配对、序列记忆等
 */
@Slf4j
@Component("memoryGameAgent")
public class MemoryGameAgent extends BaseAgent {
    
    @Override
    public void execute(AgentContext context) {
        log.info("🎮 开始生成记忆游戏");
        
        GameConfig config = context.getGameConfig();
        if (config == null) {
            config = GameConfig.builder()
                    .gameType(GameConfig.GameType.MEMORY)
                    .difficulty(GameConfig.DifficultyLevel.EASY)
                    .ageGroup("6-8")
                    .theme("animals")
                    .timerEnabled(false)
                    .soundEnabled(false)
                    .duration(10)
                    .scoreEnabled(true)
                    .build();
            context.setGameConfig(config);
        }
        
        // 生成游戏HTML
        String gameHtml = generateMemoryGame(config);
        
        // 构建返回结果，保持与MathGameAgent一致的格式
        Map<String, Object> result = new HashMap<>();
        result.put("html", gameHtml);
        result.put("type", "memory");
        result.put("gameData", Map.of(
            "title", config.getTitle() != null ? config.getTitle() : "记忆翻牌游戏",
            "theme", config.getTheme(),
            "difficulty", config.getDifficulty().name()
        ));
        result.put("generatedByLLM", false);
        
        context.setResult(result);
    }
    
    @Override
    public String getName() {
        return "记忆游戏Agent";
    }
    
    @Override
    public String getDescription() {
        return "生成记忆类教育游戏，包括卡片配对、序列记忆、位置记忆等";
    }
    
    /**
     * 生成记忆游戏HTML
     */
    private String generateMemoryGame(GameConfig config) {
        String theme = config.getTheme() != null ? config.getTheme() : "animals";
        String difficulty = config.getDifficulty() != null ? config.getDifficulty().name() : "EASY";
        
        // 根据难度设置卡片数量
        int pairs = difficulty.equals("EASY") ? 6 : difficulty.equals("MEDIUM") ? 8 : 10;
        
        // 根据主题选择图标
        String[] icons = getThemeIcons(theme);
        
        // 生成游戏HTML
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>记忆翻牌游戏</title>\n");
        html.append("    <style>\n");
        html.append("        body { font-family: Arial, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }\n");
        html.append("        .game-container { background: white; border-radius: 20px; padding: 30px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); }\n");
        html.append("        h1 { text-align: center; color: #333; margin-bottom: 20px; }\n");
        html.append("        .stats { display: flex; justify-content: space-around; margin-bottom: 20px; font-size: 18px; }\n");
        html.append("        .stat { background: #f0f0f0; padding: 10px 20px; border-radius: 10px; }\n");
        html.append("        .game-board { display: grid; grid-template-columns: repeat(4, 100px); gap: 10px; justify-content: center; }\n");
        html.append("        .card { width: 100px; height: 100px; background: linear-gradient(45deg, #3498db, #2ecc71); border-radius: 10px; cursor: pointer; display: flex; justify-content: center; align-items: center; font-size: 40px; transition: transform 0.6s; transform-style: preserve-3d; position: relative; }\n");
        html.append("        .card.flipped { transform: rotateY(180deg); }\n");
        html.append("        .card.matched { background: linear-gradient(45deg, #f39c12, #e74c3c); pointer-events: none; animation: bounce 0.5s; }\n");
        html.append("        .card-front, .card-back { position: absolute; width: 100%; height: 100%; backface-visibility: hidden; display: flex; justify-content: center; align-items: center; border-radius: 10px; }\n");
        html.append("        .card-front { background: linear-gradient(45deg, #3498db, #2ecc71); color: white; }\n");
        html.append("        .card-back { background: white; transform: rotateY(180deg); }\n");
        html.append("        @keyframes bounce { 0%, 100% { transform: rotateY(180deg) scale(1); } 50% { transform: rotateY(180deg) scale(1.1); } }\n");
        html.append("        .win-message { display: none; text-align: center; margin-top: 20px; font-size: 24px; color: #27ae60; font-weight: bold; }\n");
        html.append("        button { background: #3498db; color: white; border: none; padding: 10px 20px; border-radius: 5px; cursor: pointer; font-size: 16px; margin-top: 20px; }\n");
        html.append("        button:hover { background: #2980b9; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"game-container\">\n");
        html.append("        <h1>🎮 记忆翻牌游戏</h1>\n");
        html.append("        <div class=\"stats\">\n");
        html.append("            <div class=\"stat\">步数: <span id=\"moves\">0</span></div>\n");
        html.append("            <div class=\"stat\">配对: <span id=\"pairs\">0</span>/").append(pairs).append("</div>\n");
        html.append("            <div class=\"stat\">时间: <span id=\"time\">0</span>秒</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"game-board\" id=\"gameBoard\"></div>\n");
        html.append("        <div class=\"win-message\" id=\"winMessage\">🎉 恭喜你赢了！</div>\n");
        html.append("        <center><button onclick=\"resetGame()\">重新开始</button></center>\n");
        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append("        const icons = ").append(getIconsArray(icons, pairs)).append(";\n");
        html.append("        let cards = [...icons, ...icons];\n");
        html.append("        let flippedCards = [];\n");
        html.append("        let matchedPairs = 0;\n");
        html.append("        let moves = 0;\n");
        html.append("        let startTime = Date.now();\n");
        html.append("        let timerInterval;\n");
        html.append("\n");
        html.append("        function shuffle(array) {\n");
        html.append("            for (let i = array.length - 1; i > 0; i--) {\n");
        html.append("                const j = Math.floor(Math.random() * (i + 1));\n");
        html.append("                [array[i], array[j]] = [array[j], array[i]];\n");
        html.append("            }\n");
        html.append("            return array;\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function createBoard() {\n");
        html.append("            const board = document.getElementById('gameBoard');\n");
        html.append("            board.innerHTML = '';\n");
        html.append("            shuffle(cards).forEach((icon, index) => {\n");
        html.append("                const card = document.createElement('div');\n");
        html.append("                card.className = 'card';\n");
        html.append("                card.dataset.icon = icon;\n");
        html.append("                card.dataset.index = index;\n");
        html.append("                card.innerHTML = `\n");
        html.append("                    <div class=\"card-front\">?</div>\n");
        html.append("                    <div class=\"card-back\">${icon}</div>\n");
        html.append("                `;\n");
        html.append("                card.addEventListener('click', flipCard);\n");
        html.append("                board.appendChild(card);\n");
        html.append("            });\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function flipCard() {\n");
        html.append("            if (flippedCards.length >= 2) return;\n");
        html.append("            if (this.classList.contains('flipped')) return;\n");
        html.append("            \n");
        html.append("            this.classList.add('flipped');\n");
        html.append("            flippedCards.push(this);\n");
        html.append("            \n");
        html.append("            if (flippedCards.length === 2) {\n");
        html.append("                moves++;\n");
        html.append("                document.getElementById('moves').textContent = moves;\n");
        html.append("                checkMatch();\n");
        html.append("            }\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function checkMatch() {\n");
        html.append("            const [card1, card2] = flippedCards;\n");
        html.append("            const match = card1.dataset.icon === card2.dataset.icon;\n");
        html.append("            \n");
        html.append("            setTimeout(() => {\n");
        html.append("                if (match) {\n");
        html.append("                    card1.classList.add('matched');\n");
        html.append("                    card2.classList.add('matched');\n");
        html.append("                    matchedPairs++;\n");
        html.append("                    document.getElementById('pairs').textContent = matchedPairs;\n");
        html.append("                    \n");
        html.append("                    if (matchedPairs === ").append(pairs).append(") {\n");
        html.append("                        endGame();\n");
        html.append("                    }\n");
        html.append("                } else {\n");
        html.append("                    card1.classList.remove('flipped');\n");
        html.append("                    card2.classList.remove('flipped');\n");
        html.append("                }\n");
        html.append("                flippedCards = [];\n");
        html.append("            }, 1000);\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function endGame() {\n");
        html.append("            clearInterval(timerInterval);\n");
        html.append("            document.getElementById('winMessage').style.display = 'block';\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function updateTimer() {\n");
        html.append("            const elapsed = Math.floor((Date.now() - startTime) / 1000);\n");
        html.append("            document.getElementById('time').textContent = elapsed;\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function resetGame() {\n");
        html.append("            flippedCards = [];\n");
        html.append("            matchedPairs = 0;\n");
        html.append("            moves = 0;\n");
        html.append("            startTime = Date.now();\n");
        html.append("            document.getElementById('moves').textContent = '0';\n");
        html.append("            document.getElementById('pairs').textContent = '0';\n");
        html.append("            document.getElementById('time').textContent = '0';\n");
        html.append("            document.getElementById('winMessage').style.display = 'none';\n");
        html.append("            createBoard();\n");
        html.append("            clearInterval(timerInterval);\n");
        html.append("            timerInterval = setInterval(updateTimer, 1000);\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        // 初始化游戏\n");
        html.append("        createBoard();\n");
        html.append("        timerInterval = setInterval(updateTimer, 1000);\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString();
    }
    
    /**
     * 根据主题获取图标
     */
    private String[] getThemeIcons(String theme) {
        switch (theme.toLowerCase()) {
            case "animals":
                return new String[]{"🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯"};
            case "space":
                return new String[]{"🚀", "🛸", "🌟", "⭐", "🌙", "☄️", "🌍", "🪐", "👽", "🛰️"};
            case "ocean":
                return new String[]{"🐠", "🐟", "🐡", "🦈", "🐙", "🦀", "🦞", "🦐", "🐚", "🐳"};
            case "dinosaur":
                return new String[]{"🦕", "🦖", "🦴", "🥚", "🌋", "🌿", "🌳", "🪨", "🔥", "☄️"};
            case "superhero":
                return new String[]{"🦸", "🦹", "⚡", "🛡️", "⚔️", "🎯", "💪", "🔥", "❄️", "🌟"};
            default:
                return new String[]{"😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😊"};
        }
    }
    
    /**
     * 生成图标数组字符串
     */
    private String getIconsArray(String[] icons, int pairs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(pairs, icons.length); i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(icons[i]).append("'");
        }
        sb.append("]");
        return sb.toString();
    }
}
