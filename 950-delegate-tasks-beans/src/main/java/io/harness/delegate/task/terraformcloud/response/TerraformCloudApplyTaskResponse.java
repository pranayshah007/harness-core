/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.terraformcloud.TerraformCloudTaskType;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.CDP)
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TerraformCloudApplyTaskResponse extends TerraformCloudDelegateTaskResponse {
  String runId;
  String tfOutput;

  @Override
  public TerraformCloudTaskType getTaskType() {
    return TerraformCloudTaskType.RUN_APPLY;
  }
}