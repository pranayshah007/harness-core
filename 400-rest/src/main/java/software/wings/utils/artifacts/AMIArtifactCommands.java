/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils.artifacts;

import static software.wings.beans.Graph.Builder.aGraph;
import static software.wings.beans.Graph.graphIdGenerator;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.CommandUnitType.AWS_AMI;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_SETUP_COMMAND_NAME;
import static software.wings.sm.states.AwsAmiServiceDeployState.ASG_COMMAND_NAME;

import static java.util.Arrays.asList;

import software.wings.beans.GraphNode;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;

import java.util.List;

public class AMIArtifactCommands implements ArtifactCommands {
  @Override
  public boolean isInternal() {
    return true;
  }

  @Override
  public List<Command> getDefaultCommands() {
    return asList(getAmiSetupCommandUnit(), getAmiDeployCommandUnit());
  }

  private Command getAmiSetupCommandUnit() {
    return aCommand()
        .withCommandType(CommandType.SETUP)
        .withGraph(aGraph()
                       .withGraphName(AMI_SETUP_COMMAND_NAME)
                       .addNodes(GraphNode.builder()
                                     .origin(true)
                                     .id(graphIdGenerator("node"))
                                     .name(AMI_SETUP_COMMAND_NAME)
                                     .type(AWS_AMI.name())
                                     .build())
                       .buildPipeline())
        .build();
  }

  /**
   * Get Code Deploy Command
   * @return
   */
  private Command getAmiDeployCommandUnit() {
    return aCommand()
        .withCommandType(CommandType.INSTALL)
        .withGraph(aGraph()
                       .withGraphName(ASG_COMMAND_NAME)
                       .addNodes(GraphNode.builder()
                                     .origin(true)
                                     .id(graphIdGenerator("node"))
                                     .name(ASG_COMMAND_NAME)
                                     .type(AWS_AMI.name())
                                     .build())
                       .buildPipeline())
        .build();
  }
}
