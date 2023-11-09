/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.connector.gcpconnector.GcpOidcDetailsDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.oidc.gcp.GcpOidcTokenRequestDTO;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import retrofit2.Response;

@Slf4j
@Component
public class GcpOidcAuthenticator {
  private final ConnectorResourceClient connectorResourceClient;

  @Inject
  public GcpOidcAuthenticator(ConnectorResourceClient connectorResourceClient) {
    this.connectorResourceClient = connectorResourceClient;
  }

  public Map<EnvVariableEnum, String> handleOidcAuthentication(String accountId, GcpOidcDetailsDTO gcpOidcDetailsDTO)
      throws IOException {
    if (accountId == null || gcpOidcDetailsDTO == null) {
      throw new IllegalArgumentException("Invalid input to handleOidcAuthentication");
    }

    GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO = createGcpOidcTokenRequestDTO(accountId, gcpOidcDetailsDTO);
    String oidcIdTkn = fetchOidcIdToken(gcpOidcTokenRequestDTO);
    return populateEnvVariablesMap(gcpOidcDetailsDTO, oidcIdTkn);
  }

  private GcpOidcTokenRequestDTO createGcpOidcTokenRequestDTO(String accountId, GcpOidcDetailsDTO gcpOidcDetailsDTO) {
    GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO = new GcpOidcTokenRequestDTO();
    gcpOidcTokenRequestDTO.setAccountId(accountId);
    gcpOidcTokenRequestDTO.setWorkloadPoolId(gcpOidcDetailsDTO.getWorkloadPoolId());
    gcpOidcTokenRequestDTO.setProviderId(gcpOidcDetailsDTO.getProviderId());
    gcpOidcTokenRequestDTO.setServiceAccountEmail(gcpOidcDetailsDTO.getServiceAccountEmail());
    gcpOidcTokenRequestDTO.setGcpProjectId(gcpOidcDetailsDTO.getGcpProjectId());

    validateGcpOidcTokenRequestDTO(gcpOidcTokenRequestDTO);
    return gcpOidcTokenRequestDTO;
  }

  private String fetchOidcIdToken(GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) throws IOException {
    Response<ResponseDTO<String>> oidcIdToken =
        connectorResourceClient.getOidcIdToken(gcpOidcTokenRequestDTO).execute();
    if (oidcIdToken.isSuccessful()) {
      return oidcIdToken.body().getData();
    } else {
      throw new IOException("Failed to create OIDC ID Token");
    }
  }

  private Map<EnvVariableEnum, String> populateEnvVariablesMap(GcpOidcDetailsDTO gcpOidcDetailsDTO, String oidcIdTkn) {
    Map<EnvVariableEnum, String> envVariablesMap = new HashMap<>();
    envVariablesMap.put(EnvVariableEnum.PLUGIN_PROJECT_NUMBER, gcpOidcDetailsDTO.getGcpProjectId());
    envVariablesMap.put(EnvVariableEnum.PLUGIN_PROVIDER_ID, gcpOidcDetailsDTO.getProviderId());
    envVariablesMap.put(EnvVariableEnum.PLUGIN_POOL_ID, gcpOidcDetailsDTO.getWorkloadPoolId());
    envVariablesMap.put(EnvVariableEnum.PLUGIN_SERVICE_ACCOUNT_EMAIL, gcpOidcDetailsDTO.getServiceAccountEmail());
    envVariablesMap.put(EnvVariableEnum.PLUGIN_OIDC_TOKEN_ID, oidcIdTkn);
    return envVariablesMap;
  }

  private void validateGcpOidcTokenRequestDTO(GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    if (gcpOidcTokenRequestDTO.getAccountId() == null || gcpOidcTokenRequestDTO.getWorkloadPoolId() == null
        || gcpOidcTokenRequestDTO.getProviderId() == null || gcpOidcTokenRequestDTO.getServiceAccountEmail() == null
        || gcpOidcTokenRequestDTO.getGcpProjectId() == null) {
      throw new IllegalArgumentException("All properties must be set on GcpOidcTokenRequestDTO");
    }
  }
}