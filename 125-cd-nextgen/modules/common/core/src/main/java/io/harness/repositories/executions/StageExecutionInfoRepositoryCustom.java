/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.utils.StageStatus;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDP)
public interface StageExecutionInfoRepositoryCustom {
  UpdateResult update(Scope scope, String stageExecutionId, Map<String, Object> updates);

  UpdateResult updateStatus(Scope scope, String stageExecutionId, StageStatus status);

  List<StageExecutionInfo> listSucceededStageExecutionNotIncludeCurrent(
      ExecutionInfoKey executionInfoKey, String executionId, int limit);

  DeleteResult deleteAll(Criteria criteria);

  StageExecutionInfo findByStageExecutionId(String stageExecutionId, Scope scope);
}
