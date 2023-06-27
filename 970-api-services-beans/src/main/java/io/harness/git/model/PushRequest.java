package io.harness.git.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PushRequest extends GitBaseRequest {
  private boolean pushOnlyIfHeadSeen;
  private boolean forcePush;

  public static PushRequest mapFromRevertAndPushRequest(RevertAndPushRequest parent) {
    return PushRequest.builder()
        .accountId(parent.getAccountId())
        .branch(parent.getBranch())
        .authRequest(parent.getAuthRequest())
        .commitId(parent.getCommitId())
        .connectorId(parent.getConnectorId())
        .disableUserGitConfig(parent.getDisableUserGitConfig())
        .connectorId(parent.getConnectorId())
        .repoType(parent.getRepoType())
        .pushOnlyIfHeadSeen(parent.isPushOnlyIfHeadSeen())
        .forcePush(parent.isForcePush())
        .build();
  }
}
