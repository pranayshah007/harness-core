package io.harness.releaseradar.beans;

import io.harness.releaseradar.clients.GitHubApiClient;

import java.util.Date;
import lombok.Builder;
import lombok.ToString;

@Builder
@ToString
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
}