/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static software.wings.beans.Graph.graphIdGenerator;
import static software.wings.beans.command.CommandUnitType.COMMAND;
import static software.wings.beans.command.CommandUnitType.COPY_CONFIGS;
import static software.wings.beans.command.CommandUnitType.SCP;
import static software.wings.beans.command.CommandUnitType.SETUP_ENV;

import software.wings.beans.AppContainer;
import software.wings.beans.GraphNode;
import software.wings.beans.command.Command;
import software.wings.beans.command.ScpCommandUnit;
import software.wings.beans.command.SetupEnvCommandUnit;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;

public abstract class ContainerFamilyCommandProvider {
  /**
   * Gets default commands.
   *
   * @param artifactType the artifact type
   * @param appContainer the app container
   * @return the default commands
   */
  public List<Command> getDefaultCommands(ArtifactType artifactType, AppContainer appContainer) {
    return Lists.newArrayList(
        getStartCommand(artifactType), getInstallCommand(artifactType, appContainer), getStopCommand(artifactType));
  }

  /**
   * Gets stop command graph.
   *
   * @param artifactType the artifact type
   * @return the stop command graph
   */
  protected abstract Command getStopCommand(ArtifactType artifactType);

  /**
   * Gets start command graph.
   *
   * @param artifactType the artifact type
   * @return the start command graph
   */
  protected abstract Command getStartCommand(ArtifactType artifactType);

  /**
   * Gets install command graph.
   *
   * @param artifactType the artifact type
   * @param appContainer the app container
   * @return the install command graph
   */
  protected abstract Command getInstallCommand(ArtifactType artifactType, AppContainer appContainer);

  public abstract boolean isInternal();

  protected static GraphNode getStartNode() {
    return GraphNode.builder()
        .id(graphIdGenerator("node"))
        .name("Start")
        .type(COMMAND.name())
        .properties(ImmutableMap.<String, Object>builder().put("referenceId", "Start").build())
        .build();
  }

  protected static GraphNode getCopyConfigsNode() {
    return GraphNode.builder()
        .id(graphIdGenerator("node"))
        .name("Copy Configs")
        .type(COPY_CONFIGS.name())
        .properties(ImmutableMap.<String, Object>builder().put("destinationParentPath", "$WINGS_RUNTIME_PATH").build())
        .build();
  }

  protected static GraphNode getCopyAppStackNode() {
    return GraphNode.builder()
        .id(graphIdGenerator("node"))
        .name("Copy App Stack")
        .type(SCP.name())
        .properties(ImmutableMap.<String, Object>builder()
                        .put("destinationDirectoryPath", "$WINGS_RUNTIME_PATH")
                        .put("fileCategory", ScpCommandUnit.ScpFileCategory.APPLICATION_STACK)
                        .build())
        .build();
  }

  protected static GraphNode getStopNode() {
    return GraphNode.builder()
        .id(graphIdGenerator("node"))
        .name("Stop")
        .type(COMMAND.name())
        .properties(ImmutableMap.<String, Object>builder().put("referenceId", "Stop").build())
        .build();
  }

  protected static GraphNode getSetupRuntimePathsNode() {
    return GraphNode.builder()
        .origin(true)
        .id(graphIdGenerator("node"))
        .name("Setup Runtime Paths")
        .type(SETUP_ENV.name())
        .properties(ImmutableMap.<String, Object>builder()
                        .put("commandString", SetupEnvCommandUnit.SETUP_ENV_COMMAND_STRING)
                        .build())
        .build();
  }
}
