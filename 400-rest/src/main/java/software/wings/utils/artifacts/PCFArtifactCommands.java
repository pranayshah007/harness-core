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
import static software.wings.service.impl.workflow.WorkflowServiceHelper.PCF_RESIZE;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.PCF_SETUP;
import static software.wings.utils.ArtifactType.PCF;

import static java.util.Arrays.asList;

import software.wings.beans.GraphNode;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;

import java.util.List;

public class PCFArtifactCommands implements ArtifactCommands {
  @Override
  public boolean isInternal() {
    return true;
  }

  @Override
  public List<Command> getDefaultCommands() {
    return asList(getPcfSetupCommand());
  }

  /**
   * Get Code Deploy Command
   * @return
   */
  private Command getPcfSetupCommand() {
    return aCommand()
        .withCommandType(CommandType.SETUP)
        .withGraph(aGraph()
                       .withGraphName(PCF_SETUP)
                       .addNodes(GraphNode.builder()
                                     .origin(true)
                                     .id(graphIdGenerator("node"))
                                     .name(PCF_SETUP)
                                     .type(PCF.name())
                                     .build())
                       .buildPipeline())
        .build();
  }

  private Command getPcfDeployCommand() {
    return aCommand()
        .withCommandType(CommandType.RESIZE)
        .withGraph(aGraph()
                       .withGraphName(PCF_RESIZE)
                       .addNodes(GraphNode.builder()
                                     .origin(true)
                                     .id(graphIdGenerator("node"))
                                     .name(PCF_RESIZE)
                                     .type(PCF.name())
                                     .build())
                       .buildPipeline())
        .build();
  }
}
