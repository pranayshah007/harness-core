/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureartifacts.outcome;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.ConnectorType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CDC)
public class AzureArtifactsConnectorOutcomeDTO
    extends ConnectorConfigOutcomeDTO implements DelegateSelectable, ManagerExecutable {
  @URL @NotBlank @NotNull String azureArtifactsUrl;
  @Valid @NotNull AzureArtifactsAuthenticationOutcomeDTO auth;
  Set<String> delegateSelectors;

  Boolean executeOnDelegate = true;

  @Builder
  public AzureArtifactsConnectorOutcomeDTO(String azureArtifactsUrl, AzureArtifactsAuthenticationOutcomeDTO auth,
      Set<String> delegateSelectors, Boolean executeOnDelegate) {
    this.azureArtifactsUrl = azureArtifactsUrl;
    this.auth = auth;
    this.delegateSelectors = delegateSelectors;
    this.executeOnDelegate = executeOnDelegate;
  }

  public String getAzureArtifactsUrl() {
    return azureArtifactsUrl;
  }

  @JsonIgnore
  public ConnectorType getConnectorType() {
    return ConnectorType.AZURE_ARTIFACTS;
  }
}
