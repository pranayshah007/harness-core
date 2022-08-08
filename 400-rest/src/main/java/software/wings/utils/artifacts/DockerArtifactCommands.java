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
import static software.wings.beans.command.CommandUnitType.ECS_SETUP;
import static software.wings.beans.command.CommandUnitType.KUBERNETES_SETUP;
import static software.wings.beans.command.CommandUnitType.RESIZE;
import static software.wings.beans.command.CommandUnitType.RESIZE_KUBERNETES;

import static java.util.Arrays.asList;

import software.wings.beans.GraphNode;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;

import java.util.List;

public class DockerArtifactCommands implements ArtifactCommands {
  @Override
  public boolean isInternal() {
    return true;
  }

  @Override
  public List<Command> getDefaultCommands() {
    return asList(aCommand()
                      .withCommandType(CommandType.SETUP)
                      .withGraph(aGraph()
                                     .withGraphName("Setup Service Cluster")
                                     .addNodes(GraphNode.builder()
                                                   .origin(true)
                                                   .id(graphIdGenerator("node"))
                                                   .name("Setup ECS Service")
                                                   .type(ECS_SETUP.name())
                                                   .build())
                                     .buildPipeline())
                      .build(),
        aCommand()
            .withCommandType(CommandType.SETUP)
            .withGraph(aGraph()
                           .withGraphName("Setup Replication Controller")
                           .addNodes(GraphNode.builder()
                                         .origin(true)
                                         .id(graphIdGenerator("node"))
                                         .name("Setup Kubernetes Replication Controller")
                                         .type(KUBERNETES_SETUP.name())
                                         .build())
                           .buildPipeline())
            .build(),
        aCommand()
            .withCommandType(CommandType.RESIZE)
            .withGraph(aGraph()
                           .withGraphName("Resize Service Cluster")
                           .addNodes(GraphNode.builder()
                                         .origin(true)
                                         .id(graphIdGenerator("node"))
                                         .name("Resize ECS Service")
                                         .type(RESIZE.name())
                                         .build())
                           .buildPipeline())
            .build(),
        aCommand()
            .withCommandType(CommandType.RESIZE)
            .withGraph(aGraph()
                           .withGraphName("Resize Replication Controller")
                           .addNodes(GraphNode.builder()
                                         .origin(true)
                                         .id(graphIdGenerator("node"))
                                         .name("Resize Kubernetes Replication Controller")
                                         .type(RESIZE_KUBERNETES.name())
                                         .build())
                           .buildPipeline())
            .build());
  }
}
