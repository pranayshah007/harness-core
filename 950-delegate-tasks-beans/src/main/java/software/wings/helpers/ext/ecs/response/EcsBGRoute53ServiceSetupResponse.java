/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.ecs.response;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class EcsBGRoute53ServiceSetupResponse extends EcsCommandResponse {
  private ContainerSetupCommandUnitExecutionData setupData;

  @Builder
  public EcsBGRoute53ServiceSetupResponse(CommandExecutionStatus commandExecutionStatus, String output,
      ContainerSetupCommandUnitExecutionData setupData, boolean timeoutFailure) {
    super(commandExecutionStatus, output, timeoutFailure);
    this.setupData = setupData;
  }
}
