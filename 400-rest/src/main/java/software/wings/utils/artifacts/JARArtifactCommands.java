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
import static software.wings.beans.command.CommandUnitType.COMMAND;
import static software.wings.beans.command.CommandUnitType.PROCESS_CHECK_RUNNING;
import static software.wings.beans.command.CommandUnitType.PROCESS_CHECK_STOPPED;

import static java.util.Arrays.asList;

import software.wings.beans.GraphNode;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;

import com.google.common.collect.ImmutableMap;
import java.util.List;

public class JARArtifactCommands implements ArtifactCommands {
  @Override
  public boolean isInternal() {
    return false;
  }

  @Override
  public List<Command> getDefaultCommands() {
    return asList(getStartCommand(), getStopCommand(), getInstallCommand());
  }

  /**
   * Gets start command graph.
   *
   * @return the start command graph
   */
  private Command getStartCommand() {
    return aCommand()
        .withCommandType(CommandType.START)
        .withGraph(aGraph()
                       .withGraphName("Start")
                       .addNodes(ArtifactCommandHelper.getStartServiceNode(
                                     "java -jar \"$WINGS_RUNTIME_PATH/$ARTIFACT_FILE_NAME\""),
                           GraphNode.builder()
                               .id(graphIdGenerator("node"))
                               .name("Process Running")
                               .type(PROCESS_CHECK_RUNNING.name())
                               .properties(ImmutableMap.<String, Object>builder()
                                               .put("commandString",
                                                   "i=0\n"
                                                       + "while [ \"$i\" -lt 30 ]\n"
                                                       + "do\n"
                                                       + "  pgrep -f \"$WINGS_RUNTIME_PATH/$ARTIFACT_FILE_NAME\"\n"
                                                       + "  rc=$?\n"
                                                       + "  if [ \"$rc\" -eq 0 ]\n"
                                                       + "  then\n"
                                                       + "    exit 0\n"
                                                       + "    sleep 1\n"
                                                       + "    i=$((i+1))\n"
                                                       + "  else\n"
                                                       + "    sleep 1\n"
                                                       + "    i=$((i+1))\n"
                                                       + "  fi\n"
                                                       + "done\n"
                                                       + "exit 1")
                                               .build())

                               .build())
                       .buildPipeline())
        .build();
  }

  /**
   * Gets stop command graph.
   *
   * @return the stop command graph
   */
  private Command getStopCommand() {
    return aCommand()
        .withCommandType(CommandType.STOP)
        .withGraph(aGraph()
                       .withGraphName("Stop")
                       .addNodes(ArtifactCommandHelper.getStopServiceNode(
                                     "\npgrep -f \"$WINGS_RUNTIME_PATH/$ARTIFACT_FILE_NAME\" | xargs kill  || true"),
                           GraphNode.builder()
                               .id(graphIdGenerator("node"))
                               .name("Process Stopped")
                               .type(PROCESS_CHECK_STOPPED.name())
                               .properties(ImmutableMap.<String, Object>builder()
                                               .put("commandString",
                                                   "i=0\n"
                                                       + "while [ \"$i\" -lt 30 ]\n"
                                                       + "do\n"
                                                       + "  pgrep -f \"$WINGS_RUNTIME_PATH/$ARTIFACT_FILE_NAME\"\n"
                                                       + "  rc=$?\n"
                                                       + "  if [ \"$rc\" -eq 0 ]\n"
                                                       + "  then\n"
                                                       + "    sleep 1\n"
                                                       + "    i=$((i+1))\n"
                                                       + "  else\n"
                                                       + "    exit 0\n"
                                                       + "  fi\n"
                                                       + "done\n"
                                                       + "exit 1")
                                               .build())
                               .build())
                       .buildPipeline())
        .build();
  }

  /**
   * Gets install command graph.
   *
   * @return the install command graph
   */
  private Command getInstallCommand() {
    return aCommand()
        .withCommandType(CommandType.INSTALL)
        .withGraph(aGraph()
                       .withGraphName("Install")
                       .addNodes(ArtifactCommandHelper.getSetupRuntimePathsNode(),
                           GraphNode.builder()
                               .id(graphIdGenerator("node"))
                               .name("Stop")
                               .type(COMMAND.name())
                               .properties(ImmutableMap.<String, Object>builder().put("referenceId", "Stop").build())
                               .build(),
                           ArtifactCommandHelper.getCopyArtifactNode(), ArtifactCommandHelper.getCopyConfigsNode(),
                           GraphNode.builder()
                               .id(graphIdGenerator("node"))
                               .name("Start")
                               .type(COMMAND.name())
                               .properties(ImmutableMap.<String, Object>builder().put("referenceId", "Start").build())
                               .build())
                       .buildPipeline())
        .build();
  }
}
