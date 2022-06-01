/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.CommandExecutionStatus;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
public class CloudFormationCreateStackNGResponse extends CloudFormationCommandNGResponse {
  String stackId;
  Map<String, Object> cloudFormationOutputMap;
  boolean existentStack;
  String stackStatus;

  @Builder
  public CloudFormationCreateStackNGResponse(CommandExecutionStatus commandExecutionStatus,
      Map<String, Object> cloudFormationOutputMap, String stackId, boolean existentStack, String stackStatus) {
    super(commandExecutionStatus);
    this.stackId = stackId;
    this.cloudFormationOutputMap = cloudFormationOutputMap;
    this.existentStack = existentStack;
    this.stackStatus = stackStatus;
  }
}
