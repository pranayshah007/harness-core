/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.deployment;

import io.harness.event.reconciliation.service.DeploymentReconService;
import io.harness.event.reconciliation.service.DeploymentStepReconServiceImpl;

import software.wings.search.framework.ExecutionEntity;
import software.wings.sm.StateExecutionInstance;

import com.google.inject.Inject;

public class DeploymentStepExecutionEntity implements ExecutionEntity<StateExecutionInstance> {
  @Inject DeploymentStepReconServiceImpl deploymentStepReconService;

  public static final Class<StateExecutionInstance> SOURCE_ENTITY_CLASS = StateExecutionInstance.class;

  private static final String RUNNING_EXECUTIONS =
      "SELECT ID,STATUS FROM DEPLOYMENT_STEP WHERE ACCOUNT_ID=? AND STATUS IN ('RUNNING','PAUSED');";
  private static final String ENTITY_COUNT =
      "SELECT COUNT(DISTINCT(ID)) FROM DEPLOYMENT_STEP WHERE ACCOUNT_ID=? AND ((START_TIME>=? AND START_TIME<=?) OR (END_TIME>=? AND END_TIME<=?));";
  private static final String DELETE_SET = "DELETE FROM DEPLOYMENT_STEP WHERE ID = ANY (?);";
  private static final String GET_DUPLICATE =
      "SELECT DISTINCT(D.ID) FROM DEPLOYMENT_STEP D,(SELECT COUNT(ID), ID FROM DEPLOYMENT_STEP A WHERE ACCOUNT_ID = ? AND ((START_TIME>=? AND START_TIME<=?) OR (END_TIME>=? AND END_TIME<=?)) GROUP BY ID HAVING COUNT(ID) > 1) AS B WHERE D.ID = B.ID;";

  @Override
  public DeploymentReconService getReconService() {
    return deploymentStepReconService;
  }

  @Override
  public String getRunningExecutionQuery() {
    return RUNNING_EXECUTIONS;
  }

  @Override
  public String getEntityCountQuery() {
    return ENTITY_COUNT;
  }

  @Override
  public String getDuplicatesQuery() {
    return GET_DUPLICATE;
  }

  @Override
  public String getDeleteSetQuery() {
    return DELETE_SET;
  }

  @Override
  public Class<StateExecutionInstance> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }
}
