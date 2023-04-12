/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.services;

import io.harness.deploydetective.beans.CommitDetails;
import io.harness.deploydetective.beans.CommitDetailsRequest;
import io.harness.deploydetective.clients.GitHubApiClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GitServiceImpl implements GitService {
  GitHubApiClient gitHubApiClient = new GitHubApiClient();
  String GITHUB_API_KEY = System.getenv("GITHUB_X_API_KEY");

  @Override
  public List<CommitDetails> getCommitList(CommitDetailsRequest commitDetailsRequest) throws IOException {
    List<GitHubApiClient.Commit> githubCommitList = new ArrayList<>();
    List<CommitDetails> commitDetailsList = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      List<GitHubApiClient.Commit> commitList =
          gitHubApiClient.listCommits("harness", "harness-core", "GITHUB_API_KEY", i);
      githubCommitList.addAll(commitList);
    }

    githubCommitList.forEach(commit -> commitDetailsList.add(CommitDetails.toCommitDetails(commit)));
    return commitDetailsList;
  }
}
