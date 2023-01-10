/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.app.MainConfiguration;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions.Type;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;

/**
 * Class for managing elasticsearch indexes
 *
 * @author utkarsh
 */

@OwnedBy(PL)
@Slf4j
public class ElasticsearchIndexManager {
  @Inject private ElasticsearchClient elasticsearchClient;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private MainConfiguration mainConfiguration;
  @Inject private Set<SearchEntity<?>> searchEntities;

  boolean createIndex(String indexName, String entityConfiguration) {
    try {
      CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
      createIndexRequest.source(entityConfiguration, XContentType.JSON);
      CreateIndexResponse createIndexResponse = elasticsearchClient.createIndex(createIndexRequest);
      if (createIndexResponse != null && createIndexResponse.isAcknowledged()) {
        return true;
      }
      log.error("Could not create index {}", indexName);
    } catch (IOException e) {
      log.error("Failed to create index", e);
    }
    return false;
  }

  boolean deleteIndex(String indexName) {
    try {
      GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
      boolean exists = elasticsearchClient.indexExists(getIndexRequest);
      if (exists) {
        log.info("{} index exists. Deleting the index", indexName);
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        AcknowledgedResponse deleteIndexResponse = elasticsearchClient.deleteIndex(request);
        if (deleteIndexResponse == null || !deleteIndexResponse.isAcknowledged()) {
          log.error("Could not delete index {}", indexName);
          return false;
        }
      }
      return true;
    } catch (IOException e) {
      log.error("Failed to delete index {}", indexName, e);
      return false;
    }
  }

  boolean removeIndexFromAlias(String indexName) {
    try {
      GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
      boolean exists = elasticsearchClient.indexExists(getIndexRequest);
      if (exists) {
        log.info("{} index exists. Deleting the index", indexName);
        IndicesAliasesRequest request = new IndicesAliasesRequest();
        AliasActions aliasActions = new AliasActions(Type.REMOVE_INDEX).index(indexName);
        request.addAliasAction(aliasActions);

        AcknowledgedResponse indicesAliasesResponse = elasticsearchClient.updateAliases(request);
        if (indicesAliasesResponse == null || !indicesAliasesResponse.isAcknowledged()) {
          log.error("Could not delete index {}", indexName);
          return false;
        } else {
          log.info("{} index removed from alias", indexName);
        }
      }
      return true;
    } catch (IOException e) {
      log.error("Failed to delete index {}", indexName, e);
      return false;
    }
  }

  boolean attachIndexToAlias(String aliasName, String indexName) {
    IndicesAliasesRequest request = new IndicesAliasesRequest();
    AliasActions aliasAction = new AliasActions(Type.ADD).index(indexName).alias(aliasName);
    request.addAliasAction(aliasAction);
    try {
      log.info("Attaching index {} to alias {}", indexName, aliasName);
      AcknowledgedResponse indicesAliasesResponse = elasticsearchClient.updateAliases(request);
      return indicesAliasesResponse.isAcknowledged();
    } catch (IOException e) {
      log.error("Could not connect to elasticsearch", e);
    }
    return false;
  }

  String getIndexName(String type) {
    Map<String, Class<? extends SearchEntity>> searchEntitiesMap = new HashMap<>();
    searchEntities.forEach(searchEntity -> searchEntitiesMap.put(searchEntity.getType(), searchEntity.getClass()));
    Class<? extends SearchEntity> searchEntityClass = searchEntitiesMap.get(type);
    if (searchEntityClass != null) {
      SearchEntityIndexState searchEntityIndexState =
          wingsPersistence.get(SearchEntityIndexState.class, searchEntityClass.getCanonicalName());
      if (searchEntityIndexState != null) {
        return searchEntityIndexState.getIndexName();
      }
    }
    return null;
  }

  public String getAliasName(String type) {
    String indexSuffix = mainConfiguration.getElasticsearchConfig().getIndexSuffix();
    return type.concat(indexSuffix);
  }
}
