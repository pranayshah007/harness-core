/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.beans;

import io.harness.releaseradar.clients.GitHubApiClient;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.Date;
import java.util.Objects;

@Builder
@ToString
@Data
public class CommitDetails {
  String sha;
  String message;
  Date date;

  public static CommitDetails toCommitDetails(GitHubApiClient.Commit githubCommit) {
    return CommitDetails.builder()
        .date(githubCommit.getCommit().getCommitter().getDate())
        .sha(githubCommit.getSha())
        .message(githubCommit.getCommit().getMessage())
        .build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CommitDetails that = (CommitDetails) o;
    return Objects.equals(sha, that.sha);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sha);
  }
}
