/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;
import io.harness.exception.InvalidRequestException;
import io.harness.validation.OneOfField;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
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

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("ServiceNowConnector")
@OneOfField(fields = {"username", "usernameRef"}) // TODO: to be removed while migration
@Schema(name = "ServiceNowConnector", description = "ServiceNow Connector details.")
public class ServiceNowConnectorDTO extends ConnectorConfigDTO implements DecryptableEntity, DelegateSelectable {
  @URL @NotNull @NotBlank String serviceNowUrl;
  String username;
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData usernameRef;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData passwordRef;
  Set<String> delegateSelectors;
  @Valid ServiceNowAuthenticationDTO auth;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (!isNull(auth) && !isNull(auth.getCredentials())) {
      return Collections.singletonList(auth.getCredentials());
    }
    return Collections.singletonList(this);
  }

  @Override
  public void validate() {
    if (!isNull(auth) && !isNull(auth.getCredentials())) {
      auth.getCredentials().validate();
      return;
    }
    if (isBlank(username) && (usernameRef == null || usernameRef.isNull())) {
      throw new InvalidRequestException("Username cannot be empty");
    }
    if (EmptyPredicate.isNotEmpty(username) && usernameRef != null && !usernameRef.isNull()) {
      throw new InvalidRequestException("Only one of username or usernameRef can be provided");
    }
  }
}
