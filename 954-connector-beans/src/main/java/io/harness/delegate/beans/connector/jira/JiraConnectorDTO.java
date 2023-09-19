/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.jira;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
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

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("JiraConnector")
@Schema(name = "JiraConnector", description = "JIRA Connector details.")
public class JiraConnectorDTO extends ConnectorConfigDTO implements DecryptableEntity, DelegateSelectable {
  @URL @NotNull @NotBlank String jiraUrl;
  /** @deprecated */
  @Deprecated(since = "moved to JiraConnector with authType and jiraAuthentication") String username;
  /** @deprecated */
  @ApiModelProperty(dataType = "string")
  @SecretReference
  @Deprecated(since = "moved to JiraConnector with authType and jiraAuthentication")
  SecretRefData usernameRef;
  /** @deprecated */
  @ApiModelProperty(dataType = "string")
  @SecretReference
  @Deprecated(since = "moved to JiraConnector with authType and jiraAuthentication")
  SecretRefData passwordRef;
  Set<String> delegateSelectors;
  @Valid @NotNull JiraAuthenticationDTO auth;
  ConnectorType connectorType;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (!isNull(auth) && !isNull(auth.getCredentials())) {
      return Collections.singletonList(auth.getCredentials());
    }
    return Collections.singletonList(this);
  }

  @Override
  public void validate() {
    Preconditions.checkState(EmptyPredicate.isNotEmpty(jiraUrl), "Jira URL cannot be empty");
    if (!isNull(auth) && !isNull(auth.getCredentials())) {
      auth.getCredentials().validate();
      return;
    }
    if (EmptyPredicate.isEmpty(username) && (usernameRef == null || usernameRef.isNull())) {
      throw new InvalidRequestException("Username cannot be empty");
    }
    if (EmptyPredicate.isNotEmpty(username) && usernameRef != null && !usernameRef.isNull()) {
      throw new InvalidRequestException("Only one of username or usernameRef can be provided");
    }
  }
}
