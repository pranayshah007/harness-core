/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.FAILED_TO_FETCH_PR_DETAILS;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.SHIVAM;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.IssueCommentWebhookEvent;
import io.harness.beans.Repository;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.gitapi.GitApiFindPRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.Metadata;
import io.harness.polling.contracts.PollingResponse;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.Issue;
import io.harness.product.ci.scm.proto.IssueCommentHook;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.WebhookParserSCMService;
import io.harness.tasks.BinaryResponseData;
import io.harness.utils.ConnectorUtils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
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

public class GithubIssueCommentTriggerFilterTest extends CategoryTest {
  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;
  @Inject @InjectMocks private GithubIssueCommentTriggerFilter githubIssueCommentTriggerFilter;
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;

  @Mock ConnectorUtils connectorUtils;
  @Mock TaskExecutionUtils taskExecutionUtils;
  @Mock WebhookParserSCMService webhookParserSCMService;
  @Mock PayloadConditionsTriggerFilter payloadConditionsTriggerFilter;
  private static Repository repository1 = Repository.builder()
                                              .httpURL("https://github.com/owner1/repo1.git")
                                              .sshURL("git@github.com:owner1/repo1.git")
                                              .link("https://github.com/owner1/repo1/b")
                                              .build();

  String pushPayload = "{\"commits\": [\n"
      + "  {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest1.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File1_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value1.yml\"\n"
      + "    ]\n"
      + "  }, \n"
      + "  {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest2.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File2_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value2.yml\"\n"
      + "    ]\n"
      + "  }\n"
      + "]}";

  @Before
  public void setUp() throws IOException, IllegalAccessException {
    initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    logger = (Logger) LoggerFactory.getLogger(BitbucketPRCommentTriggerFilter.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long creatAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
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
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(creatAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("20").build())
                    .originalEvent(triggerWebhookEvent)
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
    byte[] data = new byte[0];
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .webhookEventResponse(TriggerEventResponse.builder().payload(pushPayload).build())
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(filterRequestData);
    WebhookEventMappingResponse webhookEventMappingResponse =
        githubIssueCommentTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterEmptyPrJsonTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long creatAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
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
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(creatAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("20").build())
                    .originalEvent(triggerWebhookEvent)
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
    byte[] data = new byte[0];
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson("").build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .webhookEventResponse(TriggerEventResponse.builder().payload(pushPayload).build())
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(filterRequestData);
    WebhookEventMappingResponse webhookEventMappingResponse =
        githubIssueCommentTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getMessage())
        .isEqualTo("Failed to fetch PR Details");
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getPayload()).isEqualTo(pushPayload);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterExceptionTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long creatAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
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
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(creatAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("20").build())
                    .originalEvent(triggerWebhookEvent)
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
    byte[] data = new byte[0];
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doThrow(NullPointerException.class).when(webhookParserSCMService).convertPRWebhookEvent(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .webhookEventResponse(TriggerEventResponse.builder().payload(pushPayload).build())
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(filterRequestData);
    WebhookEventMappingResponse webhookEventMappingResponse =
        githubIssueCommentTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getMessage())
        .isEqualTo("Failed to fetch PR Details: java.lang.NullPointerException");
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getPayload()).isEqualTo(pushPayload);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFailedToFetchPr() {
    FilterRequestData filterRequestData1 =
        FilterRequestData.builder()
            .isCustomTrigger(false)
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().createdAt(1L).build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("pullRequestNum").build())
                    .repository(Repository.builder().branch("branch").name("name").build())
                    .build())
            .details(Collections.singletonList(
                TriggerDetails.builder()
                    .ngTriggerEntity(NGTriggerEntity.builder()
                                         .metadata(NGTriggerMetadata.builder()
                                                       .webhook(WebhookMetadata.builder()
                                                                    .git(GitMetadata.builder()
                                                                             .connectorIdentifier("connectorIdentifier")
                                                                             .build())
                                                                    .build())
                                                       .build())
                                         .build())
                    .build()))
            .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    BinaryResponseData binaryResponseData = BinaryResponseData.builder().build();
    when(taskExecutionUtils.executeSyncTask(any())).thenReturn(binaryResponseData);
    GitApiTaskResponse gitApiTaskResponse = GitApiTaskResponse.builder().errorMessage("errorMessage").build();
    when(kryoSerializer.asInflatedObject(any())).thenReturn(gitApiTaskResponse);
    WebhookEventMappingResponse webhookEventMappingResponse1 =
        githubIssueCommentTriggerFilter.applyFilter(filterRequestData1);
    assertThat(webhookEventMappingResponse1.isFailedToFindTrigger()).isEqualTo(true);
    assertThat(webhookEventMappingResponse1.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(FAILED_TO_FETCH_PR_DETAILS);
  }
}
