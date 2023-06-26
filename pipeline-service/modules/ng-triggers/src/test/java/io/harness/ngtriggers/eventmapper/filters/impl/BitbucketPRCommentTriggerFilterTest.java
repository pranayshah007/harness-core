/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.rule.OwnerRule.SHIVAM;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.PRWebhookEvent;
import io.harness.beans.Repository;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.Metadata;
import io.harness.polling.contracts.PollingResponse;
import io.harness.product.ci.scm.proto.Issue;
import io.harness.product.ci.scm.proto.IssueCommentHook;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.rule.Owner;
import io.harness.service.WebhookParserSCMService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

public class BitbucketPRCommentTriggerFilterTest extends CategoryTest {
  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;
  @Inject @InjectMocks private BitbucketPRCommentTriggerFilter bitbucketPRCommentTriggerFilter;
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Mock private PayloadConditionsTriggerFilter payloadConditionsTriggerFilter;
  private String ngTriggerYaml_bitbucket_pr_comment;
  @Mock BuildTriggerHelper buildTriggerHelper;
  @Mock WebhookParserSCMService webhookParserSCMService;
  private static Repository repository1 = Repository.builder()
                                              .httpURL("https://github.com/owner1/repo1.git")
                                              .sshURL("git@github.com:owner1/repo1.git")
                                              .link("https://github.com/owner1/repo1/b")
                                              .build();

  @Before
  public void setUp() throws IOException, IllegalAccessException {
    initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    logger = (Logger) LoggerFactory.getLogger(BitbucketPRCommentTriggerFilter.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    ngTriggerYaml_bitbucket_pr_comment =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-bitbucket-pr-comment-v2.yaml")),
            StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterTest() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long createdAt = 12L;
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_bitbucket_pr_comment);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("BITBUCKET")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(PRWebhookEvent.builder().repository(repository1).build())
                    .originalEvent(TriggerWebhookEvent.builder()
                                       .sourceRepoType(WebhookTriggerType.GITHUB.getEntityMetadataName())
                                       .createdAt(createdAt)
                                       .build())
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("release.1234"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    doReturn(PRWebhookEvent.builder().build()).when(webhookParserSCMService).convertPRWebhookEvent(any());
    doReturn(WebhookEventMappingResponse.builder().failedToFindTrigger(false).build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(any());
    WebhookEventMappingResponse webhookEventMappingResponse =
        bitbucketPRCommentTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.getParseWebhookResponse()).isNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterExceptionTest() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long createdAt = 12L;
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_bitbucket_pr_comment);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("BITBUCKET")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(PRWebhookEvent.builder().repository(repository1).build())
                    .originalEvent(TriggerWebhookEvent.builder()
                                       .sourceRepoType(WebhookTriggerType.GITHUB.getEntityMetadataName())
                                       .createdAt(createdAt)
                                       .build())
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("release.1234"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    doReturn(WebhookEventMappingResponse.builder().failedToFindTrigger(false).build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(any());
    WebhookEventMappingResponse webhookEventMappingResponse =
        bitbucketPRCommentTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.getParseWebhookResponse()).isNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
  }
}
