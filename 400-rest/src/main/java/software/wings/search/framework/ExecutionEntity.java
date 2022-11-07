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
