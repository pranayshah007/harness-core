/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.CommitDetails;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.execution.WebhookGitUser;
import io.harness.ci.pipeline.executions.beans.CIBuildAuthor;
import io.harness.ci.pipeline.executions.beans.CIBuildBranchHook;
import io.harness.ci.pipeline.executions.beans.CIBuildCommit;
import io.harness.ci.pipeline.executions.beans.CIBuildPRHook;
import io.harness.ci.pipeline.executions.beans.CIWebhookInfoDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CI)
public class CIModuleInfoMapper {
  private static final String PR_OPEN = "open";
  private static final String PR_CLOSED = "closed";
  private static final String PR_MERGED = "merged";
  private static final String PR = "pullRequest";
  private static final String BRANCH = "branch";

  public CIWebhookInfoDTO getCIBuildResponseDTO(ExecutionSource executionSource) throws InternalError {
    CIWebhookInfoDTO ciWebhookInfoDTO = CIWebhookInfoDTO.builder().build();
    if (executionSource != null) {
      if (executionSource.getType() == ExecutionSource.Type.WEBHOOK) {
        WebhookExecutionSource webhookExecutionSource = (WebhookExecutionSource) executionSource;
        ciWebhookInfoDTO.setAuthor(convertGitAuthor(webhookExecutionSource.getUser()));
        if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.PR) {
          PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();
          ciWebhookInfoDTO.setPullRequest(convertPR(prWebhookEvent));
          ciWebhookInfoDTO.setEvent(PR);
        } else if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.BRANCH) {
          BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();
          ciWebhookInfoDTO.setBranch(convertBranch(branchWebhookEvent));
          ciWebhookInfoDTO.setEvent(BRANCH);
        }
      }
    }

    return ciWebhookInfoDTO;
  }

  private CIBuildPRHook convertPR(PRWebhookEvent pr) {
    String state = PR_OPEN;
    if (pr.isClosed()) {
      state = PR_CLOSED;
    } else if (pr.isMerged()) {
      state = PR_MERGED;
    }
    List<CIBuildCommit> commitList = new ArrayList<>();
    if (isNotEmpty(pr.getCommitDetailsList())) {
      pr.getCommitDetailsList().forEach(commit -> commitList.add(convertCommit(commit)));
    }
    Collections.reverse(commitList);
    return CIBuildPRHook.builder()
        .id(pr.getPullRequestId())
        .link(pr.getPullRequestLink())
        .title(pr.getTitle())
        .body(pr.getPullRequestBody())
        .sourceBranch(pr.getSourceBranch())
        .targetBranch(pr.getTargetBranch())
        .state(state)
        .commits(commitList)
        .build();
  }

  private CIBuildBranchHook convertBranch(BranchWebhookEvent branch) {
    List<CIBuildCommit> commitList = new ArrayList<>();
    branch.getCommitDetailsList().forEach(commit -> commitList.add(convertCommit(commit)));
    return CIBuildBranchHook.builder().name(branch.getBranchName()).link(branch.getLink()).commits(commitList).build();
  }

  private CIBuildAuthor convertGitAuthor(WebhookGitUser user) {
    return CIBuildAuthor.builder()
        .id(user.getGitId())
        .name(user.getName())
        .email(user.getEmail())
        .avatar(user.getAvatar())
        .build();
  }

  private CIBuildCommit convertCommit(CommitDetails commit) {
    return CIBuildCommit.builder()
        .id(commit.getCommitId())
        .message(commit.getMessage())
        .link(commit.getLink())
        .ownerEmail(commit.getOwnerEmail())
        .ownerId(commit.getOwnerId())
        .ownerName(commit.getOwnerName())
        .timeStamp(convertToMilliSeconds(commit.getTimeStamp()))
        .build();
  }

  private long convertToMilliSeconds(long seconds) {
    return seconds * 1000;
  }
}
