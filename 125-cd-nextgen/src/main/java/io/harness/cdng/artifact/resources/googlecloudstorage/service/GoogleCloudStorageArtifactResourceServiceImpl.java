/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.googlecloudstorage.service;

import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.googlecloudstorage.dtos.GoogleCloudStorageBucketDetails;
import io.harness.cdng.gcp.GcpHelperService;
import io.harness.cdng.k8s.resources.gcp.dtos.GcpProjectDetails;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.task.gcp.GcpTaskType;
import io.harness.delegate.task.gcp.request.GcsListBucketsRequest;
import io.harness.delegate.task.gcp.response.GcpProjectListTaskResponse;
import io.harness.delegate.task.gcp.response.GcsBucketListResponse;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

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
    GcsBucketListResponse gcsBucketListResponse = gcpHelperService.executeSyncTask(
        baseNGAccess, request, GcpTaskType.LIST_GCS_BUCKETS_PER_PROJECT, "list GCS buckets per project");
    gcsBucketListResponse.getBuckets().forEach(
        (key, value)
            -> googleCloudStorageBucketDetails.add(
                GoogleCloudStorageBucketDetails.builder().id(key).name(value).build()));
    return googleCloudStorageBucketDetails;
  }
}
