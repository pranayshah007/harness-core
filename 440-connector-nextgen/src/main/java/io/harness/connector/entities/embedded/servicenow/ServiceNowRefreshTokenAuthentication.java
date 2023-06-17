/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.servicenow;

import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthCredentialsDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowRefreshTokenDTO;
import io.harness.encryption.SecretRefHelper;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@FieldNameConstants(innerTypeName = "ServiceNowAuthenticationKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("ServiceNowRefreshTokenAuthentication")
public class ServiceNowRefreshTokenAuthentication implements ServiceNowAuthentication {
  String tokenUrl;
  String refreshTokenRef;
  String clientIdRef;
  String clientSecretRef;
  String scope;

  @Override
  public ServiceNowAuthCredentialsDTO toServiceNowAuthCredentialsDTO() {
    return ServiceNowRefreshTokenDTO.builder()
        .tokenUrl(this.getTokenUrl())
        .refreshTokenRef(SecretRefHelper.createSecretRef(this.getRefreshTokenRef()))
        .clientIdRef(SecretRefHelper.createSecretRef(this.getClientIdRef()))
        .clientSecretRef(SecretRefHelper.createSecretRef(this.getClientSecretRef()))
        .scope(this.getScope())
        .build();
  }

  public static ServiceNowAuthentication fromServiceNowAuthCredentialsDTO(
      ServiceNowAuthCredentialsDTO serviceNowAuthCredentialsDTO) {
    ServiceNowRefreshTokenDTO serviceNowRefreshTokenDTO = (ServiceNowRefreshTokenDTO) serviceNowAuthCredentialsDTO;
    return ServiceNowRefreshTokenAuthentication.builder()
        .tokenUrl(serviceNowRefreshTokenDTO.getTokenUrl())
        .refreshTokenRef(SecretRefHelper.getSecretConfigString(serviceNowRefreshTokenDTO.getRefreshTokenRef()))
        .clientIdRef(SecretRefHelper.getSecretConfigString(serviceNowRefreshTokenDTO.getClientIdRef()))
        .clientSecretRef(SecretRefHelper.getSecretConfigString(serviceNowRefreshTokenDTO.getClientSecretRef()))
        .scope(serviceNowRefreshTokenDTO.getScope())
        .build();
  }
}
