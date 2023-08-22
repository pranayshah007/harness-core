/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.execution.PlanExecution;

import java.util.List;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

public interface PlanExecutionRepositoryCustom {
  PlanExecution getWithProjectionsWithoutUuid(String planExecutionId, List<String> fieldNames);

  /**
   * Update Plan execution with given find query and updateOperations, it returns new record after update
   * @param query
   * @param updateOps
   * @param upsert
   * @return
   */
  PlanExecution updatePlanExecution(Query query, Update updateOps, boolean upsert);

  /**
   * Update multiple records of Plan execution with given find query and updateOperations
   * @param query
   * @param updateOps
   * @return
   */
  void multiUpdatePlanExecution(Query query, Update updateOps);

  PlanExecution getPlanExecutionWithProjections(String planExecutionId, List<String> excludedFieldNames);

  PlanExecution getPlanExecutionWithIncludedProjections(String planExecutionId, List<String> includedFieldNames);

  /**
   * Fetch plan executions from analytics node
   * Query should contain projection fields else it will throw exception and max batch size of iterator is 1k
   * @param query
   * @return
   */
  CloseableIterator<PlanExecution> fetchPlanExecutionsFromAnalytics(Query query);
}
