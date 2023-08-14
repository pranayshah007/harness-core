/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.repositories.planExecutionJson;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.execution.PlanExecutionExpansion;

import java.util.Set;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface PlanExecutionExpansionRepositoryCustom {
  void update(String planExecutionId, Update update, long lockTimeoutInMinutes);

  PlanExecutionExpansion find(Query query);

  void deleteAllExpansions(Set<String> planExecutionIds);

  /**
   * Update multiple records of PlanExpansion with given find query and updateOperations
   * @param query
   * @param updateOps
   * @return
   */
  void multiUpdate(Query query, Update updateOps);
}
