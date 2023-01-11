/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.search.framework.EntityBaseView.EntityBaseViewKeys;
import software.wings.search.framework.EntityInfo.EntityInfoKeys;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * Elasticsearch Dao
 *
 * @author ujjawal
 */
@OwnedBy(PL)
@Slf4j
public class ElasticsearchDao implements SearchDao {
  @Inject private ElasticsearchClient elasticsearchClient;
  @Inject private ElasticsearchIndexManager elasticsearchIndexManager;
  private static final String SCRIPT_LANGUAGE = "painless";
  private static final String COULD_NOT_CONNECT_ERROR_MESSAGE = "Could not connect to elasticsearch";
  private static final String FIELD_TO_UPDATE_PARAMS_KEY = "fieldToUpdate";
  private static final String NEW_ELEMENT_PARAMS_KEY = "newList";
  private static final String ID_TO_BE_DELETED_PARAMS_KEY = "idToBeDeleted";

  @Override
  public boolean upsertDocument(String entityType, String entityId, String entityJson) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateRequest updateRequest = new UpdateRequest(indexName, entityId);
    updateRequest.doc(entityJson, XContentType.JSON);
    updateRequest.retryOnConflict(3);
    updateRequest.docAsUpsert(true);
    updateRequest.setRefreshPolicy(RefreshPolicy.WAIT_UNTIL);
    try {
      UpdateResponse updateResponse = elasticsearchClient.update(updateRequest);
      return updateResponse.status() == RestStatus.OK || updateResponse.status() == RestStatus.CREATED;
    } catch (ElasticsearchException e) {
      log.error("Error while updating document {} in index {}", entityJson, indexName, e);
    } catch (IOException e) {
      log.error(COULD_NOT_CONNECT_ERROR_MESSAGE, e);
    }
    return false;
  }

  @Override
  public boolean deleteDocument(String entityType, String documentId) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    DeleteRequest deleteRequest = new DeleteRequest(indexName, documentId);
    deleteRequest.setRefreshPolicy(RefreshPolicy.WAIT_UNTIL);
    try {
      DeleteResponse deleteResponse = elasticsearchClient.delete(deleteRequest);
      return deleteResponse.status() == RestStatus.OK || deleteResponse.status() == RestStatus.NOT_FOUND;
    } catch (ElasticsearchException e) {
      log.error("Error while trying to delete document {} in index {}", documentId, indexName, e);
    } catch (IOException e) {
      log.error(COULD_NOT_CONNECT_ERROR_MESSAGE, e);
    }
    return false;
  }

  @Override
  public boolean appendToListInMultipleDocuments(
      String entityType, String listToUpdate, List<String> documentIds, Map<String, Object> newElement) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);

    Map<String, Object> params = new HashMap<>();
    params.put(FIELD_TO_UPDATE_PARAMS_KEY, listToUpdate);
    params.put(NEW_ELEMENT_PARAMS_KEY, newElement);

    String key = listToUpdate + "." + EntityInfoKeys.id;
    request.setQuery(
        QueryBuilders.boolQuery()
            .must(QueryBuilders.termsQuery(EntityBaseViewKeys.id, documentIds))
            .mustNot(QueryBuilders.nestedQuery(listToUpdate,
                QueryBuilders.boolQuery().filter(QueryBuilders.termQuery(key, newElement.get(EntityInfoKeys.id))),
                ScoreMode.Max)));
    request.setQuery(QueryBuilders.termsQuery(EntityBaseViewKeys.id, documentIds));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if(ctx._source[params.fieldToUpdate]!=null){ctx._source[params.fieldToUpdate].add(params.newList);} "
            + "else{ctx._source[params.fieldToUpdate] = [params.newList];}",
        params));
    log.info(request.toString());
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public boolean appendToListInMultipleDocuments(String entityType, String listToUpdate, List<String> documentIds,
      Map<String, Object> newElement, int maxElementsInList) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);

    Map<String, Object> params = new HashMap<>();
    params.put(FIELD_TO_UPDATE_PARAMS_KEY, listToUpdate);
    params.put(NEW_ELEMENT_PARAMS_KEY, newElement);
    params.put("maxElementsInList", maxElementsInList);

    String key = listToUpdate + "." + EntityInfoKeys.id;
    request.setQuery(
        QueryBuilders.boolQuery()
            .must(QueryBuilders.termsQuery(EntityBaseViewKeys.id, documentIds))
            .mustNot(QueryBuilders.nestedQuery(listToUpdate,
                QueryBuilders.boolQuery().filter(QueryBuilders.termQuery(key, newElement.get(EntityInfoKeys.id))),
                ScoreMode.Max)));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if (ctx._source[params.fieldToUpdate] != null) {if (ctx._source[params.fieldToUpdate].length == params.maxElementsInList) { ctx._source[params.fieldToUpdate] = ctx._source[params.fieldToUpdate].stream().skip(1).collect(Collectors.toList());} ctx._source[params.fieldToUpdate].add(params.newList);} else {ctx._source[params.fieldToUpdate] = [params.newList];}",
        params));
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public boolean appendToListInSingleDocument(
      String entityType, String listToUpdate, String documentId, Map<String, Object> newElement) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);
    String key = listToUpdate + "." + EntityInfoKeys.id;
    Map<String, Object> params = new HashMap<>();
    params.put(FIELD_TO_UPDATE_PARAMS_KEY, listToUpdate);
    params.put(NEW_ELEMENT_PARAMS_KEY, newElement);
    request.setQuery(
        QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery(EntityBaseViewKeys.id, documentId))
            .mustNot(QueryBuilders.nestedQuery(listToUpdate,
                QueryBuilders.boolQuery().filter(QueryBuilders.termQuery(key, newElement.get(EntityInfoKeys.id))),
                ScoreMode.Max)));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if(ctx._source[params.fieldToUpdate]!=null){ctx._source[params.fieldToUpdate].add(params.newList);} "
            + "else{ctx._source[params.fieldToUpdate] = [params.newList];}",
        params));
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public boolean appendToListInSingleDocument(String entityType, String listToUpdate, String documentId,
      Map<String, Object> newElement, int maxElementsInList) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);

    String key = listToUpdate + "." + EntityInfoKeys.id;
    Map<String, Object> params = new HashMap<>();
    params.put(FIELD_TO_UPDATE_PARAMS_KEY, listToUpdate);
    params.put(NEW_ELEMENT_PARAMS_KEY, newElement);
    params.put("maxElementsInList", maxElementsInList);
    request.setQuery(
        QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery(EntityBaseViewKeys.id, documentId))
            .mustNot(QueryBuilders.nestedQuery(listToUpdate,
                QueryBuilders.boolQuery().filter(QueryBuilders.termQuery(key, newElement.get(EntityBaseViewKeys.id))),
                ScoreMode.Max)));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if (ctx._source[params.fieldToUpdate] != null) {if (ctx._source[params.fieldToUpdate].length == params.maxElementsInList) { ctx._source[params.fieldToUpdate] = ctx._source[params.fieldToUpdate].stream().skip(1).collect(Collectors.toList());} ctx._source[params.fieldToUpdate].add(params.newList);} else {ctx._source[params.fieldToUpdate] = [params.newList];}",
        params));
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public boolean removeFromListInMultipleDocuments(
      String entityType, String listToUpdate, List<String> documentIds, String idToBeDeleted) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);

    Map<String, Object> params = new HashMap<>();
    params.put(FIELD_TO_UPDATE_PARAMS_KEY, listToUpdate);
    params.put(ID_TO_BE_DELETED_PARAMS_KEY, idToBeDeleted);

    request.setQuery(QueryBuilders.termsQuery(EntityBaseViewKeys.id, documentIds));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if(ctx._source[params.fieldToUpdate]!=null){ctx._source[params.fieldToUpdate] = ctx._source[params.fieldToUpdate].stream().filter(item -> item.id != params.idToBeDeleted).collect(Collectors.toList());}",
        params));
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public boolean removeFromListInMultipleDocuments(
      String entityType, String listToUpdate, String documentId, String idToBeDeleted) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);

    Map<String, Object> params = new HashMap<>();
    params.put(FIELD_TO_UPDATE_PARAMS_KEY, listToUpdate);
    params.put(ID_TO_BE_DELETED_PARAMS_KEY, idToBeDeleted);

    request.setQuery(QueryBuilders.termsQuery(EntityBaseViewKeys.id, documentId));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if(ctx._source[params.fieldToUpdate]!=null){ctx._source[params.fieldToUpdate] = ctx._source[params.fieldToUpdate].stream().filter(item -> item.id != params.idToBeDeleted).collect(Collectors.toList());}",
        params));
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public boolean removeFromListInMultipleDocuments(String entityType, String listToUpdate, String idToBeDeleted) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);

    Map<String, Object> params = new HashMap<>();
    params.put("listKey", listToUpdate);
    params.put(ID_TO_BE_DELETED_PARAMS_KEY, idToBeDeleted);
    String key = listToUpdate + "." + EntityInfoKeys.id;

    request.setQuery(QueryBuilders.nestedQuery(
        listToUpdate, QueryBuilders.boolQuery().filter(QueryBuilders.termQuery(key, idToBeDeleted)), ScoreMode.Max));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if(ctx._source[params.listKey]!=null){ctx._source[params.listKey] = ctx._source[params.listKey].stream().filter(item -> item.id != params.idToBeDeleted).collect(Collectors.toList());}",
        params));
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public boolean updateListInMultipleDocuments(
      String type, String listToUpdate, String newElement, String elementId, String elementKeyToChange) {
    String indexName = elasticsearchIndexManager.getIndexName(type);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);

    Map<String, Object> params = new HashMap<>();
    params.put("entityType", listToUpdate);
    params.put("newValue", newElement);
    params.put("filterId", elementId);
    params.put(FIELD_TO_UPDATE_PARAMS_KEY, elementKeyToChange);
    String key = listToUpdate + "." + EntityInfoKeys.id;

    request.setQuery(QueryBuilders.nestedQuery(
        listToUpdate, QueryBuilders.boolQuery().filter(QueryBuilders.termQuery(key, elementId)), ScoreMode.Max));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if (ctx._source[params.entityType] != null){for(item in ctx._source[params.entityType]){ if(item.id==params.filterId){item[params.fieldToUpdate] = params.newValue;}}}",
        params));
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public boolean updateKeyInMultipleDocuments(
      String entityType, String listToUpdate, String newValue, String filterKey, String filterValue) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);

    Map<String, Object> params = new HashMap<>();
    params.put("keyToUpdate", listToUpdate);
    params.put("newValue", newValue);
    params.put("filterKey", filterKey);
    params.put("filterValue", filterValue);

    request.setQuery(QueryBuilders.termQuery(filterKey, filterValue));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if (ctx._source[params.filterKey] == params.filterValue) {ctx._source[params.keyToUpdate] = params.newValue;}",
        params));
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public boolean addTimestamp(
      String entityType, String fieldName, String documentId, long createdAt, int daysToRetain) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);
    Map<String, Object> params = new HashMap<>();
    long cutoffTimestamp = SearchEntityUtils.getTimestampNdaysBackInMillis(daysToRetain);

    params.put(FIELD_TO_UPDATE_PARAMS_KEY, fieldName);
    params.put("cutoffTimestamp", cutoffTimestamp);
    params.put("currentTimestamp", createdAt);

    request.setQuery(QueryBuilders.boolQuery()
                         .must(QueryBuilders.termQuery(EntityBaseViewKeys.id, documentId))
                         .mustNot(QueryBuilders.termQuery(fieldName, createdAt)));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if (ctx._source[params.fieldToUpdate] != null) { ctx._source[params.fieldToUpdate] = ctx._source[params.fieldToUpdate].stream().filter(item -> item >= params.cutoffTimestamp).collect(Collectors.toList()); ctx._source[params.fieldToUpdate].add(params.currentTimestamp); } else { ctx._source[params.fieldToUpdate] = [params.currentTimestamp]; }",
        params));
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public boolean addTimestamp(
      String entityType, String fieldName, List<String> documentIds, long createdAt, int daysToRetain) {
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);
    Map<String, Object> params = new HashMap<>();
    long cutoffTimestamp = SearchEntityUtils.getTimestampNdaysBackInMillis(daysToRetain);
    params.put(FIELD_TO_UPDATE_PARAMS_KEY, fieldName);
    params.put("cutoffTimestamp", cutoffTimestamp);
    params.put("currentTimestamp", createdAt);

    request.setQuery(QueryBuilders.boolQuery()
                         .must(QueryBuilders.termsQuery(EntityBaseViewKeys.id, documentIds))
                         .mustNot(QueryBuilders.termQuery(fieldName, createdAt)));
    request.setScript(new Script(ScriptType.INLINE, SCRIPT_LANGUAGE,
        "if (ctx._source[params.fieldToUpdate] != null) { ctx._source[params.fieldToUpdate] = ctx._source[params.fieldToUpdate].stream().filter(item -> item >= params.cutoffTimestamp).collect(Collectors.toList()); ctx._source[params.fieldToUpdate].add(params.currentTimestamp); } else { ctx._source[params.fieldToUpdate] = [params.currentTimestamp]; }",
        params));
    return processUpdateByQuery(request, params, indexName);
  }

  @Override
  public List<String> nestedQuery(String entityType, String fieldName, String value) {
    final int MAX_RESULTS = 500;
    String indexName = elasticsearchIndexManager.getIndexName(entityType);
    SearchRequest searchRequest = new SearchRequest(indexName);
    String path = fieldName + "." + EntityBaseViewKeys.id;
    NestedQueryBuilder nestedQueryBuilder =
        QueryBuilders.nestedQuery(fieldName, QueryBuilders.termQuery(path, value), ScoreMode.Max);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(nestedQueryBuilder).size(MAX_RESULTS);
    searchRequest.source(searchSourceBuilder);
    try {
      SearchResponse searchResponse = elasticsearchClient.search(searchRequest);
      List<String> requiredIds = new ArrayList<>();
      for (SearchHit searchHit : searchResponse.getHits()) {
        requiredIds.add(searchHit.getId());
      }
      return requiredIds;
    } catch (IOException e) {
      log.error(COULD_NOT_CONNECT_ERROR_MESSAGE, e);
    }
    return new ArrayList<>();
  }

  private boolean processUpdateByQuery(
      UpdateByQueryRequest updateByQueryRequest, Map<String, Object> params, String indexName) {
    try {
      updateByQueryRequest.setRefresh(true);
      BulkByScrollResponse bulkResponse = elasticsearchClient.updateByQuery(updateByQueryRequest);
      if (bulkResponse.getSearchFailures().isEmpty() && bulkResponse.getBulkFailures().isEmpty()) {
        if (bulkResponse.getUpdated() == 0) {
          log.warn(String.format("No documents were updated with params %s in index %s", params.toString(), indexName));
        }
        return true;
      }
      log.error("Failed to update index {} by query with params {}", indexName, params.toString());
      log.error("Update by Query response is {}", bulkResponse);
    } catch (IOException e) {
      log.error(COULD_NOT_CONNECT_ERROR_MESSAGE, e);
    }
    return false;
  }
}
