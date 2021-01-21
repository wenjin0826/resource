package com.cloud.common.elasticsearch;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.elasticsearch.common.collect.MapBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentEntity;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ElasticsearchSupport {
    public static final String ANALYZER_WHITESPACE = "whitespace";
    public static final String INDEX_NAME_SEPERATOR = "-";

    @Autowired(required = false)
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    public <T> T save(T entity, Class<T> indexClass, String... indexDates) {
        IndexCoordinates index = getIndexCoordinates(indexClass, indexDates);
        return elasticsearchRestTemplate.save(entity, index);
    }

    public <T> Iterable<T> save(Iterable<T> entities, Class<T> indexClass, String... indexDates) {
        IndexCoordinates index = getIndexCoordinates(indexClass, indexDates);
        return elasticsearchRestTemplate.save(entities, index);
    }

    public String delete(String id, Class indexClass, String... indexDates) {
        IndexCoordinates index = getIndexCoordinates(indexClass, indexDates);
        return elasticsearchRestTemplate.delete(id, index);
    }

    public <T> SearchHits<T> search(Query query, Class<T> indexClass, String... indexDates) {
        IndexCoordinates index = getIndexCoordinates(indexClass, indexDates);
        return elasticsearchRestTemplate.search(query, indexClass, index);
    }

    public <T> List<SearchHits<T>> multiSearch(List<Query> queryList, Class<T> indexClass, String... indexDates) {
        IndexCoordinates index = getIndexCoordinates(indexClass, indexDates);
        return elasticsearchRestTemplate.multiSearch(queryList, indexClass, index);
    }

    public <T> T get(String id, Class<T> indexClass, String... indexDates) {
        IndexCoordinates index = getIndexCoordinates(indexClass, indexDates);
        return elasticsearchRestTemplate.get(id, indexClass, index);
    }

    public <T> List<T> multiGet(Query query, Class<T> indexClass, String... indexDates) {
        IndexCoordinates index = getIndexCoordinates(indexClass, indexDates);
        return elasticsearchRestTemplate.multiGet(query, indexClass, index);
    }

    public void createIndex(Class indexClass, String... indexDates) {
        IndexCoordinates index = getIndexCoordinates(indexClass, indexDates);
        IndexOperations indexOps = elasticsearchRestTemplate.indexOps(index);
        if (!indexOps.exists()) {
            indexOps.create(getDocumentSettings(indexClass));
            log.info("create index success, indexName={}", index.getIndexName());
        }
        indexOps.putMapping(indexOps.createMapping(indexClass));
    }

    public void deleteIndex(String indexName) {
        IndexOperations indexOps = elasticsearchRestTemplate.indexOps(IndexCoordinates.of(indexName));
        if (indexOps.exists()) {
            indexOps.delete();
            log.info("delete index success, indexName={}", indexName);
        }
    }

    public String indexYear(Date date) {
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate.format(DateTimeFormatter.ofPattern("yyyy"));
    }

    public String indexMonth(Date date) {
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private Document getDocumentSettings(Class indexClass) {
        ElasticsearchPersistentEntity persistentEntity = getPersistentEntity(indexClass);
        if (persistentEntity.isUseServerConfiguration()) {
            return Document.create();
        }
        Map<String, String> map = new MapBuilder<String, String>()
                .put("index.number_of_shards", String.valueOf(persistentEntity.getShards()))
                .put("index.number_of_replicas", String.valueOf(persistentEntity.getReplicas()))
                .put("index.refresh_interval", persistentEntity.getRefreshInterval())
                .put("index.store.type", persistentEntity.getIndexStoreType()).map();
        return Document.from(map);
    }

    private IndexCoordinates getIndexCoordinates(Class indexClass, String... indexDates) {
        ElasticsearchPersistentEntity persistentEntity = getPersistentEntity(indexClass);
        String indexName = persistentEntity.getIndexCoordinates().getIndexName();
        if (ArrayUtils.isEmpty(indexDates)) {
            return IndexCoordinates.of(indexName);
        }
        String[] indexNames = new String[indexDates.length];
        for (int i = 0; i < indexDates.length; i++) {
            indexNames[i] = indexName + INDEX_NAME_SEPERATOR + indexDates[i];
        }
        return IndexCoordinates.of(indexNames);
    }

    private ElasticsearchPersistentEntity getPersistentEntity(Class indexClass) {
        return elasticsearchRestTemplate.getElasticsearchConverter()
                .getMappingContext().getRequiredPersistentEntity(indexClass);
    }
}
