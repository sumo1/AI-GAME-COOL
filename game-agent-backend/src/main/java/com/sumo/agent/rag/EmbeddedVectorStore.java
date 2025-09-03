/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * 嵌入式向量数据库
 * 使用本地文件存储，无需外部服务
 * 
 * 可选方案：
 * 1. H2 Database（支持向量扩展）
 * 2. SQLite + sqlite-vss
 * 3. DuckDB
 * 4. Chroma (Python需要通过API调用)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "agent.rag.type", havingValue = "embedded")
public class EmbeddedVectorStore implements VectorStore {
    
    // 这里可以使用 H2 数据库 + pgvector 扩展
    // 或者使用 Apache Lucene（Java原生）
    
    @Override
    public void save(Document document) {
        // 使用 H2 或 SQLite 存储
        log.info("保存到嵌入式数据库: {}", document.getId());
    }
    
    @Override
    public void saveAll(List<Document> documents) {
        documents.forEach(this::save);
    }
    
    @Override
    public List<Document> search(String query, int topK) {
        // 实现向量搜索
        log.info("从嵌入式数据库搜索: {}", query);
        return new java.util.ArrayList<>();
    }
    
    @Override
    public Document findById(String id) {
        return null;
    }
    
    @Override
    public void delete(String id) {
        log.info("从嵌入式数据库删除: {}", id);
    }
}