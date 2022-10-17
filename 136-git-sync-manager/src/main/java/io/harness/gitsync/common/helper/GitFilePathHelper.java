/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.gitsync.beans.GitRepositoryDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class GitFilePathHelper {
  GitSyncConnectorHelper gitSyncConnectorHelper;

  public String getFileUrl(
      Scope scope, String connectorRef, String branchName, String filePath, GitRepositoryDTO gitRepositoryDTO) {
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), connectorRef, gitRepositoryDTO.getName());
    return scmConnector.getFileUrl(branchName, filePath, gitRepositoryDTO);
  }
}
