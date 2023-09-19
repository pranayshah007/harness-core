/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.artifactoryconnector;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.outcome.ArtifactoryAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.outcome.ArtifactoryConnectorOutcomeDTO;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("ArtifactoryConnector")
@Schema(name = "ArtifactoryConnector", description = "This entity contains the details of the Artifactory Connectors")
@RecasterAlias("io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO")
public class ArtifactoryConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable, ManagerExecutable {
  @URL @NotNull @NotBlank String artifactoryServerUrl;
  @Valid ArtifactoryAuthenticationDTO auth;
  Set<String> delegateSelectors;
  @Builder.Default Boolean executeOnDelegate = true;
  ConnectorType connectorType;


  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (auth == null) {
      throw new InvalidRequestException("Auth Field is Null");
    }
    if (auth.getAuthType() == ArtifactoryAuthType.ANONYMOUS) {
      return null;
    }
    return Collections.singletonList(auth.getCredentials());
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return ArtifactoryConnectorOutcomeDTO.builder()
        .auth(ArtifactoryAuthenticationOutcomeDTO.builder()
                  .spec(this.auth.getCredentials())
                  .type(this.auth.getAuthType())
                  .build())
        .artifactoryServerUrl(this.artifactoryServerUrl)
        .delegateSelectors(this.delegateSelectors)
        .executeOnDelegate(this.executeOnDelegate)
        .build();
  }
}
