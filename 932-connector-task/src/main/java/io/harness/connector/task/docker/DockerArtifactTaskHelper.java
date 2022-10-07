/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.task.docker;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.context.MdcGlobalContextData;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
import io.harness.exception.runtime.DockerHubServerRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.manage.GlobalContextManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class DockerArtifactTaskHelper {
  private final DockerArtifactTaskHandler dockerArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(ArtifactTaskParameters artifactTaskParameters) {
    DockerArtifactDelegateRequest attributes = (DockerArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    String registryUrl = attributes.getDockerConnectorDTO().getDockerRegistryUrl();
    dockerArtifactTaskHandler.decryptRequestDTOs(attributes);
    ArtifactTaskResponse artifactTaskResponse;
    try {
      log.info("Validating Artifact Server");
      artifactTaskResponse = getSuccessTaskResponse(dockerArtifactTaskHandler.validateArtifactServer(attributes));
      log.info("Validated Artifact Server: " + registryUrl);
    } catch (DockerHubServerRuntimeException ex) {
      if (GlobalContextManager.get(MdcGlobalContextData.MDC_ID) == null) {
        MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(new HashMap<>()).build();
        GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
      }
      ((MdcGlobalContextData) GlobalContextManager.get(MdcGlobalContextData.MDC_ID))
          .getMap()
          .put(ExceptionMetadataKeys.CONNECTOR.name(), attributes.getConnectorRef());
      throw ex;
    }
    return artifactTaskResponse;
  }

  private ArtifactTaskResponse getSuccessTaskResponse(ArtifactTaskExecutionResponse taskExecutionResponse) {
    return ArtifactTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .artifactTaskExecutionResponse(taskExecutionResponse)
        .build();
  }
}
