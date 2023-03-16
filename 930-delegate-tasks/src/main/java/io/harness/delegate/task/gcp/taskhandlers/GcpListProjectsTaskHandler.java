/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.taskhandlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.delegate.task.gcp.request.GcpListProjectsRequest;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.delegate.task.gcp.response.GcpProjectListTaskResponse;
import io.harness.delegate.task.gcp.response.GcpResponse;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.model.ListProjectsResponse;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
public class GcpListProjectsTaskHandler implements TaskHandler {
  @Inject private NGErrorHelper ngErrorHelper;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private GcpHelperService gcpHelperService;

  @Override
  public GcpResponse executeRequest(GcpRequest gcpRequest) {
    if (!(gcpRequest instanceof GcpListProjectsRequest)) {
      throw new UnsupportedOperationException(format("Unsupported request type: %s, expected: %s",
          gcpRequest.getClass().getSimpleName(), GcpListProjectsRequest.class.getSimpleName()));
    }
    try {
      decryptDTO(gcpRequest);
      return getProjectNames((GcpListProjectsRequest) gcpRequest);
    } catch (Exception exception) {
      log.error("Failed retrieving GCP cluster list.", exception);
      return failureResponse(exception);
    }
  }

  private GcpProjectListTaskResponse getProjectNames(GcpListProjectsRequest gcpListProjectsRequest) throws IOException {
    boolean useDelegate = gcpListProjectsRequest.getGcpManualDetailsDTO() == null
        && isNotEmpty(gcpListProjectsRequest.getDelegateSelectors());

    char[] serviceAccountKeyFileContent = getGcpServiceAccountKeyFileContent(gcpListProjectsRequest);
    if (isNotEmpty(serviceAccountKeyFileContent)) {
      SecretSanitizerThreadLocal.add(String.valueOf(serviceAccountKeyFileContent));
    }
    CloudResourceManager cloudResourceManager =
        gcpHelperService.getCloudResourceManager(serviceAccountKeyFileContent, useDelegate);
    CloudResourceManager.Projects projectService = cloudResourceManager.projects();
    return GcpProjectListTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .projects(listProjects(projectService))
        .build();
  }

  private Map<String, String> listProjects(CloudResourceManager.Projects projectService) throws IOException {
    String nextPageToken = "";
    CloudResourceManager.Projects.List projectsList = projectService.list();
    Map<String, String> projectsMap = new HashMap<>();
    do {
      ListProjectsResponse listProjectsResponse = projectsList.execute();
      if (listProjectsResponse != null && listProjectsResponse.getProjects() != null) {
        listProjectsResponse.getProjects().forEach(
            project -> projectsMap.put(project.getProjectId(), project.getName()));
        nextPageToken = listProjectsResponse.getNextPageToken();
      }
    } while (isNotEmpty(nextPageToken));
    return projectsMap;
  }

  private void decryptDTO(GcpRequest gcpRequest) {
    if (gcpRequest.getGcpManualDetailsDTO() != null) {
      secretDecryptionService.decrypt(gcpRequest.getGcpManualDetailsDTO(), gcpRequest.getEncryptionDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          gcpRequest.getGcpManualDetailsDTO(), gcpRequest.getEncryptionDetails());
    }
  }

  private GcpProjectListTaskResponse failureResponse(Exception ex) {
    return GcpProjectListTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .errorMessage(ngErrorHelper.getErrorSummary(ex.getMessage()))
        .errorDetail(ngErrorHelper.createErrorDetail(ex.getMessage()))
        .build();
  }

  private char[] getGcpServiceAccountKeyFileContent(GcpRequest request) {
    GcpManualDetailsDTO gcpManualDetailsDTO = request.getGcpManualDetailsDTO();
    if (gcpManualDetailsDTO != null) {
      return gcpManualDetailsDTO.getSecretKeyRef().getDecryptedValue();
    }

    return null;
  }
}
