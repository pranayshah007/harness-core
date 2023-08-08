/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.googlecloudstorage.service;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.googlecloudstorage.dtos.GoogleCloudStorageBucketDetails;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.gcp.GcpHelperService;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.task.gcp.request.GcpTaskParameters;
import io.harness.delegate.task.gcp.request.GcsListBucketsRequest;
import io.harness.delegate.task.gcp.response.GcsBucketListResponse;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public class GoogleCloudStorageArtifactResourceServiceImpl implements GoogleCloudStorageArtifactResourceService {
  @Inject private GcpHelperService gcpHelperService;

  @Override
  public List<GoogleCloudStorageBucketDetails> listGcsBuckets(
      IdentifierRef gcpConnectorRef, String accountId, String orgIdentifier, String projectIdentifier, String project) {
    GcpConnectorDTO connector = gcpHelperService.getConnector(gcpConnectorRef);
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(gcpConnectorRef.getAccountIdentifier())
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build();

    List<EncryptedDataDetail> encryptionDetails = gcpHelperService.getEncryptionDetails(connector, baseNGAccess);
    GcsListBucketsRequest request =
        GcsListBucketsRequest.builder()
            .gcpManualDetailsDTO(gcpHelperService.getManualDetailsDTO(connector))
            .useDelegate(INHERIT_FROM_DELEGATE == connector.getCredential().getGcpCredentialType())
            .delegateSelectors(connector.getDelegateSelectors())
            .encryptionDetails(encryptionDetails)
            .project(project)
            .build();
    List<GoogleCloudStorageBucketDetails> googleCloudStorageBucketDetails = new ArrayList<>();
    GcpTaskParameters gcpTaskParameters =
        GcpTaskParameters.builder().accountId(baseNGAccess.getAccountIdentifier()).gcpRequest(request).build();
    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(baseNGAccess);
    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                  .accountId(baseNGAccess.getAccountIdentifier())
                                                  .taskType(TaskType.GCS_BUCKETS_TASK_NG.name())
                                                  .taskParameters(gcpTaskParameters)
                                                  .executionTimeout(java.time.Duration.ofSeconds(30))
                                                  .taskSetupAbstractions(abstractions)
                                                  .taskSelectors(request.getDelegateSelectors())
                                                  .build();
    GcsBucketListResponse gcsBucketListResponse =
        gcpHelperService.executeSyncTaskV2(delegateTaskRequest, "list GCS buckets per project");
    gcsBucketListResponse.getBuckets().forEach(
        (key, value)
            -> googleCloudStorageBucketDetails.add(
                GoogleCloudStorageBucketDetails.builder().id(key).name(value).build()));
    return googleCloudStorageBucketDetails;
  }
}
