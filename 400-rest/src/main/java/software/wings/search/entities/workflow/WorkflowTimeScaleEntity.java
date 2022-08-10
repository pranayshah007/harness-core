/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.workflow;

import io.harness.event.reconciliation.service.LookerEntityReconService;
import io.harness.event.reconciliation.service.WorkflowEntityReconServiceImpl;
import io.harness.persistence.PersistentEntity;

import software.wings.beans.Workflow;
import software.wings.search.framework.ChangeHandler;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.timescale.migrations.MigrateWorkflowsToTimeScaleDB;

import com.google.inject.Inject;
import java.util.Set;

public class WorkflowTimeScaleEntity implements TimeScaleEntity<Workflow> {
  @Inject private WorkflowTimescaleChangeHandler workflowTimescaleChangeHandler;
  @Inject private WorkflowEntityReconServiceImpl workflowEntityReconService;
  @Inject private MigrateWorkflowsToTimeScaleDB migrateWorkflowsToTimeScaleDB;

  public static final Class<Workflow> SOURCE_ENTITY_CLASS = Workflow.class;

  @Override
  public Class<Workflow> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }

  @Override
  public ChangeHandler getChangeHandler() {
    return workflowTimescaleChangeHandler;
  }

  @Override
  public LookerEntityReconService getReconService() {
    return workflowEntityReconService;
  }

  @Override
  public boolean toProcessChangeEvent(Set<String> accountIds, PersistentEntity entity) {
    Workflow workflow = (Workflow) entity;

    return accountIds.contains(workflow.getAccountId());
  }

  @Override
  public boolean runMigration(String accountId) {
    return migrateWorkflowsToTimeScaleDB.runTimeScaleMigration(accountId);
  }

  @Override
  public void savetoTimescale(Workflow entity) {}

  @Override
  public void deleteFromTimescale(String id) {}
}
