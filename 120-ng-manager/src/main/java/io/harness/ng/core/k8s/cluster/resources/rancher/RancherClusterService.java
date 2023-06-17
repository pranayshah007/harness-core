/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.k8s.cluster.resources.rancher;

import static io.harness.ng.core.k8s.cluster.resources.rancher.RancherClusterHelper.throwExceptionIfTaskFailed;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.delegate.beans.connector.rancher.RancherListClustersTaskResponse;
import io.harness.delegate.beans.connector.rancher.RancherTaskParams;
import io.harness.delegate.beans.connector.rancher.RancherTaskType;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class RancherClusterService {
  @Inject private RancherClusterHelper clusterHelper;

  public RancherClusterListResponseDTO listClusters(String accountId, String orgId, String projectId,
      IdentifierRef connectorRef, Map<String, String> pageRequestParams) {
    RancherConnectorDTO rancherConnectorDTO = clusterHelper.getRancherConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        BaseNGAccess.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    List<EncryptedDataDetail> encryptionDetails = clusterHelper.getEncryptionDetails(rancherConnectorDTO, baseNGAccess);

    RancherTaskParams taskParams = RancherTaskParams.builder()
                                       .rancherConnectorDTO(rancherConnectorDTO)
                                       .encryptionDetails(encryptionDetails)
                                       .rancherTaskType(RancherTaskType.LIST_CLUSTERS)
                                       .pageRequestParams(pageRequestParams)
                                       .build();

    RancherListClustersTaskResponse taskResponse = executeRancherListClustersTask(taskParams, baseNGAccess);
    return RancherClusterListResponseDTO.builder().clusters(taskResponse.getClusters()).build();
  }

  private RancherListClustersTaskResponse executeRancherListClustersTask(
      RancherTaskParams taskParams, BaseNGAccess baseNGAccess) {
    DelegateResponseData delegateTaskResponse = clusterHelper.executeListClustersDelegateTask(taskParams, baseNGAccess);
    throwExceptionIfTaskFailed(delegateTaskResponse);
    return (RancherListClustersTaskResponse) delegateTaskResponse;
  }
}
