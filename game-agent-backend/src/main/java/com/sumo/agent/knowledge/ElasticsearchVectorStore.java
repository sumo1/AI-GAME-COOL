/*
 * @since: 2025/8/11
 * @author: sumo
 */
package com.sumo.agent.knowledge;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch向量存储实现
 * 支持向量搜索和全文搜索
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "agent.rag.type", havingValue = "elasticsearch")
public class ElasticsearchVectorStore implements VectorStore {
    
    private static final String INDEX_NAME = "game_knowledge";
    
    @Value("${agent.rag.elasticsearch.host:localhost}")
    private String host;
    
    @Value("${agent.rag.elasticsearch.port:9200}")
    private int port;
    
    private ElasticsearchClient client;
    private RestClient restClient;
    
    @PostConstruct
    public void init() {
        try {
            // 创建REST客户端
            restClient = RestClient.builder(
                new HttpHost(host, port, "http")
            ).build();
            
            // 创建Elasticsearch客户端
            ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper()
            );
            client = new ElasticsearchClient(transport);
            
            // 创建索引
            createIndexIfNotExists();
            
            log.info("✅ Elasticsearch连接成功: {}:{}", host, port);
        } catch (Exception e) {
            log.error("❌ Elasticsearch连接失败", e);
            throw new RuntimeException("无法连接到Elasticsearch", e);
        }
    }
    
    @PreDestroy
    public void destroy() {
        try {
            if (restClient != null) {
                restClient.close();
            }
        } catch (IOException e) {
            log.error("关闭Elasticsearch连接失败", e);
        }
    }
    
    /**
     * 创建索引（如果不存在）
     */
    private void createIndexIfNotExists() throws IOException {
        ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(INDEX_NAME));
        boolean exists = client.indices().exists(existsRequest).value();
        
        if (!exists) {
            // 创建索引，定义mapping
            String mappingJson = """
                {
                  "properties": {
                    "id": { "type": "keyword" },
                    "content": { 
                      "type": "text",
                      "analyzer": "ik_max_word"
                    },
                    "type": { "type": "keyword" },
                    "embedding": {
                      "type": "dense_vector",
                      "dims": 768,
                      "index": true,
                      "similarity": "cosine"
                    },
                    "metadata": { "type": "object" },
                    "created_at": { "type": "date" }
                  }
                }
                """;
            
            CreateIndexRequest createRequest = CreateIndexRequest.of(i -> i
                .index(INDEX_NAME)
                .mappings(m -> m.source(s -> s.withJson(new java.io.StringReader(mappingJson))))
            );
            
            client.indices().create(createRequest);
            log.info("📚 创建索引: {}", INDEX_NAME);
        }
    }
    
    @Override
    public void save(Document document) {
        try {
            // 如果没有embedding，生成一个模拟的
            if (document.getEmbedding() == null) {
                document.setEmbedding(generateMockEmbedding(document.getContent()));
            }
            
            // 构建ES文档
            Map<String, Object> esDoc = new HashMap<>();
            esDoc.put("id", document.getId());
            esDoc.put("content", document.getContent());
            esDoc.put("type", document.getType().name());
            esDoc.put("embedding", document.getEmbedding());
            esDoc.put("metadata", document.getMetadata());
            esDoc.put("created_at", new Date());
            
            // 索引文档
            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                .index(INDEX_NAME)
                .id(document.getId())
                .document(esDoc)
            );
            
            IndexResponse response = client.index(request);
            log.debug("📝 索引文档: {} -> {}", document.getId(), response.result());
            
        } catch (IOException e) {
            log.error("保存文档失败", e);
            throw new RuntimeException("保存文档失败", e);
        }
    }
    
    @Override
    public void saveAll(List<Document> documents) {
        documents.forEach(this::save);
        log.info("📚 批量保存 {} 个文档", documents.size());
    }
    
    @Override
    public List<Document> search(String query, int topK) {
        try {
            // 生成查询向量（实际应用中应使用真实的embedding模型）
            float[] queryVector = generateMockEmbedding(query);
            
            // 构建混合查询（向量 + 文本）
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .size(topK)
                // KNN向量搜索
                .knn(k -> k
                    .field("embedding")
                    .queryVector(floatArrayToDoubleList(queryVector))
                    .k(topK)
                    .numCandidates(topK * 2)
                )
                // 文本搜索（可选，增强相关性）
                .query(q -> q
                    .match(m -> m
                        .field("content")
                        .query(query)
                    )
                )
            );
            
            SearchResponse<Map> response = client.search(searchRequest, Map.class);
            
            // 转换结果
            List<Document> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Document doc = convertToDocument(hit.source());
                results.add(doc);
            }
            
            log.debug("🔍 搜索 '{}' 返回 {} 个结果", query, results.size());
            return results;
            
        } catch (IOException e) {
            log.error("搜索失败", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public Document findById(String id) {
        try {
            GetRequest getRequest = GetRequest.of(g -> g
                .index(INDEX_NAME)
                .id(id)
            );
            
            GetResponse<Map> response = client.get(getRequest, Map.class);
            
            if (response.found()) {
                return convertToDocument(response.source());
            }
            return null;
            
        } catch (IOException e) {
            log.error("查询文档失败: {}", id, e);
            return null;
        }
    }
    
    @Override
    public void delete(String id) {
        try {
            DeleteRequest deleteRequest = DeleteRequest.of(d -> d
                .index(INDEX_NAME)
                .id(id)
            );
            
            DeleteResponse response = client.delete(deleteRequest);
            log.debug("🗑️ 删除文档: {} -> {}", id, response.result());
            
        } catch (IOException e) {
            log.error("删除文档失败: {}", id, e);
        }
    }
    
    /**
     * 生成模拟的向量（实际应用中应使用真实的embedding模型）
     */
    private float[] generateMockEmbedding(String text) {
        // 这里应该调用真实的embedding模型（如OpenAI Embeddings API）
        // 现在使用简单的哈希函数生成固定维度的向量
        Random random = new Random(text.hashCode());
        float[] embedding = new float[768];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = random.nextFloat() * 2 - 1; // -1到1之间
        }
        return embedding;
    }
    
    /**
     * 将ES文档转换为Document对象
     */
    private Document convertToDocument(Map<String, Object> source) {
        Document doc = new Document();
        doc.setId((String) source.get("id"));
        doc.setContent((String) source.get("content"));
        
        String typeStr = (String) source.get("type");
        if (typeStr != null) {
            doc.setType(DocumentType.valueOf(typeStr));
        }
        
        Object embeddingObj = source.get("embedding");
        if (embeddingObj instanceof List) {
            List<Double> embeddingList = (List<Double>) embeddingObj;
            float[] embedding = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                embedding[i] = embeddingList.get(i).floatValue();
            }
            doc.setEmbedding(embedding);
        }
        
        doc.setMetadata((Map<String, Object>) source.get("metadata"));
        
        return doc;
    }
    
    /**
     * float数组转换为Float列表
     */
    private List<Float> floatArrayToDoubleList(float[] floatArray) {
        List<Float> floatList = new ArrayList<>();
        for (float f : floatArray) {
            floatList.add(f);
        }
        return floatList;
    }
    
    /**
     * 获取索引统计信息
     */
    public Map<String, Object> getStats() {
        try {
            CountRequest countRequest = CountRequest.of(c -> c.index(INDEX_NAME));
            CountResponse countResponse = client.count(countRequest);
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalDocuments", countResponse.count());
            stats.put("indexName", INDEX_NAME);
            stats.put("host", host + ":" + port);
            
            return stats;
        } catch (IOException e) {
            log.error("获取统计信息失败", e);
            return new HashMap<>();
        }
    }
}