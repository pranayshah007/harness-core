/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.adapter;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(DX)
public class BitbucketToGitMapper {
  public static GitConfigDTO mapToGitConfigDTO(BitbucketConnectorDTO bitbucketConnectorDTO) {
    final GitAuthType authType = bitbucketConnectorDTO.getAuthentication().getAuthType();
    final GitConnectionType connectionType = bitbucketConnectorDTO.getConnectionType();
    final String url = bitbucketConnectorDTO.getUrl();
    final String validationRepo = bitbucketConnectorDTO.getValidationRepo();
    if (authType == GitAuthType.HTTP) {
      final BitbucketHttpCredentialsSpecDTO httpCredentialsSpec =
          ((BitbucketHttpCredentialsDTO) bitbucketConnectorDTO.getAuthentication().getCredentials())
              .getHttpCredentialsSpec();
      final BitbucketUsernamePasswordDTO usernamePasswordDTO = (BitbucketUsernamePasswordDTO) httpCredentialsSpec;

      GitConfigDTO gitConfigForHttp = GitConfigCreater.getGitConfigForHttp(connectionType, url, validationRepo,
          usernamePasswordDTO.getUsername(), usernamePasswordDTO.getUsernameRef(), usernamePasswordDTO.getPasswordRef(),
          bitbucketConnectorDTO.getDelegateSelectors());
      gitConfigForHttp.setExecuteOnDelegate(bitbucketConnectorDTO.getExecuteOnDelegate());
      return gitConfigForHttp;
    } else if (authType == GitAuthType.SSH) {
      final BitbucketSshCredentialsDTO sshCredentials =
          (BitbucketSshCredentialsDTO) bitbucketConnectorDTO.getAuthentication().getCredentials();
      final SecretRefData sshKeyRef = sshCredentials.getSshKeyRef();

      GitConfigDTO gitConfigForSsh = GitConfigCreater.getGitConfigForSsh(
          connectionType, url, validationRepo, sshKeyRef, bitbucketConnectorDTO.getDelegateSelectors());
      gitConfigForSsh.setExecuteOnDelegate(bitbucketConnectorDTO.getExecuteOnDelegate());
      return gitConfigForSsh;
    }
    throw new InvalidRequestException("Unknown auth type: " + authType);
  }
}
