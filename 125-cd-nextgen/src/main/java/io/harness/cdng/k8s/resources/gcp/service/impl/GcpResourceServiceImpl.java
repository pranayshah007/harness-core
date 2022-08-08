/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s.resources.gcp.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.gcp.GcpHelperService;
import io.harness.cdng.k8s.resources.gcp.GcpResponseDTO;
import io.harness.cdng.k8s.resources.gcp.service.GcpResourceService;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.task.gcp.GcpTaskType;
import io.harness.delegate.task.gcp.request.GcpListClustersRequest;
import io.harness.delegate.task.gcp.response.GcpClusterListTaskResponse;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(CDP)
public class GcpResourceServiceImpl implements GcpResourceService {
  @Inject GcpHelperService gcpHelperService;

  @Override
  public GcpResponseDTO getClusterNames(
      IdentifierRef gcpConnectorRef, String accountId, String orgIdentifier, String projectIdentifier) {
    GcpConnectorDTO connector = gcpHelperService.getConnector(gcpConnectorRef);
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(gcpConnectorRef.getAccountIdentifier())
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build();

    List<EncryptedDataDetail> encryptionDetails = gcpHelperService.getEncryptionDetails(connector, baseNGAccess);
    GcpListClustersRequest request =
        GcpListClustersRequest.builder()
            .gcpManualDetailsDTO(gcpHelperService.getManualDetailsDTO(connector))
            .useDelegate(INHERIT_FROM_DELEGATE == connector.getCredential().getGcpCredentialType())
            .delegateSelectors(connector.getDelegateSelectors())
            .encryptionDetails(encryptionDetails)
            .build();

    GcpClusterListTaskResponse gcpClusterListTaskResponse =
        gcpHelperService.executeSyncTask(baseNGAccess, request, GcpTaskType.LIST_CLUSTERS, "list GCP clusters");
    return GcpResponseDTO.builder().clusterNames(gcpClusterListTaskResponse.getClusterNames()).build();
  }
}
