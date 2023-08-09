/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.elkconnector;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.secret.SecretReference;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CV)
public class ELKConnectorDTO extends ConnectorConfigDTO implements DecryptableEntity, DelegateSelectable {
  @NotNull String url;
  Set<String> delegateSelectors;

  private String username;
  private String apiKeyId;

  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData passwordRef;
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData apiKeyRef;

  private ELKAuthType authType;

  public String getUrl() {
    if (url.endsWith("/")) {
      return url;
    }
    return url + "/";
  }

  public ELKAuthType getAuthType() {
    if (authType == null) {
      return ELKAuthType.USERNAME_PASSWORD;
    }
    return authType;
  }

  @Override
  public void validate() {
    Preconditions.checkState(isNotEmpty(url), "URL cannot be empty");
    // Until the framework handles NPEs well, we will throw InvalidRequestException
    if (getAuthType().equals(ELKAuthType.USERNAME_PASSWORD) && (isEmpty(username) || passwordRef.isNull())) {
      throw new InvalidRequestException("Username and Password cannot be empty for UsernamePassword Auth type");
    } else if (getAuthType().equals(ELKAuthType.API_CLIENT_TOKEN) && (isEmpty(apiKeyId) || apiKeyRef.isNull())) {
      throw new InvalidRequestException("Client ID or Client Secret cannot be empty for ApiClientToken Auth type");
    } else if (getAuthType().equals(ELKAuthType.BEARER_TOKEN) && (apiKeyRef.isNull())) {
      throw new InvalidRequestException("API Key Ref cannot be empty for BearerToken Auth type");
    }
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }
}
