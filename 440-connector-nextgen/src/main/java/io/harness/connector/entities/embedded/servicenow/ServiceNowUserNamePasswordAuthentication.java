/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.servicenow;

import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthCredentialsDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
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
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.servicenow.ServiceNowUserNamePasswordAuthentication")
public class ServiceNowUserNamePasswordAuthentication implements ServiceNowAuthentication {
  String username;
  String usernameRef;
  String passwordRef;

  @Override
  public ServiceNowAuthCredentialsDTO toServiceNowAuthCredentialsDTO() {
    return ServiceNowUserNamePasswordDTO.builder()
        .username(this.getUsername())
        .usernameRef(SecretRefHelper.createSecretRef(this.getUsernameRef()))
        .passwordRef(SecretRefHelper.createSecretRef(this.getPasswordRef()))
        .build();
  }

  public static ServiceNowAuthentication fromServiceNowAuthCredentialsDTO(
      ServiceNowAuthCredentialsDTO serviceNowAuthCredentialsDTO) {
    ServiceNowUserNamePasswordDTO serviceNowUserNamePasswordDTO =
        (ServiceNowUserNamePasswordDTO) serviceNowAuthCredentialsDTO;
    return ServiceNowUserNamePasswordAuthentication.builder()
        .username(serviceNowUserNamePasswordDTO.getUsername())
        .usernameRef(SecretRefHelper.getSecretConfigString(serviceNowUserNamePasswordDTO.getUsernameRef()))
        .passwordRef(SecretRefHelper.getSecretConfigString(serviceNowUserNamePasswordDTO.getPasswordRef()))
        .build();
  }
}
