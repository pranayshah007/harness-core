/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.outcome;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.pms.pipelinestage.outcome.PipelineStageOutcome")
public class PipelineStageOutcome extends OrchestrationMap implements Outcome {
  public PipelineStageOutcome() {}

  public PipelineStageOutcome(Map<String, Object> map) {
    super(map);
  }

  public static PmsStepParameters parse(String json) {
    if (EmptyPredicate.isEmpty(json)) {
      return null;
    }
    return new PmsStepParameters(RecastOrchestrationUtils.fromJson(json));
  }

  public static PmsStepParameters parse(Map<String, Object> map) {
    if (EmptyPredicate.isEmpty(map)) {
      return null;
    }

    return new PmsStepParameters(map);
  }
}