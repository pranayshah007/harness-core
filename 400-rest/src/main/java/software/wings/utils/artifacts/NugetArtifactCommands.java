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
import static software.wings.beans.command.CommandUnitType.EXEC;
import static software.wings.beans.command.CommandUnitType.PROCESS_CHECK_STOPPED;

import static java.util.Arrays.asList;

import software.wings.beans.GraphNode;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;

import com.google.common.collect.ImmutableMap;
import java.util.List;

public class NugetArtifactCommands implements ArtifactCommands {
  @Override
  public boolean isInternal() {
    return false;
  }

  @Override
  public List<Command> getDefaultCommands() {
    return asList(getStartCommand(), getInstallCommand(), getStopCommand());
  }

  /**
   * Gets start command graph.
   * @return the start command graph
   */
  private Command getStartCommand() {
    return aCommand()
        .withCommandType(CommandType.START)
        .withGraph(aGraph()
                       .withGraphName("Start")
                       .addNodes(ArtifactCommandHelper.getStartServiceNode(
                                     "echo \"service start script should be added here\""),
                           ArtifactCommandHelper.getServiceRunningNode())
                       .buildPipeline())
        .build();
  }

  /**
   * Gets stop command graph.
   * @return the stop command graph
   */
  private Command getStopCommand() {
    return aCommand()
        .withCommandType(CommandType.STOP)
        .withGraph(
            aGraph()
                .withGraphName("Stop")
                .addNodes(ArtifactCommandHelper.getStopServiceNode("echo \"service stop script should be added here\""),
                    GraphNode.builder()
                        .id(graphIdGenerator("node"))
                        .name("Service Stopped")
                        .type(PROCESS_CHECK_STOPPED.name())
                        .properties(ImmutableMap.<String, Object>builder()
                                        .put("commandString", "echo \"service stopped check should be added here\"")
                                        .build())
                        .build())
                .buildPipeline())
        .build();
  }

  /**
   * Get Install Command
   * @return the install command graph
   */
  private Command getInstallCommand() {
    return aCommand()
        .withCommandType(CommandType.INSTALL)
        .withGraph(
            aGraph()
                .withGraphName("Install")
                .addNodes(ArtifactCommandHelper.getSetupRuntimePathsNode(), ArtifactCommandHelper.getCopyArtifactNode(),
                    GraphNode.builder()
                        .id(graphIdGenerator("node"))
                        .name("Expand Artifact")
                        .type(EXEC.name())
                        .properties(ImmutableMap.<String, Object>builder()
                                        .put("commandPath", "$WINGS_RUNTIME_PATH")
                                        .put("commandString", "nuget install \"$ARTIFACT_FILE_NAME\"")
                                        .build())
                        .build(),
                    ArtifactCommandHelper.getCopyConfigsNode())
                .buildPipeline())
        .build();
  }
}
