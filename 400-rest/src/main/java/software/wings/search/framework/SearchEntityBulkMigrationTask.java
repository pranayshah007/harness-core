/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.PersistentEntity;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;

@OwnedBy(PL)
@AllArgsConstructor
@Slf4j
public class SearchEntityBulkMigrationTask<T extends PersistentEntity> implements Callable<Boolean> {
  private ElasticsearchClient elasticsearchClient;
  private SearchEntity<T> searchEntity;
  private T object;
  private String newIndexName;

  private boolean insertDocument(String entityId, String entityJson) {
    IndexRequest indexRequest = new IndexRequest(newIndexName);
    indexRequest.id(entityId);
    indexRequest.source(entityJson, XContentType.JSON);
    try {
      IndexResponse indexResponse = elasticsearchClient.index(indexRequest);
      return indexResponse.status() == RestStatus.OK || indexResponse.status() == RestStatus.CREATED;
    } catch (IOException e) {
      log.error("Could not connect to elasticsearch", e);
    }
    return false;
  }

  private boolean upsertEntityBaseView(EntityBaseView entityBaseView) {
    if (entityBaseView != null) {
      Optional<String> jsonString = SearchEntityUtils.convertToJson(entityBaseView);
      if (!jsonString.isPresent()) {
        return false;
      }
      return insertDocument(entityBaseView.getId(), jsonString.get());
    }
    return true;
  }

  @Override
  public Boolean call() {
    EntityBaseView entityBaseView = searchEntity.getView(object);
    if (entityBaseView != null) {
      return upsertEntityBaseView(entityBaseView);
    } else {
      log.warn("object {} created nullBaseView", object.toString());
    }
    return true;
  }
}
