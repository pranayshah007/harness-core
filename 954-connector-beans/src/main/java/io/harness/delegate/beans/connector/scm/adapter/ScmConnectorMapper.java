/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.adapter;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(DX)
public class ScmConnectorMapper {
  public static GitConfigDTO toGitConfigDTO(ScmConnector scmConnector) {
    // this should be used on manager side only as some values in GitAuth might be dummy
    // On delegate side ScmConnectorMapperDelegate should be used
    if (scmConnector instanceof GithubConnectorDTO) {
      return GithubToGitMapper.mapToGitConfigDTO((GithubConnectorDTO) scmConnector);
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      return GitlabToGitMapper.mapToGitConfigDTO((GitlabConnectorDTO) scmConnector);
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      return BitbucketToGitMapper.mapToGitConfigDTO((BitbucketConnectorDTO) scmConnector);
    } else if (scmConnector instanceof AzureRepoConnectorDTO) {
      return AzureRepoToGitMapper.mapToGitConfigDTO((AzureRepoConnectorDTO) scmConnector);
    } else {
      return (GitConfigDTO) scmConnector;
    }
  }
}
