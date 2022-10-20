/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.execution;

import io.harness.pms.contracts.execution.Status;
import io.harness.tasks.ResponseData;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PipelineStageResponseData implements ResponseData {
  Status status;
}
