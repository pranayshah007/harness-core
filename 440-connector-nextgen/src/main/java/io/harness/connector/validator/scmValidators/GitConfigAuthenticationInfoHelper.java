/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator.scmValidators;

import static io.harness.connector.helper.GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity;
import static io.harness.connector.helper.GitApiAccessDecryptionHelper.hasApiAccess;
import static io.harness.delegate.beans.connector.scm.GitAuthType.SSH;

import static java.util.Collections.emptyList;

import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class GitConfigAuthenticationInfoHelper {
  @Inject EncryptionHelper encryptionHelper;
  @Inject SshKeySpecDTOHelper sshKeySpecDTOHelper;
  @Inject SecretCrudService secretCrudService;

  public List<EncryptedDataDetail> getEncryptedDataDetails(
      GitConfigDTO gitConfig, SSHKeySpecDTO sshKeySpecDTO, NGAccess ngAccess) {
    switch (gitConfig.getGitAuthType()) {
      case HTTP:
        return getEncryptionDetail(gitConfig.getGitAuth(), ngAccess);
      case SSH:
        return sshKeySpecDTOHelper.getSSHKeyEncryptionDetails(sshKeySpecDTO, ngAccess);
      default:
        throw new UnknownEnumTypeException("Git Authentication Type",
            gitConfig.getGitAuthType() == null ? null : gitConfig.getGitAuthType().getDisplayName());
    }
  }
  public List<EncryptedDataDetail> getApiAccessEncryptedDataDetail(ScmConnector scmConnector, NGAccess ngAccess) {
    if (hasApiAccess(scmConnector)) {
      return getEncryptionDetail(getAPIAccessDecryptableEntity(scmConnector), ngAccess);
    }
    return emptyList();
  }

  public List<EncryptedDataDetail> getGithubAppEncryptedDataDetail(ScmConnector scmConnector, NGAccess ngAccess) {
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) scmConnector;
    return getEncryptionDetail(
        ((GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials()).getHttpCredentialsSpec(),
        ngAccess);
  }

  private List<EncryptedDataDetail> getEncryptionDetail(DecryptableEntity decryptableEntity, NGAccess ngAccess) {
    return encryptionHelper.getEncryptionDetail(decryptableEntity, ngAccess.getAccountIdentifier(),
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
  }

  public SSHKeySpecDTO getSSHKey(
      GitConfigDTO gitConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (gitConfig.getGitAuthType() != SSH) {
      return null;
    }
    GitSSHAuthenticationDTO gitAuthenticationDTO = (GitSSHAuthenticationDTO) gitConfig.getGitAuth();
    SecretRefData sshKeyRef = gitAuthenticationDTO.getEncryptedSshKey();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        SecretRefHelper.getSecretConfigString(sshKeyRef), accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<SecretResponseWrapper> secretResponseWrapper = secretCrudService.get(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    if (!secretResponseWrapper.isPresent()) {
      throw new InvalidRequestException("No secret configured with identifier: " + sshKeyRef);
    }
    SecretDTOV2 secret = secretResponseWrapper.get().getSecret();
    return (SSHKeySpecDTO) secret.getSpec();
  }
}
