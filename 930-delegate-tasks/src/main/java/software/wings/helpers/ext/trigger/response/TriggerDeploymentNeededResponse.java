/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.trigger.response;

import io.harness.beans.ExecutionStatus;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TriggerDeploymentNeededResponse extends TriggerResponse {
  private boolean deploymentNeeded;

  @Builder
  public TriggerDeploymentNeededResponse(ExecutionStatus executionStatus, String errorMsg, boolean deploymentNeeded) {
    super(null, executionStatus, errorMsg);
    this.deploymentNeeded = deploymentNeeded;
  }
}
