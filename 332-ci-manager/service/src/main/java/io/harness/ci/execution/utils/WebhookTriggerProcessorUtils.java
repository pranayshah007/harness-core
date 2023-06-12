/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.CommitDetails;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.ReleaseWebhookEvent;
import io.harness.beans.execution.Repository;
import io.harness.beans.execution.WebhookBaseAttributes;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.execution.WebhookGitUser;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Release;
import io.harness.product.ci.scm.proto.ReleaseHook;
import io.harness.product.ci.scm.proto.Signature;
import io.harness.product.ci.scm.proto.User;

import io.fabric8.utils.Strings;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class WebhookTriggerProcessorUtils {
  public static WebhookExecutionSource convertWebhookResponse(ParsedPayload parseWebhookResponse) {
    if (parseWebhookResponse.hasPr()) {
      PullRequestHook prHook = parseWebhookResponse.getPr();
      return convertPullRequestHook(prHook);
    } else if (parseWebhookResponse.hasPush()) {
      PushHook pushHook = parseWebhookResponse.getPush();
      return convertPushHook(pushHook);
    } else if (parseWebhookResponse.hasRelease()) {
      ReleaseHook releaseHook = parseWebhookResponse.getRelease();
      return convertReleaseHook(releaseHook);
    } else {
      log.error("Unknown webhook event");
      throw new InvalidRequestException("Unknown webhook event", USER);
    }
  }

  private static WebhookExecutionSource convertPullRequestHook(PullRequestHook prHook) {
    WebhookGitUser webhookGitUser = convertUser(prHook.getSender());
    WebhookEvent webhookEvent = convertPRWebhookEvent(prHook);

    return WebhookExecutionSource.builder().user(webhookGitUser).webhookEvent(webhookEvent).build();
  }

  private static WebhookExecutionSource convertPushHook(PushHook pushHook) {
    WebhookGitUser webhookGitUser = convertUser(pushHook.getSender());
    WebhookEvent webhookEvent = convertPushWebhookEvent(pushHook);

    return WebhookExecutionSource.builder().user(webhookGitUser).webhookEvent(webhookEvent).build();
  }

  private static WebhookExecutionSource convertReleaseHook(ReleaseHook releaseHook) {
    WebhookGitUser webhookGitUser = convertUser(releaseHook.getSender());
    WebhookEvent webhookEvent = convertReleaseWebhookEvent(releaseHook);

    return WebhookExecutionSource.builder().user(webhookGitUser).webhookEvent(webhookEvent).build();
  }

  private static WebhookEvent convertPushWebhookEvent(PushHook pushHook) {
    List<CommitDetails> commitDetailsList = new ArrayList<>();
    pushHook.getCommitsList().forEach(commit -> commitDetailsList.add(convertCommit(commit)));

    return BranchWebhookEvent.builder()
        .branchName(pushHook.getRef().replaceFirst("^refs/heads/", ""))
        .link(pushHook.getRepo().getLink())
        .commitDetailsList(commitDetailsList)
        .repository(convertRepository(pushHook.getRepo()))
        .baseAttributes(convertPushHookBaseAttributes(pushHook))
        .build();
  }

  private static WebhookEvent convertPRWebhookEvent(PullRequestHook prHook) {
    List<CommitDetails> commitDetailsList = new ArrayList<>();
    prHook.getPr().getCommitsList().forEach(commit -> commitDetailsList.add(convertCommit(commit)));

    PullRequest pr = prHook.getPr();
    return PRWebhookEvent.builder()
        .sourceBranch(pr.getSource())
        .targetBranch(pr.getTarget())
        .pullRequestLink(pr.getLink())
        .pullRequestBody(pr.getBody())
        .pullRequestId(pr.getNumber())
        .title(pr.getTitle())
        .closed(pr.getClosed())
        .merged(pr.getMerged())
        .repository(convertRepository(prHook.getRepo()))
        .baseAttributes(convertPrHookBaseAttributes(prHook))
        .commitDetailsList(commitDetailsList)
        .build();
  }

  private static WebhookEvent convertReleaseWebhookEvent(ReleaseHook releaseHook) {
    Release release = releaseHook.getRelease();

    return ReleaseWebhookEvent.builder()
        .releaseLink(release.getLink())
        .releaseBody(release.getDescription())
        .releaseTag(release.getTag())
        .title(release.getTitle())
        .prerelease(release.getPrerelease())
        .draft(release.getDraft())
        .repository(convertRepository(releaseHook.getRepo()))
        .baseAttributes(convertReleaseHookBaseAttributes(releaseHook))
        .build();
  }

  private static WebhookBaseAttributes convertPrHookBaseAttributes(PullRequestHook prHook) {
    PullRequest pr = prHook.getPr();
    User author = prHook.getPr().getAuthor();
    String message = pr.getBody();
    if (message.equals("")) {
      message = pr.getTitle();
    }
    return WebhookBaseAttributes.builder()
        .link(pr.getLink())
        .message(message)
        .before(pr.getBase().getSha())
        .after(pr.getSha())
        .ref(pr.getRef())
        .source(pr.getSource())
        .target(pr.getTarget())
        .authorLogin(author.getLogin())
        .authorName(author.getName())
        .authorEmail(author.getEmail())
        .authorAvatar(author.getAvatar())
        .sender(prHook.getSender().getLogin())
        .action(prHook.getAction().toString().toLowerCase())
        .mergeSha(pr.getMergeSha())
        .build();
  }

  private static WebhookBaseAttributes convertPushHookBaseAttributes(PushHook pushHook) {
    String trimmedRef = pushHook.getRef().replaceFirst("^refs/heads/", "");
    Signature author = pushHook.getCommit().getAuthor();
    return WebhookBaseAttributes.builder()
        .link(pushHook.getCommit().getLink())
        .message(pushHook.getCommit().getMessage())
        .before(pushHook.getBefore())
        .after(pushHook.getAfter())
        .ref(pushHook.getRef())
        .source(trimmedRef)
        .target(trimmedRef)
        .authorLogin(author.getLogin())
        .authorName(author.getName())
        .authorEmail(author.getEmail())
        .authorAvatar(author.getAvatar())
        .sender(pushHook.getSender().getLogin())
        .build();
  }

  private static WebhookBaseAttributes convertReleaseHookBaseAttributes(ReleaseHook releaseHook) {
    Release release = releaseHook.getRelease();
    User author = releaseHook.getSender();
    String message = release.getDescription();
    if (message.equals("")) {
      message = release.getTitle();
    }
    return WebhookBaseAttributes.builder()
        .link(release.getLink())
        .message(message)
        .authorLogin(author.getLogin())
        .authorName(author.getName())
        .authorEmail(author.getEmail())
        .authorAvatar(author.getAvatar())
        .sender(author.getLogin())
        .action(releaseHook.getAction().toString().toLowerCase())
        .build();
  }

  private static WebhookGitUser convertUser(User user) {
    String id;
    if (!isEmpty(user.getLogin())) {
      id = user.getLogin();
    } else {
      id = user.getId();
    }
    return WebhookGitUser.builder()
        .avatar(user.getAvatar())
        .email(user.getEmail())
        .gitId(id)
        .name(user.getName())
        .build();
  }

  public static String getShortCommitSha(String commitSha) {
    String shortCommitSha = null;
    if (Strings.isNotBlank(commitSha)) {
      shortCommitSha = commitSha.substring(0, Math.min(commitSha.length(), 7));
    }
    return shortCommitSha;
  }

  private static CommitDetails convertCommit(Commit commit) {
    long timeStamp = commit.getCommitter().getDate().getSeconds();
    return CommitDetails.builder()
        .commitId(commit.getSha())
        .message(commit.getMessage())
        .link(commit.getLink())
        .timeStamp(timeStamp)
        .ownerEmail(commit.getAuthor().getEmail())
        .ownerId(commit.getAuthor().getLogin())
        .ownerName(commit.getAuthor().getName())
        .build();
  }

  private static Repository convertRepository(io.harness.product.ci.scm.proto.Repository repo) {
    return Repository.builder()
        .name(repo.getName())
        .namespace(repo.getNamespace())
        .slug(repo.getNamespace() + "/" + repo.getName())
        .link(repo.getLink())
        .branch(repo.getBranch())
        .isPrivate(repo.getPrivate())
        .httpURL(repo.getClone())
        .sshURL(repo.getCloneSsh())
        .build();
  }
}
