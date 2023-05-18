/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.tasks.ResponseData;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("combinedRollbackSweepingOutput")
@JsonTypeName("combinedRollbackSweepingOutput")
@RecasterAlias("io.harness.cdng.pipeline.steps.CombinedRollbackSweepingOutput")
public class CombinedRollbackSweepingOutput implements ExecutionSweepingOutput {
  Map<String, ResponseData> responseDataMap;
}
