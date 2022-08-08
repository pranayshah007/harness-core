/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils.artifacts;

import static software.wings.beans.Graph.graphIdGenerator;
import static software.wings.beans.command.CommandUnitType.COPY_CONFIGS;
import static software.wings.beans.command.CommandUnitType.EXEC;
import static software.wings.beans.command.CommandUnitType.PROCESS_CHECK_RUNNING;
import static software.wings.beans.command.CommandUnitType.SCP;
import static software.wings.beans.command.CommandUnitType.SETUP_ENV;

import software.wings.beans.GraphNode;
import software.wings.beans.command.ScpCommandUnit;
import software.wings.beans.command.SetupEnvCommandUnit;
import software.wings.utils.ArtifactType;

import com.google.common.collect.ImmutableMap;

public class ArtifactCommandHelper {
  public static ArtifactCommands getArtifactCommands(ArtifactType artifactType) {
    switch (artifactType) {
      case JAR:
        return new JARArtifactCommands();
      case WAR:
        return new WARArtifactCommands();
      case TAR:
        return new TARArtifactCommands();
      case ZIP:
        return new ZIPArtifactCommands();
      case NUGET:
        return new NugetArtifactCommands();
      case DOCKER:
        return new DockerArtifactCommands();
      case RPM:
        return new RPMArtifactCommand();
      case AWS_LAMBDA:
        return new AWSLambdaArtifactCommands();
      case AWS_CODEDEPLOY:
        return new AWSCodeDeployArtifactCommands();
      case PCF:
        return new PCFArtifactCommands();
      case AMI:
        return new AMIArtifactCommands();
      case AZURE_MACHINE_IMAGE:
        return new AzureMachineImageArtifactCommands();
      case AZURE_WEBAPP:
        return new AzureWebAppArtifactCommands();
      case IIS:
        return new IISArtifactCommands();
      case OTHER:
        return new OtherArtifactCommands();
      case IIS_APP:
        return new IISAppArtifactCommands();
      case IIS_VirtualDirectory:
        return new IISVirtualDirectoryArtifactCommands();
      default:
        return null;
    }
  }

  public static GraphNode getSetupRuntimePathsNode() {
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

  public static GraphNode getCopyConfigsNode() {
    return GraphNode.builder()
        .id(graphIdGenerator("node"))
        .name("Copy Configs")
        .type(COPY_CONFIGS.name())
        .properties(ImmutableMap.<String, Object>builder().put("destinationParentPath", "$WINGS_RUNTIME_PATH").build())
        .build();
  }

  public static GraphNode getCopyArtifactNode() {
    return GraphNode.builder()
        .id(graphIdGenerator("node"))
        .name("Copy Artifact")
        .type(SCP.name())
        .properties(ImmutableMap.<String, Object>builder()
                        .put("fileCategory", ScpCommandUnit.ScpFileCategory.ARTIFACTS)
                        .put("destinationDirectoryPath", "$WINGS_RUNTIME_PATH")
                        .build())
        .build();
  }

  public static GraphNode getStartServiceNode(String commandString) {
    return GraphNode.builder()
        .origin(true)
        .id(graphIdGenerator("node"))
        .type(EXEC.name())
        .name("Start Service")
        .properties(ImmutableMap.<String, Object>builder()
                        .put("commandPath", "$WINGS_RUNTIME_PATH")
                        .put("commandString", "java -jar \"$WINGS_RUNTIME_PATH/$ARTIFACT_FILE_NAME\"")
                        .build())
        .build();
  }

  public static GraphNode getStopServiceNode(String commandString) {
    return GraphNode.builder()
        .origin(true)
        .id(graphIdGenerator("node"))
        .type(EXEC.name())
        .name("Stop Service")
        .properties(ImmutableMap.<String, Object>builder()
                        .put("commandPath", "$WINGS_RUNTIME_PATH")
                        .put("commandString", commandString)
                        .build())
        .build();
  }

  public static GraphNode getServiceRunningNode() {
    return GraphNode.builder()
        .id(graphIdGenerator("node"))
        .name("Service Running")
        .type(PROCESS_CHECK_RUNNING.name())
        .properties(ImmutableMap.<String, Object>builder()
                        .put("commandString", "echo \"service running check should be added here\"")
                        .build())
        .build();
  }
}
