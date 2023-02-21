/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.git.helper;

import static java.lang.String.format;

import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.beans.GitRepositoryDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BitbucketHelper {
  private static final String USERNAME_ERR = "Unable to get username information from api access for identifier %s";
  private static final String BITBUCKET_CLOUD_URL = "https://bitbucket.org/";

  private BitbucketHelper() {
    throw new IllegalStateException("Utility class");
  }

  public static String fetchUserName(BitbucketConnectorDTO configDTO, String connectorIdentifier) {
    try {
      if (configDTO.getApiAccess().getType() == BitbucketApiAccessType.USERNAME_AND_TOKEN) {
        return ((BitbucketUsernameTokenApiAccessDTO) configDTO.getApiAccess().getSpec()).getUsername();
      }
    } catch (Exception ex) {
      log.error(format(USERNAME_ERR, connectorIdentifier), ex);
      throw new InvalidRequestException(format(USERNAME_ERR, connectorIdentifier));
    }
    throw new InvalidRequestException(format(USERNAME_ERR, connectorIdentifier));
  }

  public static String getBitbucketPRLink(BitbucketConnectorDTO bitbucketConnectorDTO, int prNumber) {
    String url = bitbucketConnectorDTO.getUrl();
    GitRepositoryDTO gitRepo = bitbucketConnectorDTO.getGitRepositoryDetails();
    String workspace = gitRepo.getOrg();
    String repoSlug = gitRepo.getName();
    if (GitClientHelper.isBitBucketSAAS(url)) {
      return BITBUCKET_CLOUD_URL + workspace + "/" + repoSlug + "/pull-requests/" + prNumber;
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      return GitClientHelper.getHttpProtocolPrefix(url) + domain + "/projects/" + workspace.toUpperCase() + "/repos/"
          + repoSlug + "/pull-requests/" + prNumber;
    }
  }
}
