/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.constants.Constants.BITBUCKET_CLOUD_HEADER_KEY;
import static io.harness.constants.Constants.BITBUCKET_SERVER_HEADER_KEY;
import static io.harness.constants.Constants.X_AMZ_SNS_MESSAGE_TYPE;
import static io.harness.constants.Constants.X_BIT_BUCKET_EVENT;
import static io.harness.constants.Constants.X_GIT_HUB_EVENT;
import static io.harness.constants.Constants.X_GIT_LAB_EVENT;
import static io.harness.constants.Constants.X_HARNESS_TRIGGER;
import static io.harness.constants.Constants.X_VSS_HEADER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CommitDetails;
import io.harness.beans.HeaderConfig;
import io.harness.beans.IssueCommentWebhookEvent;
import io.harness.beans.PRWebhookEvent;
import io.harness.beans.PushWebhookEvent;
import io.harness.beans.ReleaseWebhookEvent;
import io.harness.beans.Repository;
import io.harness.beans.WebhookBaseAttributes;
import io.harness.beans.WebhookGitUser;
import io.harness.beans.WebhookPayload;
import io.harness.beans.WebhookPayload.WebhookPayloadBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.GitProvider;
import io.harness.product.ci.scm.proto.Header;
import io.harness.product.ci.scm.proto.IssueCommentHook;
import io.harness.product.ci.scm.proto.ParseWebhookRequest;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.ReleaseHook;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.product.ci.scm.proto.Signature;
import io.harness.product.ci.scm.proto.User;
import io.harness.service.WebhookParserSCMService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(DX)
public class WebhookParserSCMServiceImpl implements WebhookParserSCMService {
  @Inject private final SCMGrpc.SCMBlockingStub scmBlockingStub;

  @Override
  public ParseWebhookResponse parseWebhookUsingSCMAPI(List<HeaderConfig> headers, String payload) {
    Set<String> headerKeys = headers.stream().map(HeaderConfig::getKey).collect(toSet());
    GitProvider gitProvider = obtainWebhookSource(headerKeys);
    Header.Builder header = Header.newBuilder();
    headers.forEach(headerConfig
        -> header.addFields(
            Header.Pair.newBuilder().setKey(headerConfig.getKey()).addAllValues(headerConfig.getValues()).build()));

    ParseWebhookResponse parseWebhookResponse;
    try {
      Stopwatch stopwatch = Stopwatch.createStarted();
      parseWebhookResponse = scmBlockingStub.parseWebhook(
          ParseWebhookRequest.newBuilder().setBody(payload).setProvider(gitProvider).setHeader(header.build()).build());
      log.info("Finished parsing webhook payload in {} ", stopwatch.elapsed(TimeUnit.SECONDS));
    } catch (StatusRuntimeException e) {
      log.error("Failed to parse webhook payload");
      throw e;
    }
    return parseWebhookResponse;
  }

  @Override
  public WebhookPayload parseWebhookPayload(ParseWebhookResponse parseWebhookResponse) {
    WebhookPayloadBuilder builder;
    if (parseWebhookResponse.hasPr()) {
      PullRequestHook prHook = parseWebhookResponse.getPr();
      builder = convertPullRequestHook(prHook);
    } else if (parseWebhookResponse.hasPush()) {
      PushHook pushHook = parseWebhookResponse.getPush();
      builder = convertPushHook(pushHook);
    } else if (parseWebhookResponse.hasComment() && parseWebhookResponse.getComment().getIssue() != null
        && parseWebhookResponse.getComment().getIssue().getPr() != null) {
      IssueCommentHook commentHook = parseWebhookResponse.getComment();
      builder = convertComment(commentHook);
    } else if (parseWebhookResponse.hasRelease()) {
      ReleaseHook releaseHook = parseWebhookResponse.getRelease();
      builder = convertRelease(releaseHook);
    } else {
      log.error("Unsupported webhook event");
      throw new InvalidRequestException("Unsupported webhook event", USER);
    }
    return builder.build();
  }

  private WebhookPayloadBuilder convertPullRequestHook(PullRequestHook prHook) {
    WebhookGitUser webhookGitUser = convertUser(prHook.getSender());
    PRWebhookEvent prWebhookEvent = convertPRWebhookEvent(prHook);

    return WebhookPayload.builder()
        .webhookGitUser(webhookGitUser)
        .repository(prWebhookEvent.getRepository())
        .webhookEvent(prWebhookEvent);
  }

  WebhookPayloadBuilder convertPushHook(PushHook pushHook) {
    WebhookGitUser webhookGitUser = convertUser(pushHook.getSender());
    PushWebhookEvent webhookEvent = convertPushWebhookEvent(pushHook);

    return WebhookPayload.builder()
        .webhookGitUser(webhookGitUser)
        .repository(webhookEvent.getRepository())
        .webhookEvent(webhookEvent);
  }

  WebhookPayloadBuilder convertComment(IssueCommentHook commentHook) {
    Repository repository = convertRepository(commentHook.getRepo());
    WebhookGitUser webhookGitUser = convertUser(commentHook.getSender());

    IssueCommentWebhookEvent webhookEvent =
        IssueCommentWebhookEvent.builder()
            .commentBody(commentHook.getComment().getBody())
            .pullRequestNum(Integer.toString(commentHook.getIssue().getNumber()))
            .repository(repository)
            .baseAttributes(
                WebhookBaseAttributes.builder().action(commentHook.getAction().name().toLowerCase()).build())
            .build();

    return WebhookPayload.builder().webhookGitUser(webhookGitUser).repository(repository).webhookEvent(webhookEvent);
  }

  WebhookPayloadBuilder convertRelease(ReleaseHook releaseHook) {
    Repository repository = convertRepository(releaseHook.getRepo());
    WebhookGitUser webhookGitUser = convertUser(releaseHook.getSender());

    ReleaseWebhookEvent webhookEvent =
        ReleaseWebhookEvent.builder()
            .repository(repository)
            .baseAttributes(
                WebhookBaseAttributes.builder().action(releaseHook.getAction().name().toLowerCase()).build())
            .build();

    return WebhookPayload.builder().webhookGitUser(webhookGitUser).repository(repository).webhookEvent(webhookEvent);
  }

  @Override
  public PRWebhookEvent convertPRWebhookEvent(PullRequestHook prHook) {
    // TODO Add commit details
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
        .build();
  }

  private PushWebhookEvent convertPushWebhookEvent(PushHook pushHook) {
    // TODO Add required push event details here with commit
    List<CommitDetails> commitDetailsList = new ArrayList<>();
    pushHook.getCommitsList().forEach(commit -> commitDetailsList.add(convertCommit(commit)));

    return PushWebhookEvent.builder()
        .branchName(pushHook.getRepo().getBranch())
        .link(pushHook.getRepo().getLink())
        .commitDetailsList(commitDetailsList)
        .repository(convertRepository(pushHook.getRepo()))
        .baseAttributes(convertPushHookBaseAttributes(pushHook))
        .build();
  }

  private WebhookGitUser convertUser(User user) {
    return WebhookGitUser.builder()
        .avatar(user.getAvatar())
        .email(user.getEmail())
        .gitId(user.getLogin())
        .name(user.getName())
        .build();
  }

  private CommitDetails convertCommit(Commit commit) {
    return CommitDetails.builder()
        .commitId(commit.getSha())
        .message(commit.getMessage())
        .link(commit.getLink())
        .timeStamp(commit.getCommitter().getDate().getSeconds() * 1000)
        .ownerEmail(commit.getAuthor().getEmail())
        .ownerId(commit.getAuthor().getLogin())
        .ownerName(commit.getAuthor().getName())
        .build();
  }

  private Repository convertRepository(io.harness.product.ci.scm.proto.Repository repo) {
    return Repository.builder()
        .id(repo.getId())
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

  public GitProvider obtainWebhookSource(Set<String> headerKeys) {
    if (isEmpty(headerKeys)) {
      throw new InvalidRequestException("Failed to resolve Webhook Source. Reason: HttpHeaders are empty.");
    }

    if (containsHeaderKey(headerKeys, X_GIT_HUB_EVENT)) {
      return GitProvider.GITHUB;
    } else if (containsHeaderKey(headerKeys, X_GIT_LAB_EVENT)) {
      return GitProvider.GITLAB;
    } else if (containsHeaderKey(headerKeys, X_BIT_BUCKET_EVENT)) {
      return getBitbucketProvider(headerKeys);
    } else if (containsHeaderKey(headerKeys, X_AMZ_SNS_MESSAGE_TYPE)) {
      return GitProvider.CODECOMMIT;
    } else if (containsHeaderKey(headerKeys, X_VSS_HEADER)) {
      return GitProvider.AZURE;
    } else if (containsHeaderKey(headerKeys, X_HARNESS_TRIGGER)) {
      return GitProvider.HARNESS;
    }

    throw new InvalidRequestException("Unable to resolve the Webhook Source. "
            + "One of " + X_GIT_HUB_EVENT + ", " + X_BIT_BUCKET_EVENT + ", " + X_GIT_LAB_EVENT + ", " + X_VSS_HEADER
            + " must be present in Headers",
        USER);
  }

  public boolean containsHeaderKey(Set<String> headerKeys, String key) {
    if (isEmpty(headerKeys) || isBlank(key)) {
      return false;
    }

    return headerKeys.contains(key) || headerKeys.contains(key.toLowerCase())
        || headerKeys.stream().anyMatch(key::equalsIgnoreCase);
  }

  @VisibleForTesting
  GitProvider getBitbucketProvider(Set<String> headerKeys) {
    if (containsHeaderKey(headerKeys, BITBUCKET_CLOUD_HEADER_KEY)) {
      return GitProvider.BITBUCKET;
    } else if (isBitbucketServer(headerKeys)) {
      return GitProvider.STASH;
    } else {
      StringBuilder stringBuilder = new StringBuilder(
          "TRIGGER: Could not determine if source is Bitbucket Cloud or Server, defaulting to Cloud. Please verify header again. ");
      headerKeys.forEach(key -> stringBuilder.append(key).append(','));
      log.warn(stringBuilder.toString());
      return GitProvider.BITBUCKET;
    }
  }

  @VisibleForTesting
  boolean isBitbucketServer(Set<String> headerKeys) {
    return containsHeaderKey(headerKeys, BITBUCKET_SERVER_HEADER_KEY);
  }

  private WebhookBaseAttributes convertPrHookBaseAttributes(PullRequestHook prHook) {
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
        .action(prHook.getAction().name().toLowerCase())
        .build();
  }

  private WebhookBaseAttributes convertPushHookBaseAttributes(PushHook pushHook) {
    String trimmedRef = pushHook.getRef().replaceFirst("^refs/heads/", "");
    Signature author = pushHook.getCommit().getAuthor();
    return WebhookBaseAttributes.builder()
        .link(pushHook.getCommit().getLink())
        .message(pushHook.getCommit().getMessage())
        .before(pushHook.getBefore())
        .after(pushHook.getAfter())
        .ref(pushHook.getRef())
        .source(EMPTY)
        .target(trimmedRef)
        .authorLogin(author.getLogin())
        .authorName(author.getName())
        .authorEmail(author.getEmail())
        .authorAvatar(author.getAvatar())
        .sender(pushHook.getSender().getLogin())
        .build();
  }
}
