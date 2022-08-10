/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import io.harness.event.reconciliation.service.LookerEntityReconService;
import io.harness.persistence.PersistentEntity;

import java.util.Set;

public interface TimeScaleEntity<T extends PersistentEntity> {
  Class<T> getSourceEntityClass();
  ChangeHandler getChangeHandler();
  LookerEntityReconService getReconService();
  boolean toProcessChangeEvent(Set<String> accountIds, PersistentEntity entity);
  boolean runMigration(String accountId);
  void savetoTimescale(T entity);
  void deleteFromTimescale(String id);
}
