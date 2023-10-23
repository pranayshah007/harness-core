/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.servicenow;

import io.harness.delegate.beans.connector.servicenow.ServiceNowADFSDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthCredentialsDTO;
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
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.servicenow.ServiceNowADFSAuthentication")
public class ServiceNowADFSAuthentication implements ServiceNowAuthentication {
  String certificateRef;
  String privateKeyRef;
  String clientIdRef;
  String resourceIdRef;
  String adfsUrl;

  @Override
  public ServiceNowAuthCredentialsDTO toServiceNowAuthCredentialsDTO() {
    return ServiceNowADFSDTO.builder()
        .certificateRef(SecretRefHelper.createSecretRef(this.getCertificateRef()))
        .privateKeyRef(SecretRefHelper.createSecretRef(this.getPrivateKeyRef()))
        .clientIdRef(SecretRefHelper.createSecretRef(this.getClientIdRef()))
        .resourceIdRef(SecretRefHelper.createSecretRef(this.getResourceIdRef()))
        .adfsUrl(this.getAdfsUrl())
        .build();
  }

  public static ServiceNowAuthentication fromServiceNowAuthCredentialsDTO(
      ServiceNowAuthCredentialsDTO serviceNowAuthCredentialsDTO) {
    ServiceNowADFSDTO serviceNowADFSDTO = (ServiceNowADFSDTO) serviceNowAuthCredentialsDTO;
    return ServiceNowADFSAuthentication.builder()
        .certificateRef(SecretRefHelper.getSecretConfigString(serviceNowADFSDTO.getCertificateRef()))
        .privateKeyRef(SecretRefHelper.getSecretConfigString(serviceNowADFSDTO.getPrivateKeyRef()))
        .clientIdRef(SecretRefHelper.getSecretConfigString(serviceNowADFSDTO.getClientIdRef()))
        .resourceIdRef(SecretRefHelper.getSecretConfigString(serviceNowADFSDTO.getResourceIdRef()))
        .adfsUrl(serviceNowADFSDTO.getAdfsUrl())
        .build();
  }
}
