/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service;

import io.harness.gitpolling.github.GitPollingWebhookData;

import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public interface GithubService {
  String getToken(GithubAppConfig githubAppConfig);

  boolean sendStatus(GithubAppConfig githubAppConfig, String token, String sha, String owner, String repo,
      Map<String, Object> bodyObjectMap);

  String findPR(String apiUrl, String token, String owner, String repo, String prNumber);

  JSONObject mergePR(String apiUrl, String token, String owner, String repo, String prNumber);

  boolean deleteRef(String apiUrl, String token, String owner, String repo, String ref);

  List<GitPollingWebhookData> getWebhookRecentDeliveryEvents(
      String apiUrl, String token, String repoOwner, String repoName, String webhookId);
}
