/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.store.Store;

import java.util.List;
import java.util.Map;

public interface DataStoreService {
  <T extends GoogleDataStoreAware> void save(Class<T> clazz, List<T> records, boolean ignoreDuplicate);

  <T extends GoogleDataStoreAware> void saveInStore(
      Class<T> clazz, List<T> records, boolean ignoreDuplicate, Store store);

  <T extends GoogleDataStoreAware> int getNumberOfResults(Class<T> clazz, PageRequest<T> pageRequest);

  <T extends GoogleDataStoreAware> PageResponse<T> list(Class<T> clazz, PageRequest<T> pageRequest);
  <T extends GoogleDataStoreAware> PageResponse<T> list(
      Class<T> clazz, PageRequest<T> pageRequest, boolean getTotalRecords);
  <T extends GoogleDataStoreAware> T getEntity(Class<T> clazz, String id);
  <T extends GoogleDataStoreAware> void incrementField(Class<T> clazz, String id, String fieldName, int incrementCount);

  void delete(Class<? extends GoogleDataStoreAware> clazz, String id);

  void purgeByActivity(String appId, String activityId);

  void purgeOlderRecords();
  void purgeDataRetentionOlderRecords(Map<String, Long> accounts);

  boolean supportsInOperator();

  void delete(Class<? extends GoogleDataStoreAware> clazz, String fieldName, String fieldValue);
}
