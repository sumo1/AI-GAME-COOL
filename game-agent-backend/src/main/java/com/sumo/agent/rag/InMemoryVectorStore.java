/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存向量存储实现
 * 无需外部依赖，适合开发和小规模应用
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "agent.rag.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {
    
    // 使用Map存储文档
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    
    // 简单的倒排索引（用于关键词搜索）
    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();
    
    @Override
    public void save(Document document) {
        // 1. 存储文档
        documents.put(document.getId(), document);
        
        // 2. 更新倒排索引（简单分词）
        String[] words = document.getContent().toLowerCase().split("\\s+");
        for (String word : words) {
            invertedIndex.computeIfAbsent(word, k -> new HashSet<>())
                        .add(document.getId());
        }
        
        log.debug("📝 保存文档: {}", document.getId());
    }
    
    @Override
    public void saveAll(List<Document> docs) {
        docs.forEach(this::save);
        log.info("📚 批量保存 {} 个文档", docs.size());
    }
    
    @Override
    public List<Document> search(String query, int topK) {
        log.debug("🔍 搜索: {}", query);
        
        // 简单的关键词匹配评分
        Map<String, Double> scores = new HashMap<>();
        
        String[] queryWords = query.toLowerCase().split("\\s+");
        
        for (Document doc : documents.values()) {
            double score = calculateScore(doc, queryWords);
            if (score > 0) {
                scores.put(doc.getId(), score);
            }
        }
        
        // 按分数排序，返回top-k
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> documents.get(entry.getKey()))
            .collect(Collectors.toList());
    }
    
    @Override
    public Document findById(String id) {
        return documents.get(id);
    }
    
    @Override
    public void delete(String id) {
        Document doc = documents.remove(id);
        if (doc != null) {
            // 从倒排索引中删除
            String[] words = doc.getContent().toLowerCase().split("\\s+");
            for (String word : words) {
                Set<String> docIds = invertedIndex.get(word);
                if (docIds != null) {
                    docIds.remove(id);
                    if (docIds.isEmpty()) {
                        invertedIndex.remove(word);
                    }
                }
            }
        }
    }
    
    /**
     * 计算文档相关性分数（简化版TF-IDF）
     */
    private double calculateScore(Document doc, String[] queryWords) {
        String content = doc.getContent().toLowerCase();
        double score = 0;
        
        for (String word : queryWords) {
            // 计算词频
            int count = countOccurrences(content, word);
            if (count > 0) {
                // 简单的TF分数
                double tf = 1 + Math.log(count);
                
                // 简单的IDF分数（文档越少包含该词，分数越高）
                Set<String> docsWithWord = invertedIndex.get(word);
                double idf = docsWithWord != null ? 
                    Math.log(documents.size() / (double) docsWithWord.size()) : 0;
                
                score += tf * idf;
            }
        }
        
        // 考虑文档类型权重
        if (doc.getType() == DocumentType.SUCCESS_CASE) {
            score *= 1.5;  // 成功案例权重更高
        } else if (doc.getType() == DocumentType.EDUCATION_THEORY) {
            score *= 1.3;  // 教育理论权重次之
        }
        
        return score;
    }
    
    /**
     * 统计词频
     */
    private int countOccurrences(String text, String word) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(word, index)) != -1) {
            count++;
            index += word.length();
        }
        return count;
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", documents.size());
        stats.put("indexedWords", invertedIndex.size());
        
        // 按类型统计
        Map<DocumentType, Long> typeCount = documents.values().stream()
            .collect(Collectors.groupingBy(
                Document::getType, 
                Collectors.counting()
            ));
        stats.put("documentsByType", typeCount);
        
        return stats;
    }
}