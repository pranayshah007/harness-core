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
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_WEBAPP_SLOT_SETUP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_WEBAPP_SLOT_SWAP;

import static java.util.Arrays.asList;

import software.wings.beans.GraphNode;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.CommandUnitType;

import java.util.List;

public class AzureWebAppArtifactCommands implements ArtifactCommands {
  @Override
  public boolean isInternal() {
    return true;
  }

  @Override
  public List<Command> getDefaultCommands() {
    return asList(getAzureWebAppSlotSetupCommand(), getAzureWebAppSlotSwapCommand());
  }

  private Command getAzureWebAppSlotSetupCommand() {
    return aCommand()
        .withCommandType(CommandType.SETUP)
        .withGraph(aGraph()
                       .withGraphName(AZURE_WEBAPP_SLOT_SETUP)
                       .addNodes(GraphNode.builder()
                                     .origin(true)
                                     .id(graphIdGenerator("node"))
                                     .name(AZURE_WEBAPP_SLOT_SETUP)
                                     .type(CommandUnitType.AZURE_WEBAPP.name())
                                     .build())
                       .buildPipeline())
        .build();
  }

  /**
   * Get Code Deploy Command
   * @return
   */
  private Command getAzureWebAppSlotSwapCommand() {
    return aCommand()
        .withCommandType(CommandType.INSTALL)
        .withGraph(aGraph()
                       .withGraphName(AZURE_WEBAPP_SLOT_SWAP)
                       .addNodes(GraphNode.builder()
                                     .origin(true)
                                     .id(graphIdGenerator("node"))
                                     .name(AZURE_WEBAPP_SLOT_SWAP)
                                     .type(CommandUnitType.AZURE_WEBAPP.name())
                                     .build())
                       .buildPipeline())
        .build();
  }
}
