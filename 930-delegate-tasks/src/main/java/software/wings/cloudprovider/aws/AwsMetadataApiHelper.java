/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.cloudprovider.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogLevel;
import io.harness.network.Http;
import io.harness.serializer.JsonUtils;

import software.wings.beans.command.ExecutionLogCallback;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.Task;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AwsMetadataApiHelper {
  private static final String CONTAINER_METADATA_FORMAT_STRING = "http://%s:51678/v1/tasks";

  public String getDockerIdUsingEc2MetadataEndpointApi(Instance ec2Instance, Task taskRunningContainer,
      String containerName, ExecutionLogCallback executionLogCallback) throws IOException {
    String uri = generateTaskMetadataEndpointUrl(ec2Instance, executionLogCallback);
    if (isNotEmpty(uri)) {
      if (executionLogCallback != null) {
        printToExecutionLog(executionLogCallback, "Fetching container meta data from " + uri, INFO);
      }

      TaskMetadata taskMetadata = JsonUtils.asObject(getResponseStringFromUrl(uri), TaskMetadata.class);
      Optional<TaskMetadata.Task> optionalTask =
          taskMetadata.getTasks()
              .stream()
              .filter(task -> taskRunningContainer.getTaskArn().equals(task.getArn()))
              .findFirst();

      if (optionalTask.isPresent()) {
        TaskMetadata.Task task = optionalTask.get();
        List<TaskMetadata.Container> containers = task.getContainers();
        if (isNotEmpty(containers)) {
          TaskMetadata.Container mainContainer = containers.stream()
                                                     .filter(container -> containerName.equals(container.getName()))
                                                     .findFirst()
                                                     .orElse(null);
          if (mainContainer != null) {
            return mainContainer.getDockerId();
          }
        }
      }
    }

    return EMPTY;
  }

  public String getResponseStringFromUrl(String uri) throws IOException {
    return Http.getResponseStringFromUrl(uri, 30, 30);
  }

  // https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-agent-introspection.html
  public String generateTaskMetadataEndpointUrl(Instance ec2Instance, ExecutionLogCallback executionLogCallback) {
    String url = format(CONTAINER_METADATA_FORMAT_STRING, ec2Instance.getPrivateDnsName());
    printToExecutionLog(executionLogCallback, format("Testing private Dns: [%s] for connectivity", url), INFO);
    if (checkConnectivity(url)) {
      printToExecutionLog(
          executionLogCallback, format("[%s] is reachable. Using it for container meta data", url), INFO);
      return url;
    }

    url = format(CONTAINER_METADATA_FORMAT_STRING, ec2Instance.getPrivateIpAddress());
    printToExecutionLog(executionLogCallback, format("Testing private ip: [%s] for connectivity", url), INFO);
    if (checkConnectivity(url)) {
      printToExecutionLog(
          executionLogCallback, format("[%s] is reachable. Using it for container meta data", url), INFO);
      return url;
    }

    printToExecutionLog(executionLogCallback, "Could not reach URL: " + url, WARN);
    return EMPTY;
  }

  @VisibleForTesting
  boolean checkConnectivity(String url) {
    return Http.connectableHttpUrl(url);
  }

  public String tryMetadataApiForDockerIdIfAccessble(Instance ec2Instance, Task taskRunningContainer,
      String containerName, ExecutionLogCallback executionLogCallback) {
    String dockerId;
    printToExecutionLog(executionLogCallback,
        "Aws DescribeTask API did not return dockerId(RuntimeId) for container. Please update ECS agent version.",
        WARN);

    printToExecutionLog(executionLogCallback,
        "Trying to fetch dockerId using metadata API. This will work only if delegate is running in same VPC as deployed task and port 51678 is open for access",
        WARN);
    try {
      dockerId = getDockerIdUsingEc2MetadataEndpointApi(
          ec2Instance, taskRunningContainer, containerName, executionLogCallback);
    } catch (Exception e) {
      log.error("Exception Occured in fetching dockerId using Metadata Api", e);
      printToExecutionLog(executionLogCallback, e.getMessage(), ERROR);
      dockerId = EMPTY;
    }

    if (isEmpty(dockerId)) {
      printToExecutionLog(executionLogCallback,
          "Failed to get dockerId using Metadata Endpoint as well. Recommended action is to update ECS Agent version",
          ERROR);
    }
    return dockerId;
  }

  private void printToExecutionLog(ExecutionLogCallback executionLogCallback, String message, LogLevel logLevel) {
    if (executionLogCallback == null) {
      return;
    }

    if (logLevel == null) {
      logLevel = INFO;
    }

    executionLogCallback.saveExecutionLog(message, logLevel);
  }
}
