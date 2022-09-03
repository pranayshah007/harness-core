/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.jenkins;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.connector.jenkins.JenkinsAuthType.ANONYMOUS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.data.validator.UrlField;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

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

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "JenkinsConnector", description = "Jenkins Connector details.")
public class JenkinsConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable {
  @UrlField @NotNull @NotBlank String jenkinsUrl;
  @Valid JenkinsAuthenticationDTO auth;
  Set<String> delegateSelectors;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (auth.getAuthType() == ANONYMOUS) {
      return null;
    }
    return Collections.singletonList(auth.getCredentials());
  }
}
