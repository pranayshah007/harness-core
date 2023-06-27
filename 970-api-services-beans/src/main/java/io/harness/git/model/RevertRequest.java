package io.harness.git.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RevertRequest extends GitBaseRequest {
  private String commitMessage;
  private String authorName;
  private String authorEmail;

  public static RevertRequest mapFromRevertAndPushRequest(RevertAndPushRequest parent) {
    return RevertRequest.builder()
        .authorEmail(parent.getAuthorEmail())
        .authorName(parent.getAuthorName())
        .commitMessage(parent.getCommitMessage())
        .accountId(parent.getAccountId())
        .branch(parent.getBranch())
        .authRequest(parent.getAuthRequest())
        .commitId(parent.getCommitId())
        .connectorId(parent.getConnectorId())
        .disableUserGitConfig(parent.getDisableUserGitConfig())
        .connectorId(parent.getConnectorId())
        .repoType(parent.getRepoType())
        .build();
  }
}
