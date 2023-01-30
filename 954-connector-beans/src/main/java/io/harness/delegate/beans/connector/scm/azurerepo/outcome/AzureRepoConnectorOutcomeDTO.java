/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.azurerepo.outcome;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
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

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.PL)
public class AzureRepoConnectorOutcomeDTO
    extends ConnectorConfigOutcomeDTO implements DelegateSelectable, ManagerExecutable {
  @NotNull AzureRepoConnectionTypeDTO type;
  @NotBlank @NotNull String url;
  String validationRepo;
  @Valid @NotNull AzureRepoAuthenticationOutcomeDTO authentication;
  @Valid AzureRepoApiAccessDTO apiAccess;
  Set<String> delegateSelectors;
  Boolean executeOnDelegate = true;
  String gitConnectionUrl;
  private static final String AZURE_REPO_NAME_SEPARATOR = "_git/";

  @Builder
  public AzureRepoConnectorOutcomeDTO(AzureRepoConnectionTypeDTO type, String url, String validationRepo,
      AzureRepoAuthenticationOutcomeDTO authentication, AzureRepoApiAccessDTO apiAccess, Set<String> delegateSelectors,
      Boolean executeOnDelegate) {
    this.type = type;
    this.url = url;
    this.validationRepo = validationRepo;
    this.authentication = authentication;
    this.apiAccess = apiAccess;
    this.delegateSelectors = delegateSelectors;
    this.executeOnDelegate = executeOnDelegate;
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    List<DecryptableEntity> decryptableEntities = new ArrayList<>();
    if (authentication.getType() == GitAuthType.HTTP) {
      AzureRepoHttpCredentialsSpecDTO httpCredentialsSpec =
          ((AzureRepoHttpCredentialsDTO) authentication.getSpec()).getHttpCredentialsSpec();
      if (httpCredentialsSpec != null) {
        decryptableEntities.add(httpCredentialsSpec);
      }
    } else {
      AzureRepoSshCredentialsDTO sshCredential = (AzureRepoSshCredentialsDTO) authentication.getSpec();
      if (sshCredential != null) {
        decryptableEntities.add(sshCredential);
      }
    }
    if (apiAccess != null && apiAccess.getSpec() != null) {
      decryptableEntities.add(apiAccess.getSpec());
    }
    return decryptableEntities;
  }
}