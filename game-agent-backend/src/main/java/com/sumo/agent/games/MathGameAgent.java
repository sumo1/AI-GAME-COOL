/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.games;

import com.sumo.agent.core.AgentContext;
import com.sumo.agent.core.AgentPriority;
import com.sumo.agent.core.BaseAgent;
import com.sumo.agent.core.GameConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;

/**
 * 数学游戏生成Agent
 */
@Slf4j
@Component("mathAgent")
public class MathGameAgent extends BaseAgent {
    
    @Autowired
    private TemplateEngine templateEngine;
    
    @Override
    public void execute(AgentContext context) {
        GameConfig config = context.getGameConfig();
        log.info("🔢 生成数学游戏: {}", config.getTitle());
        
        // 生成游戏数据
        MathGameData gameData = generateGameData(config);
        
        // 渲染HTML模板
        String gameHtml = renderGameTemplate(gameData, config);
        
        // 设置结果
        Map<String, Object> result = new HashMap<>();
        result.put("html", gameHtml);
        result.put("gameData", gameData);
        result.put("type", "math");
        result.put("generatedByLLM", false);
        
        context.setResult(result);
        context.setSuccess(true);
    }
    
    @Override
    public String getName() {
        return "数学游戏Agent";
    }
    
    @Override
    public String getDescription() {
        return "生成数学类教育游戏，包括加减乘除、数字排序、找规律等";
    }
    
    @Override
    public AgentPriority getPriority() {
        return AgentPriority.HIGH;
    }
    
    /**
     * 生成游戏数据
     */
    private MathGameData generateGameData(GameConfig config) {
        MathGameData data = new MathGameData();
        data.setTitle(config.getTitle() != null ? config.getTitle() : "数学小英雄");
        data.setTheme(config.getTheme() != null ? config.getTheme() : "adventure");
        
        // 根据年龄组和难度生成题目
        List<MathQuestion> questions = generateQuestions(config);
        data.setQuestions(questions);
        
        // 设置游戏参数
        data.setTimeLimit(config.isTimerEnabled() ? 60 : 0);
        data.setSoundEnabled(config.isSoundEnabled());
        data.setScoreEnabled(config.isScoreEnabled());
        
        return data;
    }
    
    /**
     * 生成数学题目
     */
    private List<MathQuestion> generateQuestions(GameConfig config) {
        List<MathQuestion> questions = new ArrayList<>();
        Random random = new Random();
        
        // 根据年龄组确定数字范围
        int maxNumber = getMaxNumber(config.getAgeGroup());
        int questionCount = 10;
        
        for (int i = 0; i < questionCount; i++) {
            MathQuestion question = new MathQuestion();
            
            // 生成运算符
            String operator = selectOperator(config.getDifficulty());
            question.setOperator(operator);
            
            // 生成操作数
            int num1 = random.nextInt(maxNumber) + 1;
            int num2 = random.nextInt(maxNumber) + 1;
            
            // 确保减法不出现负数
            if (operator.equals("-") && num1 < num2) {
                int temp = num1;
                num1 = num2;
                num2 = temp;
            }
            
            question.setNum1(num1);
            question.setNum2(num2);
            
            // 计算答案
            int answer = calculateAnswer(num1, num2, operator);
            question.setAnswer(answer);
            
            // 生成选项
            List<Integer> options = generateOptions(answer, maxNumber);
            question.setOptions(options);
            
            questions.add(question);
        }
        
        return questions;
    }
    
    /**
     * 根据年龄组获取最大数字
     */
    private int getMaxNumber(String ageGroup) {
        if (ageGroup == null) {
            return 10;
        }
        
        if (ageGroup.contains("3-5")) {
            return 5;
        } else if (ageGroup.contains("6-8")) {
            return 20;
        } else if (ageGroup.contains("9-12")) {
            return 100;
        }
        
        return 10;
    }
    
    /**
     * 根据难度选择运算符
     */
    private String selectOperator(GameConfig.DifficultyLevel difficulty) {
        if (difficulty == null) {
            return "+";
        }
        
        Random random = new Random();
        return switch (difficulty) {
            case EASY -> "+";
            case MEDIUM -> random.nextBoolean() ? "+" : "-";
            case HARD -> {
                String[] operators = {"+", "-", "*"};
                yield operators[random.nextInt(operators.length)];
            }
            case ADAPTIVE -> "+";
        };
    }
    
    /**
     * 计算答案
     */
    private int calculateAnswer(int num1, int num2, String operator) {
        return switch (operator) {
            case "+" -> num1 + num2;
            case "-" -> num1 - num2;
            case "*" -> num1 * num2;
            case "/" -> num1 / num2;
            default -> 0;
        };
    }
    
    /**
     * 生成选项
     */
    private List<Integer> generateOptions(int correctAnswer, int maxNumber) {
        Set<Integer> options = new HashSet<>();
        options.add(correctAnswer);
        
        Random random = new Random();
        while (options.size() < 4) {
            int offset = random.nextInt(10) - 5;
            int option = correctAnswer + offset;
            if (option >= 0 && option <= maxNumber * 2) {
                options.add(option);
            }
        }
        
        List<Integer> list = new ArrayList<>(options);
        Collections.shuffle(list);
        return list;
    }
    
    /**
     * 渲染游戏模板
     */
    private String renderGameTemplate(MathGameData gameData, GameConfig config) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>").append(gameData.getTitle()).append("</title>\n");
        html.append("    <style>\n");
        html.append("        body { font-family: 'Comic Sans MS', cursive; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); margin: 0; padding: 20px; min-height: 100vh; display: flex; justify-content: center; align-items: center; }\n");
        html.append("        .game-container { background: white; border-radius: 20px; padding: 30px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); max-width: 600px; width: 100%; }\n");
        html.append("        h1 { text-align: center; color: #333; margin-bottom: 30px; font-size: 2em; }\n");
        html.append("        .score-board { display: flex; justify-content: space-around; margin-bottom: 30px; }\n");
        html.append("        .score-item { background: #f0f0f0; padding: 10px 20px; border-radius: 10px; font-size: 18px; }\n");
        html.append("        .question-container { background: #f8f9fa; padding: 30px; border-radius: 15px; margin-bottom: 20px; text-align: center; }\n");
        html.append("        .question { font-size: 36px; color: #2c3e50; margin-bottom: 30px; font-weight: bold; }\n");
        html.append("        .options { display: grid; grid-template-columns: repeat(2, 1fr); gap: 15px; margin-top: 20px; }\n");
        html.append("        .option-btn { padding: 20px; font-size: 24px; border: none; border-radius: 10px; background: linear-gradient(45deg, #3498db, #2ecc71); color: white; cursor: pointer; transition: transform 0.3s, box-shadow 0.3s; }\n");
        html.append("        .option-btn:hover { transform: translateY(-3px); box-shadow: 0 10px 20px rgba(0,0,0,0.2); }\n");
        html.append("        .option-btn.correct { background: linear-gradient(45deg, #27ae60, #2ecc71); animation: pulse 0.5s; }\n");
        html.append("        .option-btn.wrong { background: linear-gradient(45deg, #e74c3c, #c0392b); animation: shake 0.5s; }\n");
        html.append("        @keyframes pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.1); } }\n");
        html.append("        @keyframes shake { 0%, 100% { transform: translateX(0); } 25% { transform: translateX(-10px); } 75% { transform: translateX(10px); } }\n");
        html.append("        .progress { height: 20px; background: #ecf0f1; border-radius: 10px; overflow: hidden; margin-bottom: 20px; }\n");
        html.append("        .progress-bar { height: 100%; background: linear-gradient(90deg, #3498db, #2ecc71); transition: width 0.3s; }\n");
        html.append("        .result { display: none; text-align: center; font-size: 24px; padding: 20px; }\n");
        html.append("        .result.show { display: block; }\n");
        html.append("        button { background: #3498db; color: white; border: none; padding: 10px 20px; border-radius: 5px; cursor: pointer; font-size: 16px; }\n");
        html.append("        button:hover { background: #2980b9; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"game-container\">\n");
        html.append("        <h1>🎯 ").append(gameData.getTitle()).append("</h1>\n");
        html.append("        <div class=\"score-board\">\n");
        html.append("            <div class=\"score-item\">✅ 正确: <span id=\"correct\">0</span></div>\n");
        html.append("            <div class=\"score-item\">❌ 错误: <span id=\"wrong\">0</span></div>\n");
        html.append("            <div class=\"score-item\">📊 进度: <span id=\"current\">1</span>/").append(gameData.getQuestions().size()).append("</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"progress\">\n");
        html.append("            <div class=\"progress-bar\" id=\"progressBar\" style=\"width: 0%\"></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"question-container\">\n");
        html.append("            <div class=\"question\" id=\"question\"></div>\n");
        html.append("            <div class=\"options\" id=\"options\"></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"result\" id=\"result\">\n");
        html.append("            <h2>🎉 游戏完成！</h2>\n");
        html.append("            <p>正确率: <span id=\"accuracy\"></span>%</p>\n");
        html.append("            <button onclick=\"resetGame()\">再玩一次</button>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append("        const questions = ").append(convertQuestionsToJson(gameData.getQuestions())).append(";\n");
        html.append("        let currentIndex = 0;\n");
        html.append("        let correctCount = 0;\n");
        html.append("        let wrongCount = 0;\n");
        html.append("\n");
        html.append("        function displayQuestion() {\n");
        html.append("            if (currentIndex >= questions.length) {\n");
        html.append("                showResult();\n");
        html.append("                return;\n");
        html.append("            }\n");
        html.append("            \n");
        html.append("            const q = questions[currentIndex];\n");
        html.append("            document.getElementById('question').innerHTML = `${q.num1} ${q.operator} ${q.num2} = ?`;\n");
        html.append("            document.getElementById('current').textContent = currentIndex + 1;\n");
        html.append("            \n");
        html.append("            const optionsDiv = document.getElementById('options');\n");
        html.append("            optionsDiv.innerHTML = '';\n");
        html.append("            \n");
        html.append("            q.options.forEach(option => {\n");
        html.append("                const btn = document.createElement('button');\n");
        html.append("                btn.className = 'option-btn';\n");
        html.append("                btn.textContent = option;\n");
        html.append("                btn.onclick = () => checkAnswer(option, q.answer);\n");
        html.append("                optionsDiv.appendChild(btn);\n");
        html.append("            });\n");
        html.append("            \n");
        html.append("            updateProgress();\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function checkAnswer(selected, correct) {\n");
        html.append("            const buttons = document.querySelectorAll('.option-btn');\n");
        html.append("            buttons.forEach(btn => {\n");
        html.append("                btn.disabled = true;\n");
        html.append("                if (parseInt(btn.textContent) === correct) {\n");
        html.append("                    btn.classList.add('correct');\n");
        html.append("                } else if (parseInt(btn.textContent) === selected) {\n");
        html.append("                    btn.classList.add('wrong');\n");
        html.append("                }\n");
        html.append("            });\n");
        html.append("            \n");
        html.append("            if (selected === correct) {\n");
        html.append("                correctCount++;\n");
        html.append("                document.getElementById('correct').textContent = correctCount;\n");
        html.append("            } else {\n");
        html.append("                wrongCount++;\n");
        html.append("                document.getElementById('wrong').textContent = wrongCount;\n");
        html.append("            }\n");
        html.append("            \n");
        html.append("            setTimeout(() => {\n");
        html.append("                currentIndex++;\n");
        html.append("                displayQuestion();\n");
        html.append("            }, 1500);\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function updateProgress() {\n");
        html.append("            const progress = (currentIndex / questions.length) * 100;\n");
        html.append("            document.getElementById('progressBar').style.width = progress + '%';\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function showResult() {\n");
        html.append("            document.querySelector('.question-container').style.display = 'none';\n");
        html.append("            document.getElementById('result').classList.add('show');\n");
        html.append("            const accuracy = Math.round((correctCount / questions.length) * 100);\n");
        html.append("            document.getElementById('accuracy').textContent = accuracy;\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function resetGame() {\n");
        html.append("            currentIndex = 0;\n");
        html.append("            correctCount = 0;\n");
        html.append("            wrongCount = 0;\n");
        html.append("            document.getElementById('correct').textContent = '0';\n");
        html.append("            document.getElementById('wrong').textContent = '0';\n");
        html.append("            document.querySelector('.question-container').style.display = 'block';\n");
        html.append("            document.getElementById('result').classList.remove('show');\n");
        html.append("            displayQuestion();\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        // 初始化游戏\n");
        html.append("        displayQuestion();\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString();
    }
    
    /**
     * 将问题列表转换为JSON字符串
     */
    private String convertQuestionsToJson(List<MathQuestion> questions) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < questions.size(); i++) {
            MathQuestion q = questions.get(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"num1\":").append(q.getNum1()).append(",");
            json.append("\"num2\":").append(q.getNum2()).append(",");
            json.append("\"operator\":\"").append(q.getOperator()).append("\",");
            json.append("\"answer\":").append(q.getAnswer()).append(",");
            json.append("\"options\":[");
            for (int j = 0; j < q.getOptions().size(); j++) {
                if (j > 0) json.append(",");
                json.append(q.getOptions().get(j));
            }
            json.append("]}");
        }
        json.append("]");
        return json.toString();
    }
    
    /**
     * 数学游戏数据
     */
    public static class MathGameData {
        private String title;
        private String theme;
        private List<MathQuestion> questions;
        private int timeLimit;
        private boolean soundEnabled;
        private boolean scoreEnabled;
        
        // getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }
        public List<MathQuestion> getQuestions() { return questions; }
        public void setQuestions(List<MathQuestion> questions) { this.questions = questions; }
        public int getTimeLimit() { return timeLimit; }
        public void setTimeLimit(int timeLimit) { this.timeLimit = timeLimit; }
        public boolean isSoundEnabled() { return soundEnabled; }
        public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }
        public boolean isScoreEnabled() { return scoreEnabled; }
        public void setScoreEnabled(boolean scoreEnabled) { this.scoreEnabled = scoreEnabled; }
    }
    
    /**
     * 数学题目
     */
    public static class MathQuestion {
        private int num1;
        private int num2;
        private String operator;
        private int answer;
        private List<Integer> options;
        
        // getters and setters
        public int getNum1() { return num1; }
        public void setNum1(int num1) { this.num1 = num1; }
        public int getNum2() { return num2; }
        public void setNum2(int num2) { this.num2 = num2; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public int getAnswer() { return answer; }
        public void setAnswer(int answer) { this.answer = answer; }
        public List<Integer> getOptions() { return options; }
        public void setOptions(List<Integer> options) { this.options = options; }
    }
}
