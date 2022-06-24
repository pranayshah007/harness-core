/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.yaml;

import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.GitFetchFilesRequest;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.yaml.GitClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import org.slf4j.Logger;

@Singleton
public class GitIntegrationTestUtils {
  @Inject private DelegateService delegateService;
  @Inject private SecretManager secretManager;
  @Inject private GitClient gitClient;

  public GitFetchFilesResult fetchFromGitUsingUsingBranch(
      SettingAttribute settingAttribute, String branchName, Logger logger, List<String> filePaths) throws Exception {
    GitConfig gitConfig = (GitConfig) settingAttribute.getValue();

    GitFetchFilesRequest gitFetchFilesRequest = GitFetchFilesRequest.builder()
                                                    .gitConnectorId(settingAttribute.getUuid())
                                                    .branch(branchName)
                                                    .filePaths(filePaths)
                                                    .useBranch(true)
                                                    .build();

    return gitClient.fetchFilesByPath(gitConfig, gitFetchFilesRequest, false, null);
  }
}
