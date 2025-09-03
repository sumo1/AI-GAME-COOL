/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 游戏知识RAG服务
 * 管理游戏设计知识库的检索增强生成
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true")
public class GameKnowledgeRAG {
    
    @Autowired(required = false)
    private VectorStore vectorStore;
    
    /**
     * 初始化知识库
     */
    @PostConstruct
    public void initKnowledgeBase() {
        if (vectorStore == null) {
            log.info("RAG未启用，跳过知识库初始化");
            return;
        }
        
        log.info("🎯 初始化游戏知识库...");
        
        // 1. 加载游戏设计模式
        loadGameDesignPatterns();
        
        // 2. 加载教育理论
        loadEducationTheories();
        
        // 3. 加载成功案例
        loadSuccessCases();
        
        // 4. 加载游戏素材
        loadGameAssets();
        
        log.info("✅ 知识库初始化完成");
    }
    
    /**
     * 检索增强的游戏生成
     */
    public GameGenerationContext enhanceWithRAG(String userInput, String ageGroup) {
        if (vectorStore == null) {
            return new GameGenerationContext(userInput);
        }
        
        log.info("🔍 使用RAG增强游戏生成...");
        
        // 1. 检索相关的游戏设计知识
        List<VectorStore.Document> designDocs = vectorStore.search(
            "游戏设计 " + userInput + " " + ageGroup,
            5
        );
        
        // 2. 检索教育理论
        List<VectorStore.Document> eduDocs = vectorStore.search(
            "儿童教育 认知发展 " + ageGroup,
            3
        );
        
        // 3. 检索成功案例
        List<VectorStore.Document> cases = vectorStore.search(
            "成功游戏案例 " + extractGameType(userInput),
            3
        );
        
        // 4. 构建增强的上下文
        GameGenerationContext context = new GameGenerationContext(userInput);
        context.setDesignPatterns(extractContent(designDocs));
        context.setEducationTheories(extractContent(eduDocs));
        context.setSuccessCases(extractContent(cases));
        
        // 5. 生成增强的prompt
        String enhancedPrompt = buildEnhancedPrompt(context);
        context.setEnhancedPrompt(enhancedPrompt);
        
        log.info("📚 检索到 {} 个相关文档", 
            designDocs.size() + eduDocs.size() + cases.size());
        
        return context;
    }
    
    /**
     * 加载游戏设计模式
     */
    private void loadGameDesignPatterns() {
        List<VectorStore.Document> patterns = Arrays.asList(
            new VectorStore.Document(
                "pattern-math-visual",
                "数学游戏设计模式：\n" +
                "- 使用视觉辅助：用图形、颜色帮助理解数字概念\n" +
                "- 递进式难度：从简单到复杂，每关增加少许难度\n" +
                "- 即时反馈：答对立即给予视觉和声音奖励\n" +
                "- 错误友好：答错不惩罚，引导正确答案",
                VectorStore.DocumentType.DESIGN_PATTERN
            ),
            
            new VectorStore.Document(
                "pattern-memory-game",
                "记忆游戏设计模式：\n" +
                "- 开始展示所有卡片3-5秒\n" +
                "- 使用主题相关的图片（动物、水果等）\n" +
                "- 配对成功有动画效果\n" +
                "- 记录时间和步数，鼓励挑战最佳成绩",
                VectorStore.DocumentType.DESIGN_PATTERN
            ),
            
            new VectorStore.Document(
                "pattern-word-game",
                "单词游戏设计模式：\n" +
                "- 图文结合：每个单词配图片\n" +
                "- 发音功能：点击可听标准发音\n" +
                "- 拼写辅助：显示字母轮廓\n" +
                "- 主题分组：按场景组织单词（家庭、学校、动物）",
                VectorStore.DocumentType.DESIGN_PATTERN
            )
        );
        
        vectorStore.saveAll(patterns);
        log.info("📐 加载了 {} 个游戏设计模式", patterns.size());
    }
    
    /**
     * 加载教育理论
     */
    private void loadEducationTheories() {
        List<VectorStore.Document> theories = Arrays.asList(
            new VectorStore.Document(
                "theory-piaget",
                "皮亚杰认知发展理论应用：\n" +
                "【2-7岁 前运算阶段】\n" +
                "- 需要具体形象的表现\n" +
                "- 游戏应包含大量图像和动画\n" +
                "- 避免抽象概念\n" +
                "【7-11岁 具体运算阶段】\n" +
                "- 可以理解逻辑关系\n" +
                "- 适合规则类游戏\n" +
                "- 可以处理多步骤任务",
                VectorStore.DocumentType.EDUCATION_THEORY
            ),
            
            new VectorStore.Document(
                "theory-gamification",
                "游戏化学习要素：\n" +
                "- 明确的目标：让孩子知道要完成什么\n" +
                "- 进度可视化：进度条、关卡、徽章\n" +
                "- 即时反馈：对错立即响应\n" +
                "- 适度挑战：不太易不太难\n" +
                "- 成就系统：积分、排行榜、奖励",
                VectorStore.DocumentType.EDUCATION_THEORY
            ),
            
            new VectorStore.Document(
                "theory-attention-span",
                "儿童注意力时长：\n" +
                "- 3-4岁：3-8分钟\n" +
                "- 5-6岁：10-15分钟\n" +
                "- 7-9岁：15-20分钟\n" +
                "- 10-12岁：20-30分钟\n" +
                "游戏设计应考虑关卡时长",
                VectorStore.DocumentType.EDUCATION_THEORY
            )
        );
        
        vectorStore.saveAll(theories);
        log.info("📖 加载了 {} 个教育理论", theories.size());
    }
    
    /**
     * 加载成功案例
     */
    private void loadSuccessCases() {
        List<VectorStore.Document> cases = Arrays.asList(
            new VectorStore.Document(
                "case-math-adventure",
                "成功案例：数学大冒险\n" +
                "- 目标用户：5-8岁\n" +
                "- 特色：将数学题融入冒险故事\n" +
                "- 成功要素：\n" +
                "  * 每答对一题，英雄前进一步\n" +
                "  * 错误时给出视觉提示\n" +
                "  * 收集宝石作为奖励\n" +
                "- 效果：平均游戏时长15分钟，完成率85%",
                VectorStore.DocumentType.SUCCESS_CASE
            ),
            
            new VectorStore.Document(
                "case-animal-memory",
                "成功案例：动物记忆卡\n" +
                "- 目标用户：3-6岁\n" +
                "- 特色：真实动物照片+叫声\n" +
                "- 成功要素：\n" +
                "  * 翻牌有翻转动画\n" +
                "  * 配对成功播放动物叫声\n" +
                "  * 三个难度级别（4/6/8对）\n" +
                "- 效果：重玩率高达70%",
                VectorStore.DocumentType.SUCCESS_CASE
            )
        );
        
        vectorStore.saveAll(cases);
        log.info("🏆 加载了 {} 个成功案例", cases.size());
    }
    
    /**
     * 加载游戏素材
     */
    private void loadGameAssets() {
        List<VectorStore.Document> assets = Arrays.asList(
            new VectorStore.Document(
                "assets-themes",
                "热门游戏主题：\n" +
                "- 动物世界：农场动物、野生动物、海洋生物\n" +
                "- 太空探索：星球、火箭、外星人\n" +
                "- 童话王国：公主、骑士、魔法\n" +
                "- 恐龙时代：霸王龙、三角龙、翼龙\n" +
                "- 超级英雄：拯救世界、打败坏人",
                VectorStore.DocumentType.GAME_ASSET
            ),
            
            new VectorStore.Document(
                "assets-rewards",
                "奖励机制素材：\n" +
                "- 视觉奖励：星星、烟花、彩虹\n" +
                "- 声音奖励：欢呼声、掌声、胜利音乐\n" +
                "- 收集要素：贴纸、徽章、宝石\n" +
                "- 进度奖励：解锁新关卡、新角色",
                VectorStore.DocumentType.GAME_ASSET
            )
        );
        
        vectorStore.saveAll(assets);
        log.info("🎨 加载了 {} 个游戏素材", assets.size());
    }
    
    /**
     * 提取游戏类型
     */
    private String extractGameType(String userInput) {
        if (userInput.contains("数学") || userInput.contains("计算") || userInput.contains("加法")) {
            return "数学游戏";
        } else if (userInput.contains("单词") || userInput.contains("英语") || userInput.contains("字母")) {
            return "语言游戏";
        } else if (userInput.contains("记忆") || userInput.contains("记住")) {
            return "记忆游戏";
        }
        return "教育游戏";
    }
    
    /**
     * 提取文档内容
     */
    private List<String> extractContent(List<VectorStore.Document> docs) {
        return docs.stream()
            .map(VectorStore.Document::getContent)
            .collect(Collectors.toList());
    }
    
    /**
     * 构建增强的prompt
     */
    private String buildEnhancedPrompt(GameGenerationContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户需求：").append(context.getUserInput()).append("\n\n");
        
        if (!context.getDesignPatterns().isEmpty()) {
            prompt.append("=== 相关游戏设计模式 ===\n");
            context.getDesignPatterns().forEach(p -> 
                prompt.append(p).append("\n\n"));
        }
        
        if (!context.getEducationTheories().isEmpty()) {
            prompt.append("=== 教育理论指导 ===\n");
            context.getEducationTheories().forEach(t -> 
                prompt.append(t).append("\n\n"));
        }
        
        if (!context.getSuccessCases().isEmpty()) {
            prompt.append("=== 成功案例参考 ===\n");
            context.getSuccessCases().forEach(c -> 
                prompt.append(c).append("\n\n"));
        }
        
        prompt.append("基于以上信息，生成一个适合的儿童游戏。");
        
        return prompt.toString();
    }
    
    /**
     * 游戏生成上下文
     */
    public static class GameGenerationContext {
        private String userInput;
        private List<String> designPatterns;
        private List<String> educationTheories;
        private List<String> successCases;
        private String enhancedPrompt;
        
        public GameGenerationContext(String userInput) {
            this.userInput = userInput;
            this.designPatterns = new java.util.ArrayList<>();
            this.educationTheories = new java.util.ArrayList<>();
            this.successCases = new java.util.ArrayList<>();
        }
        
        // Getters and setters
        public String getUserInput() { return userInput; }
        public void setUserInput(String userInput) { this.userInput = userInput; }
        public List<String> getDesignPatterns() { return designPatterns; }
        public void setDesignPatterns(List<String> patterns) { this.designPatterns = patterns; }
        public List<String> getEducationTheories() { return educationTheories; }
        public void setEducationTheories(List<String> theories) { this.educationTheories = theories; }
        public List<String> getSuccessCases() { return successCases; }
        public void setSuccessCases(List<String> cases) { this.successCases = cases; }
        public String getEnhancedPrompt() { return enhancedPrompt; }
        public void setEnhancedPrompt(String prompt) { this.enhancedPrompt = prompt; }
    }
}