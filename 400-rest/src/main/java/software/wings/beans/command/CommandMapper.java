/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommandMapper {
  public software.wings.beans.dto.Command toCommandDTO(Command command) {
    if (command == null) {
      return null;
    }

    return software.wings.beans.dto.Command.builder()
        .name(command.getName())
        .commandType(command.getCommandType())
        // ensure we convert all potential Commands in the tree.
        .commandUnits(
            command.getCommandUnits()
                .stream()
                .map(commandUnit -> commandUnit instanceof Command ? toCommandDTO((Command) commandUnit) : commandUnit)
                .collect(Collectors.toList()))
        .commandUnitType(command.getCommandUnitType())
        .commandExecutionStatus(command.getCommandExecutionStatus())
        .deploymentType(command.getDeploymentType())
        .templateVariables(command.getTemplateVariables())
        .variables(command.getVariables())
        // getArtifactNeeded() is not exposed and value isn't used at all, so set it to isArtifactNeeded instead.
        .artifactNeeded(command.isArtifactNeeded())
        .build();
  }
}
