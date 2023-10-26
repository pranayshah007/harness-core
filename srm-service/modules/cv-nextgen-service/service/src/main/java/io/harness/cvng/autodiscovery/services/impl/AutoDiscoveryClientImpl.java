/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.autodiscovery.services.impl;

import io.harness.cvng.autodiscovery.services.AutoDiscoveryClient;
import io.harness.cvng.client.RequestExecutor;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.servicediscovery.client.beans.DiscoveredServiceConnectionResponse;
import io.harness.servicediscovery.client.beans.DiscoveredServiceResponse;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryResponseDTO;
import io.harness.servicediscovery.client.remote.ServiceDiscoveryHttpClient;

import com.google.inject.Inject;
import java.util.List;
import retrofit2.Call;

public class AutoDiscoveryClientImpl implements AutoDiscoveryClient {
  @Inject ServiceDiscoveryHttpClient serviceDiscoveryClient;
  @Inject private RequestExecutor requestExecutor;

  @Override
  public List<DiscoveredServiceResponse> getDiscoveredServices(ProjectParams projectParams, String agentIdentifier) {
    Call<ServiceDiscoveryResponseDTO<DiscoveredServiceResponse>> discoveredServiceResponse =
        serviceDiscoveryClient.getDiscoveredServices(agentIdentifier, projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), true);
    return requestExecutor.execute(discoveredServiceResponse).getItems();
  }

  @Override
  public List<DiscoveredServiceConnectionResponse> getDiscoveredServiceConnections(
      ProjectParams projectParams, String agentIdentifier) {
    Call<ServiceDiscoveryResponseDTO<DiscoveredServiceConnectionResponse>> discoveredServiceConnectionResponse =
        serviceDiscoveryClient.getDiscoveredServiceConnection(agentIdentifier, projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), true);
    return requestExecutor.execute(discoveredServiceConnectionResponse).getItems();
  }
}
