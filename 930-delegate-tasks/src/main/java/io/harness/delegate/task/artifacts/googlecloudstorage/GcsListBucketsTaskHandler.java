/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googlecloudstorage;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.delegate.task.gcp.request.GcsListBucketsRequest;
import io.harness.delegate.task.gcp.response.GcpProjectListTaskResponse;
import io.harness.delegate.task.gcp.response.GcpResponse;
import io.harness.delegate.task.gcp.response.GcsBucketListResponse;
import io.harness.delegate.task.gcp.taskhandlers.TaskHandler;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.googlecloudstorage.GcsHelperService;
import io.harness.googlecloudstorage.GcsInternalConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Slf4j
@OwnedBy(CDP)
public class GcsListBucketsTaskHandler implements TaskHandler {
  @Inject private NGErrorHelper ngErrorHelper;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private GcsHelperService gcsHelperService;
  @Override
  public GcpResponse executeRequest(GcpRequest gcpRequest) {
    if (!(gcpRequest instanceof GcsListBucketsRequest)) {
      throw new UnsupportedOperationException(format("Unsupported request type: %s, expected: %s",
          gcpRequest.getClass().getSimpleName(), GcsListBucketsRequest.class.getSimpleName()));
    }
    try {
      decryptDTO(gcpRequest);
      return getBucketNames((GcsListBucketsRequest) gcpRequest);
    } catch (Exception exception) {
      log.error("Failed retrieving GCP cluster list.", exception);
      return failureResponse(exception);
    }
  }

  private GcsBucketListResponse getBucketNames(GcsListBucketsRequest gcsListBucketsRequest) throws IOException {
    boolean useDelegate = gcsListBucketsRequest.getGcpManualDetailsDTO() == null
        && isNotEmpty(gcsListBucketsRequest.getDelegateSelectors());

    char[] serviceAccountKeyFileContent = getGcpServiceAccountKeyFileContent(gcsListBucketsRequest);
    if (isNotEmpty(serviceAccountKeyFileContent)) {
      SecretSanitizerThreadLocal.add(String.valueOf(serviceAccountKeyFileContent));
    }
    GcsInternalConfig gcsInternalConfig = GcsInternalConfig.builder()
                                              .serviceAccountKeyFileContent(serviceAccountKeyFileContent)
                                              .isUseDelegate(useDelegate)
                                              .project(gcsListBucketsRequest.getProject())
                                              .build();
    return GcsBucketListResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .buckets(gcsHelperService.listBuckets(gcsInternalConfig))
        .build();
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
