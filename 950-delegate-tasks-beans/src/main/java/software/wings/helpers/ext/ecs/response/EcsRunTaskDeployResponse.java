/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.ecs.response;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.CommandExecutionStatus;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class EcsRunTaskDeployResponse extends EcsCommandResponse {
  private List<String> previousRegisteredRunTaskDefinitions;
  private List<String> previousRunTaskArns;
  private List<String> newRegisteredRunTaskDefinitions;
  private List<String> newRunTaskArns;

  @Builder
  public EcsRunTaskDeployResponse(CommandExecutionStatus commandExecutionStatus, String output,
      List<String> newRunTaskArns, List<String> newRegisteredRunTaskDefinitions, List<String> previousRunTaskArns,
      List<String> previousRegisteredRunTaskDefinitions, boolean timeoutFailure) {
    super(commandExecutionStatus, output, timeoutFailure);
    this.newRegisteredRunTaskDefinitions = newRegisteredRunTaskDefinitions;
    this.previousRegisteredRunTaskDefinitions = previousRegisteredRunTaskDefinitions;
    this.newRunTaskArns = newRunTaskArns;
    this.previousRunTaskArns = previousRunTaskArns;
  }
}
