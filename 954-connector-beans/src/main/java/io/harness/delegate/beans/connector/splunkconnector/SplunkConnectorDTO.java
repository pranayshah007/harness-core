/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.splunkconnector;

import static io.harness.eraro.ErrorCode.INVALID_REQUEST;
import static io.harness.exception.WingsException.USER;

import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import org.hibernate.validator.constraints.URL;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "SplunkConnector", description = "This contains the Splunk Connector configuration")
public class SplunkConnectorDTO extends ConnectorConfigDTO implements DecryptableEntity, DelegateSelectable {
  @URL @NotNull @NotBlank String splunkUrl;
  String username;
  @NotNull String accountId;
  Set<String> delegateSelectors;

  @Builder.Default @JsonProperty("type") SplunkAuthType authType = SplunkAuthType.USER_PASSWORD;

  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData passwordRef;

  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData tokenRef;

  public String getSplunkUrl() {
    if (splunkUrl.endsWith("/")) {
      return splunkUrl;
    }
    return splunkUrl + "/";
  }
  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }

  @Override
  public void validate() {
    SplunkAuthType authType = Optional.of(this.authType).orElse(SplunkAuthType.USER_PASSWORD);
    switch (authType) {
      case USER_PASSWORD:
        Preconditions.checkNotNull(this.username, "username cannot be empty");
        Preconditions.checkNotNull(this.passwordRef, "passwordRef cannot be empty");
        break;
      case BEARER_TOKEN:
        Preconditions.checkNotNull(this.tokenRef, "tokenRef cannot be empty");
        break;
      case ANONYMOUS:
        break;
      default:
        throw new InvalidRequestException("Invalid Credential type.", INVALID_REQUEST, USER);
    }
  }
}
