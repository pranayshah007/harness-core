/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.deployment;

import io.harness.event.reconciliation.service.DeploymentReconService;
import io.harness.event.reconciliation.service.DeploymentReconServiceImpl;

import software.wings.beans.WorkflowExecution;
import software.wings.search.framework.ExecutionEntity;

import com.google.inject.Inject;

public class DeploymentExecutionEntity implements ExecutionEntity<WorkflowExecution> {
  @Inject DeploymentReconServiceImpl deploymentReconService;

  public static final Class<WorkflowExecution> SOURCE_ENTITY_CLASS = WorkflowExecution.class;

  private static final String ENTITY_COUNT =
      "SELECT COUNT(DISTINCT(EXECUTIONID)) FROM DEPLOYMENT WHERE ACCOUNTID=? AND ((STARTTIME>=? AND STARTTIME<=?) OR (ENDTIME>=? AND ENDTIME<=?)) AND PARENT_EXECUTION IS NULL;";

  private static final String GET_DUPLICATE =
      "SELECT DISTINCT(D.EXECUTIONID) FROM DEPLOYMENT D,(SELECT COUNT(EXECUTIONID), EXECUTIONID FROM DEPLOYMENT A WHERE ACCOUNTID = ? AND ((STARTTIME>=? AND STARTTIME<=?) OR (ENDTIME>=? AND ENDTIME<=?)) GROUP BY EXECUTIONID HAVING COUNT(EXECUTIONID) > 1) AS B WHERE D.EXECUTIONID = B.EXECUTIONID;";

  private static final String DELETE_SET = "DELETE FROM DEPLOYMENT WHERE EXECUTIONID = ANY (?);";

  private static final String RUNNING_EXECUTIONS =
      "SELECT EXECUTIONID,STATUS FROM DEPLOYMENT WHERE ACCOUNTID=? AND STATUS IN ('RUNNING','PAUSED')";

  @Override
  public DeploymentReconService getReconService() {
    return deploymentReconService;
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
  public Class<WorkflowExecution> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }
}
