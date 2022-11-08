/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.executionInterrupt;

import io.harness.event.reconciliation.service.DeploymentReconService;
import io.harness.event.reconciliation.service.ExecutionInterruptReconServiceImpl;

import software.wings.search.framework.ExecutionEntity;
import software.wings.sm.ExecutionInterrupt;

import com.google.inject.Inject;

public class ExecutionInterruptEntity implements ExecutionEntity<ExecutionInterrupt> {
  @Inject ExecutionInterruptReconServiceImpl executionInterruptReconService;

  public static final Class<ExecutionInterrupt> SOURCE_ENTITY_CLASS = ExecutionInterrupt.class;

  public static final String EMPTY_STRING = "";
  private static final String ENTITY_COUNT =
      "SELECT COUNT(DISTINCT(ID)) FROM EXECUTION_INTERRUPT WHERE ACCOUNT_ID=? AND ((CREATED_AT>=? AND CREATED_AT<=?) OR (LAST_UPDATED_AT>=? AND LAST_UPDATED_AT<=?));";
  private static final String GET_DUPLICATE =
      "SELECT DISTINCT(D.ID) FROM EXECUTION_INTERRUPT D,(SELECT COUNT(ID), ID FROM EXECUTION_INTERRUPT A WHERE ACCOUNT_ID = ? AND ((CREATED_AT>=? AND CREATED_AT<=?) OR (LAST_UPDATED_AT>=? AND LAST_UPDATED_AT<=?)) GROUP BY ID HAVING COUNT(ID) > 1) AS B WHERE D.ID = B.ID;";
  private static final String DELETE_SET = "DELETE FROM EXECUTION_INTERRUPT WHERE ID = ANY (?);";

  @Override
  public DeploymentReconService getReconService() {
    return executionInterruptReconService;
  }

  @Override
  public String getRunningExecutionQuery() {
    return EMPTY_STRING;
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
  public Class<ExecutionInterrupt> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }
}
