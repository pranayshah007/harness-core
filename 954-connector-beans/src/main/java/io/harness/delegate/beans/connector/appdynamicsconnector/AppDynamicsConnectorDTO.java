/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.appdynamicsconnector;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CV)
public class AppDynamicsConnectorDTO extends ConnectorConfigDTO implements DecryptableEntity, DelegateSelectable {
  String username;
  @NotNull String accountname;
  @NotNull @NotBlank String controllerUrl;
  Set<String> delegateSelectors;

  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData passwordRef;
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData clientSecretRef;
  private String clientId;

  @Builder.Default private AppDynamicsAuthType authType = AppDynamicsAuthType.USERNAME_PASSWORD;

  public String getControllerUrl() {
    if (controllerUrl.endsWith("/")) {
      return controllerUrl;
    }
    return controllerUrl + "/";
  }

  public AppDynamicsAuthType getAuthType() {
    if (authType == null) {
      return AppDynamicsAuthType.USERNAME_PASSWORD;
    }
    return authType;
  }

  @Override
  public void validate() {
    Preconditions.checkState(isNotEmpty(controllerUrl), "Controller URL cannot be empty");
    // Until the framework handles NPEs well, we will throw InvalidRequestException
    if (getAuthType().equals(AppDynamicsAuthType.USERNAME_PASSWORD) && (isEmpty(username) || passwordRef.isNull())) {
      throw new InvalidRequestException("Username and Password cannot be empty for UsernamePassword Auth type");
    } else if (getAuthType().equals(AppDynamicsAuthType.API_CLIENT_TOKEN)
        && (isEmpty(clientId) || clientSecretRef.isNull())) {
      throw new InvalidRequestException("Client ID or Client Secret cannot be empty for ApiClientToken Auth type");
    }
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }
}
