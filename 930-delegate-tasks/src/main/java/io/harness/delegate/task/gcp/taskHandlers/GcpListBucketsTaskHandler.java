/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.taskHandlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.delegate.task.gcp.request.GcpListBucketsRequest;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.delegate.task.gcp.response.GcpBucketDetails;
import io.harness.delegate.task.gcp.response.GcpListBucketsResponse;
import io.harness.delegate.task.gcp.response.GcpResponse;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Buckets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class GcpListBucketsTaskHandler implements TaskHandler {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private GcpHelperService gcpHelperService;
  @Inject private NGErrorHelper ngErrorHelper;

  @Override
  public GcpResponse executeRequest(GcpRequest request) {
    if (!(request instanceof GcpListBucketsRequest)) {
      throw new UnsupportedOperationException(format("Unsupported request type: %s, expected: %s",
          request.getClass().getSimpleName(), GcpListBucketsRequest.class.getSimpleName()));
    }

    try {
      GcpListBucketsRequest gcpRequest = (GcpListBucketsRequest) request;
      boolean useDelegate = gcpRequest.getGcpManualDetailsDTO() == null;
      decryptDTO(gcpRequest);
      char[] serviceAccountKeyFileContent = getGcpServiceAccountKeyFileContent(gcpRequest);
      if (isNotEmpty(serviceAccountKeyFileContent)) {
        SecretSanitizerThreadLocal.add(String.valueOf(serviceAccountKeyFileContent));
      }
      Storage storageService = gcpHelperService.getGcsStorageService(serviceAccountKeyFileContent, useDelegate);
      String projectId = gcpHelperService.getProjectId(serviceAccountKeyFileContent, useDelegate);
      Storage.Buckets buckets = storageService.buckets();

      return GcpListBucketsResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .buckets(listBuckets(buckets, projectId))
          .build();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      return GcpListBucketsResponse.builder()
          .errorMessage(ngErrorHelper.getErrorSummary(sanitizedException.getMessage()))
          .errorDetail(ngErrorHelper.createErrorDetail(sanitizedException.getMessage()))
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .build();
    }
  }

  private List<GcpBucketDetails> listBuckets(Storage.Buckets bucketsService, String projectId) throws Exception {
    List<GcpBucketDetails> allBuckets = new ArrayList<>();
    Storage.Buckets.List bucketsList = bucketsService.list(projectId);
    String nextPageToken = "";

    do {
      Buckets listOfBuckets = bucketsList.execute();
      if (listOfBuckets != null) {
        listOfBuckets.getItems()
            .stream()
            .map(bucket -> GcpBucketDetails.builder().id(bucket.getId()).name(bucket.getName()).build())
            .forEach(allBuckets::add);
        bucketsList.setPageToken(listOfBuckets.getNextPageToken());
        nextPageToken = listOfBuckets.getNextPageToken();
      }
    } while (isNotEmpty(nextPageToken));

    return allBuckets;
  }

  private void decryptDTO(GcpRequest gcpRequest) {
    if (gcpRequest.getGcpManualDetailsDTO() != null) {
      secretDecryptionService.decrypt(gcpRequest.getGcpManualDetailsDTO(), gcpRequest.getEncryptionDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          gcpRequest.getGcpManualDetailsDTO(), gcpRequest.getEncryptionDetails());
    }
  }

  private char[] getGcpServiceAccountKeyFileContent(GcpRequest request) {
    GcpManualDetailsDTO gcpManualDetailsDTO = request.getGcpManualDetailsDTO();
    if (gcpManualDetailsDTO != null) {
      return gcpManualDetailsDTO.getSecretKeyRef().getDecryptedValue();
    }

    return null;
  }
}
