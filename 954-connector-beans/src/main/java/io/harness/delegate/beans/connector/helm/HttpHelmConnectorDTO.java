/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.helm;

import static io.harness.delegate.beans.connector.helm.HttpHelmAuthType.ANONYMOUS;

import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.helm.outcome.HttpHelmAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.helm.outcome.HttpHelmConnectorOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "HttpHelmConnector", description = "This contains http helm connector details")
public class HttpHelmConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable {
  @URL @NotNull @NotBlank String helmRepoUrl;
  @Valid HttpHelmAuthenticationDTO auth;
  Set<String> delegateSelectors;
  @Builder.Default private ConnectorType connectorType = ConnectorType.HTTP_HELM_REPO;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (auth.getAuthType() == ANONYMOUS) {
      return Collections.emptyList();
    }
    return Collections.singletonList(auth.getCredentials());
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return HttpHelmConnectorOutcomeDTO.builder()
        .helmRepoUrl(this.helmRepoUrl)
        .delegateSelectors(this.delegateSelectors)
        .auth(HttpHelmAuthenticationOutcomeDTO.builder()
                  .spec(this.auth.getCredentials())
                  .type(this.auth.getAuthType())
                  .build())
        .build();
  }
}
