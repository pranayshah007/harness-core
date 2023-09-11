/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy.retry.v1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.yaml.core.failurestrategy.v1.FailureStrategyActionConfigV1;
import io.harness.yaml.core.failurestrategy.v1.NGFailureActionTypeV1;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class RetryFailureActionConfigV1 extends FailureStrategyActionConfigV1 {
  NGFailureActionTypeV1 type = NGFailureActionTypeV1.RETRY;
  RetryFailureSpecConfigV1 spec;
}
