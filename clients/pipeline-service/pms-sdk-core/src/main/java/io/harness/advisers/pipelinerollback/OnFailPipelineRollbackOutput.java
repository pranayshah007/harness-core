/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.pipelinerollback;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("onFailPipelineRollbackOutput")
@JsonTypeName("onFailPipelineRollbackOutput")
@OwnedBy(HarnessTeam.CDC)
@RecasterAlias("io.harness.advisers.pipelinerollback.OnFailPipelineRollbackOutput")
public class OnFailPipelineRollbackOutput implements ExecutionSweepingOutput {
  boolean shouldStartPipelineRollback;
  List<Level> levelsAtFailurePoint;
}
