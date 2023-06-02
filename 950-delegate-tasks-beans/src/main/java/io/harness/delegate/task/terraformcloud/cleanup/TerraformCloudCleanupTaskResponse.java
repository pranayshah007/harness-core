/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud.cleanup;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.logging.CommandExecutionStatus;

import lombok.Builder;
import lombok.Value;

@OwnedBy(CDP)
@Value
@Builder
public class TerraformCloudCleanupTaskResponse implements DelegateResponseData {
  CommandExecutionStatus commandExecutionStatus;
  String runId;
  String errorMessage;
}