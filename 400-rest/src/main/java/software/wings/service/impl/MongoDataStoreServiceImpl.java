/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static dev.morphia.mapping.Mapper.ID_KEY;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.persistence.GoogleDataStoreAware;

import software.wings.beans.Log;
import software.wings.beans.Log.LogKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.DataStoreService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class MongoDataStoreServiceImpl implements DataStoreService {
  private WingsPersistence wingsPersistence;

  @Inject
  public MongoDataStoreServiceImpl(WingsPersistence wingsPersistence) {
    this.wingsPersistence = wingsPersistence;
  }

  @Override
  public <T extends GoogleDataStoreAware> void save(Class<T> clazz, List<T> records, boolean ignoreDuplicate) {
    if (isEmpty(records)) {
      return;
    }
    if (ignoreDuplicate) {
      wingsPersistence.saveIgnoringDuplicateKeys(records);
    } else {
      wingsPersistence.save(records);
    }
  }

  @Override
  public <T extends GoogleDataStoreAware> int getNumberOfResults(Class<T> clazz, PageRequest<T> pageRequest) {
    return wingsPersistence.convertToQuery(clazz, pageRequest).asKeyList().size();
  }

  @Override
  public void delete(Class<? extends GoogleDataStoreAware> clazz, String id) {
    Query<? extends GoogleDataStoreAware> query =
        wingsPersistence.createQuery(clazz, excludeAuthority).filter("_id", id);
    wingsPersistence.delete(query);
  }

  @Override
  public void delete(Class<? extends GoogleDataStoreAware> clazz, String fieldName, String fieldValue) {
    Query<? extends GoogleDataStoreAware> query =
        wingsPersistence.createQuery(clazz, excludeAuthority).filter(fieldName, fieldValue);
    wingsPersistence.delete(query);
  }

  @Override
  public <T extends GoogleDataStoreAware> PageResponse<T> list(Class<T> clazz, PageRequest<T> pageRequest) {
    PageResponse<T> response = wingsPersistence.query(clazz, pageRequest, excludeAuthority);

    if (isNotEmpty(pageRequest.getLimit()) && pageRequest.getLimit().equals(UNLIMITED)) {
      int previousOffset = 0;
      List<T> responseList = new ArrayList<>();
      while (!response.isEmpty()) {
        responseList.addAll(response.getResponse());
        previousOffset += response.size();
        pageRequest.setOffset(String.valueOf(previousOffset));
        response = wingsPersistence.query(clazz, pageRequest, excludeAuthority);
      }
      response.setResponse(responseList);
      response.setOffset(String.valueOf(responseList.size()));
    }
    return response;
  }

  @Override
  public <T extends GoogleDataStoreAware> PageResponse<T> list(
      Class<T> clazz, PageRequest<T> pageRequest, boolean getTotalRecords) {
    return list(clazz, pageRequest);
  }

  @Override
  public <T extends GoogleDataStoreAware> T getEntity(Class<T> clazz, String id) {
    return wingsPersistence.get(clazz, id);
  }

  @Override
  public <T extends GoogleDataStoreAware> void incrementField(
      Class<T> clazz, String id, String fieldName, int incrementCount) {
    UpdateOperations<T> updateOps = wingsPersistence.createUpdateOperations(clazz).inc(fieldName, incrementCount);
    Query<T> query = wingsPersistence.createQuery(clazz).filter(ID_KEY, id);
    wingsPersistence.findAndModify(query, updateOps, new FindAndModifyOptions());
  }

  @Override
  public void purgeByActivity(String appId, String activityId) {
    wingsPersistence.delete(wingsPersistence.createQuery(Log.class, excludeAuthority)
                                .filter("appId", appId)
                                .filter(LogKeys.activityId, activityId));
  }

  @Override
  public void purgeOlderRecords() {
    // do nothing
  }

  @Override
  public void purgeDataRetentionOlderRecords(Map<String, Long> accounts) {
    // do nothing
  }

  @Override
  public boolean supportsInOperator() {
    return true;
  }
}
