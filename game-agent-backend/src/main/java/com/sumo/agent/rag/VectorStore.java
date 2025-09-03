/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.rag;

import java.util.List;

/**
 * 向量存储接口
 * 用于存储和检索游戏相关知识
 */
public interface VectorStore {
    
    /**
     * 存储文档
     */
    void save(Document document);
    
    /**
     * 批量存储文档
     */
    void saveAll(List<Document> documents);
    
    /**
     * 检索相似文档
     * 
     * @param query 查询文本
     * @param topK 返回top-k个结果
     * @return 相似文档列表
     */
    List<Document> search(String query, int topK);
    
    /**
     * 根据ID获取文档
     */
    Document findById(String id);
    
    /**
     * 删除文档
     */
    void delete(String id);
    
    /**
     * 文档类
     */
    class Document {
        private String id;
        private String content;
        private DocumentType type;
        private float[] embedding;
        private java.util.Map<String, Object> metadata;
        
        public Document() {}
        
        public Document(String id, String content, DocumentType type) {
            this.id = id;
            this.content = content;
            this.type = type;
            this.metadata = new java.util.HashMap<>();
        }
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public DocumentType getType() { return type; }
        public void setType(DocumentType type) { this.type = type; }
        public float[] getEmbedding() { return embedding; }
        public void setEmbedding(float[] embedding) { this.embedding = embedding; }
        public java.util.Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(java.util.Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * 文档类型枚举
     */
    enum DocumentType {
        GAME_TEMPLATE("游戏模板"),
        EDUCATION_THEORY("教育理论"),
        GAME_ASSET("游戏素材"),
        USER_PROGRESS("用户进度"),
        SUCCESS_CASE("成功案例"),
        DESIGN_PATTERN("设计模式");
        
        private final String description;
        
        DocumentType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}