/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.framework;

import io.harness.event.reconciliation.service.DeploymentReconService;
import io.harness.persistence.PersistentEntity;

public interface ExecutionEntity<T extends PersistentEntity> {
  DeploymentReconService getReconService();
  String getRunningExecutionQuery();
  String getEntityCountQuery();
  String getDuplicatesQuery();
  String getDeleteSetQuery();
  Class<T> getSourceEntityClass();
}
