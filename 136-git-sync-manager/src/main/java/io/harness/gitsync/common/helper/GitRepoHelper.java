/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.product.ci.scm.proto.Repository;

import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class GitRepoHelper {
  public String getRepoUrl(ScmConnector scmConnector, String repoName) {
    String gitConnectionUrl = scmConnector.getGitConnectionUrl(GitRepositoryDTO.builder().name(repoName).build());
    switch (scmConnector.getConnectorType()) {
      case GITHUB:
        return GitClientHelper.getCompleteHTTPUrlForGithub(gitConnectionUrl);
      case BITBUCKET:
        if (GitClientHelper.isBitBucketSAAS(gitConnectionUrl)) {
          return GitClientHelper.getCompleteHTTPUrlForBitbucketSaas(gitConnectionUrl);
        }
        BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) scmConnector;
        if (GitAuthType.SSH.equals(bitbucketConnectorDTO.getAuthentication().getAuthType())) {
          return GitClientHelper.getCompleteHTTPUrlFromSSHUrlForBitbucketServer(gitConnectionUrl);
        } else {
          return gitConnectionUrl;
        }
      case AZURE_REPO:
        return GitClientHelper.getCompleteHTTPRepoUrlForAzureRepoSaas(gitConnectionUrl);
      case GITLAB:
        return GitClientHelper.getCompleteHTTPUrlForGitLab(gitConnectionUrl);
      default:
        throw new InvalidRequestException(
            format("Connector of given type : %s isn't supported", scmConnector.getConnectorType()));
    }
  }

  public String getRepoNameWithNamespace(ScmConnector scmConnector, String repo) {
    String url = getRepoUrl(scmConnector, repo);
    List<String> segments = List.of(url.split("/"));
    String namespace = segments.get(segments.size() - 2);
    return namespace + "/" + repo;
  }

  public String getCompleteRepoName(Repository repo) {
    return repo.getNamespace() + "/" + repo.getName();
  }
}
