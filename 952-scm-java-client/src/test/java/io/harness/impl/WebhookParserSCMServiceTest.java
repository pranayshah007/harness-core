/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl;

import static io.harness.beans.WebhookEvent.Type.PR;
import static io.harness.constants.Constants.BITBUCKET_CLOUD_HEADER_KEY;
import static io.harness.constants.Constants.BITBUCKET_SERVER_HEADER_KEY;
import static io.harness.product.ci.scm.proto.GitProvider.BITBUCKET;
import static io.harness.product.ci.scm.proto.GitProvider.GITHUB;
import static io.harness.product.ci.scm.proto.GitProvider.GITLAB;
import static io.harness.product.ci.scm.proto.GitProvider.STASH;
import static io.harness.rule.OwnerRule.ADWAIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.HeaderConfig;
import io.harness.beans.PRWebhookEvent;
import io.harness.beans.WebhookBaseAttributes;
import io.harness.beans.WebhookEvent;
import io.harness.beans.WebhookGitUser;
import io.harness.beans.WebhookPayload;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.Reference;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Spy;

public class WebhookParserSCMServiceTest extends CategoryTest {
  @Spy @InjectMocks WebhookParserSCMServiceImpl webhookParserSCMService;
  private ParseWebhookResponse parseWebhookResponse;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
    parseWebhookResponse = ParseWebhookResponse.newBuilder()
                               .setPr(PullRequestHook.newBuilder()
                                          .setAction(Action.OPEN)
                                          .setSender(User.newBuilder()
                                                         .setName("uname")
                                                         .setEmail("userEmail")
                                                         .setLogin("userLogin")
                                                         .setAvatar("userAvtar")
                                                         .build())
                                          .setPr(PullRequest.newBuilder()
                                                     .setSource("sourceBranch")
                                                     .setTarget("targetBranch")
                                                     .setLink("prLink")
                                                     .setNumber(123)
                                                     .setTitle("prTitle")
                                                     .setClosed(false)
                                                     .setMerged(false)
                                                     .setBase(Reference.newBuilder().setSha("beforeSha").build())
                                                     .setSha("afterSha")
                                                     .setRef("prRef")
                                                     .setAuthor(User.newBuilder()
                                                                    .setName("uname")
                                                                    .setEmail("userEmail")
                                                                    .setLogin("userLogin")
                                                                    .setAvatar("userAvtar")
                                                                    .build())
                                                     .build())
                                          .setRepo(Repository.newBuilder()
                                                       .setName("myRepo")
                                                       .setNamespace("myNamespace")
                                                       .setLink("myLink")
                                                       .setBranch("master")
                                                       .setClone("https://myRepo")
                                                       .setCloneSsh("git@myRepo")
                                                       .build())
                                          .build())
                               .build();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void parseObtainWebhookSourceTest() {
    assertThatThrownBy(() -> webhookParserSCMService.obtainWebhookSource(List.of()))
        .isInstanceOf(InvalidRequestException.class);

    List<HeaderConfig> headerKeys =
        Stream.of("a", "b", "c").map(key -> HeaderConfig.builder().key(key).build()).collect(Collectors.toList());
    assertThatThrownBy(() -> webhookParserSCMService.obtainWebhookSource(headerKeys))
        .isInstanceOf(InvalidRequestException.class);

    List<HeaderConfig> headerKeys1 = new ArrayList<>(headerKeys);
    headerKeys1.add(HeaderConfig.builder().key("X-GitHub-Event").build());
    assertThat(webhookParserSCMService.obtainWebhookSource(headerKeys1)).isEqualTo(GITHUB);
    headerKeys1.clear();
    headerKeys1.addAll(headerKeys);
    headerKeys1.add(HeaderConfig.builder().key("x-github-event").build());
    assertThat(webhookParserSCMService.obtainWebhookSource(headerKeys1)).isEqualTo(GITHUB);

    headerKeys1.clear();
    headerKeys1.addAll(headerKeys);
    headerKeys1.add(HeaderConfig.builder().key("X-Gitlab-Event").build());
    assertThat(webhookParserSCMService.obtainWebhookSource(headerKeys1)).isEqualTo(GITLAB);
    headerKeys1.clear();
    headerKeys1.addAll(headerKeys);
    headerKeys1.add(HeaderConfig.builder().key("x-gitlab-event").build());
    assertThat(webhookParserSCMService.obtainWebhookSource(headerKeys1)).isEqualTo(GITLAB);

    headerKeys1.clear();
    headerKeys1.addAll(headerKeys);
    headerKeys1.add(HeaderConfig.builder().key("X-Event-Key").build());
    assertThat(webhookParserSCMService.obtainWebhookSource(headerKeys1)).isEqualTo(BITBUCKET);
    headerKeys1.clear();
    headerKeys1.addAll(headerKeys);
    headerKeys1.add(HeaderConfig.builder().key("x-event-key").build());
    assertThat(webhookParserSCMService.obtainWebhookSource(headerKeys1)).isEqualTo(BITBUCKET);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testIsBitbucketServer() {
    assertThat(webhookParserSCMService.isBitbucketServer(
                   new HashSet<>(Arrays.asList("X-Event-Key", BITBUCKET_CLOUD_HEADER_KEY))))
        .isFalse();
    assertThat(webhookParserSCMService.isBitbucketServer(
                   new HashSet<>(Arrays.asList("X-Event-Key", BITBUCKET_SERVER_HEADER_KEY))))
        .isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetBitbucketProvider() {
    assertThat(webhookParserSCMService.getBitbucketProvider(
                   new HashSet<>(Arrays.asList("X-Event-Key", BITBUCKET_CLOUD_HEADER_KEY))))
        .isEqualTo(BITBUCKET);
    assertThat(webhookParserSCMService.getBitbucketProvider(
                   new HashSet<>(Arrays.asList("X-Event-Key", BITBUCKET_SERVER_HEADER_KEY))))
        .isEqualTo(STASH);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void parseEventTest() {
    WebhookPayload webhookPayloadData = webhookParserSCMService.parseWebhookPayload(parseWebhookResponse);

    assertThat(webhookPayloadData).isNotNull();
    assertRepo(webhookPayloadData.getRepository());
    assertGitUser(webhookPayloadData.getWebhookGitUser());
    assertPullEventData(webhookPayloadData.getWebhookEvent());
  }

  private void assertPullEventData(WebhookEvent webhookEvent) {
    assertThat(webhookEvent).isNotNull();
    assertThat(webhookEvent.getType()).isEqualTo(PR);

    PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookEvent;
    assertThat(prWebhookEvent.getSourceBranch()).isEqualTo("sourceBranch");
    assertThat(prWebhookEvent.getTargetBranch()).isEqualTo("targetBranch");
    assertThat(prWebhookEvent.getPullRequestLink()).isEqualTo("prLink");
    assertThat(prWebhookEvent.getPullRequestId()).isEqualTo(123);
    assertThat(prWebhookEvent.getTitle()).isEqualTo("prTitle");
    assertThat(prWebhookEvent.isClosed()).isFalse();
    assertThat(prWebhookEvent.isMerged()).isFalse();

    WebhookBaseAttributes baseAttributes = prWebhookEvent.getBaseAttributes();
    assertThat(baseAttributes.getAction()).isEqualTo("open");
    assertThat(baseAttributes.getLink()).isEqualTo("prLink");
    assertThat(baseAttributes.getAfter()).isEqualTo("afterSha");
    assertThat(baseAttributes.getBefore()).isEqualTo("beforeSha");
    assertThat(baseAttributes.getRef()).isEqualTo("prRef");
    assertThat(baseAttributes.getSource()).isEqualTo("sourceBranch");
    assertThat(baseAttributes.getTarget()).isEqualTo("targetBranch");
    assertThat(baseAttributes.getAuthorLogin()).isEqualTo("userLogin");
    assertThat(baseAttributes.getAuthorName()).isEqualTo("uname");
    assertThat(baseAttributes.getAuthorEmail()).isEqualTo("userEmail");
    assertThat(baseAttributes.getSender()).isEqualTo("userLogin");
  }

  private void assertGitUser(WebhookGitUser webhookGitUser) {
    assertThat(webhookGitUser).isNotNull();
    assertThat(webhookGitUser.getName()).isEqualTo("uname");
    assertThat(webhookGitUser.getEmail()).isEqualTo("userEmail");
    assertThat(webhookGitUser.getGitId()).isEqualTo("userLogin");
    assertThat(webhookGitUser.getAvatar()).isEqualTo("userAvtar");
  }

  private void assertRepo(io.harness.beans.Repository repository) {
    assertThat(repository).isNotNull();
    assertThat(repository.getName()).isEqualTo("myRepo");
    assertThat(repository.getNamespace()).isEqualTo("myNamespace");
    assertThat(repository.getLink()).isEqualTo("myLink");
    assertThat(repository.getBranch()).isEqualTo("master");
    assertThat(repository.getHttpURL()).isEqualTo("https://myRepo");
    assertThat(repository.getSshURL()).isEqualTo("git@myRepo");
  }
}
