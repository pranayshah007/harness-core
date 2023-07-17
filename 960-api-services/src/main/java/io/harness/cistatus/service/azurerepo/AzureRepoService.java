/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.azurerepo;

import java.util.Map;
import org.json.JSONObject;

public interface AzureRepoService {
  boolean sendStatus(AzureRepoConfig azureRepoConfig, String userName, String token, String sha, String prNumber,
      String org, String project, String repo, Map<String, Object> bodyObjectMap);
  JSONObject mergePR(AzureRepoConfig azureRepoConfig, String token, String sha, String org, String project, String repo,
      String prNumber, boolean deleteSourceBranch, Map<String, Object> apiParamOptions);
}
