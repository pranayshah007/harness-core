/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.MATT;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventBuilder;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventsKeys;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData.WebhookPayloadDataBuilder;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.AccountCustomTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.AccountTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.BitbucketPRCommentTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.EventActionTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.FilepathTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.GitWebhookTriggerRepoFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.GithubIssueCommentTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.GitlabMRCommentTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.HeaderTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.IssueCommentTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.JexlConditionsTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.PayloadConditionsTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.SourceRepoTypeTriggerFilter;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.product.ci.scm.proto.Issue;
import io.harness.product.ci.scm.proto.IssueCommentHook;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public class TriggerFilterHelperTest extends CategoryTest {
  @Mock GitWebhookTriggerRepoFilter gitWebhookTriggerRepoFilter;
  @Mock FilepathTriggerFilter filepathTriggerFilter;
  @Mock AccountTriggerFilter accountTriggerFilter;
  @Mock AccountCustomTriggerFilter accountCustomTriggerFilter;
  @Mock SourceRepoTypeTriggerFilter sourceRepoTypeTriggerFilter;
  @Mock EventActionTriggerFilter eventActionTriggerFilter;
  @Mock PayloadConditionsTriggerFilter payloadConditionsTriggerFilter;
  @Mock GithubIssueCommentTriggerFilter githubIssueCommentTriggerFilter;
  @Mock GitlabMRCommentTriggerFilter gitlabMRCommentTriggerFilter;
  @Mock BitbucketPRCommentTriggerFilter bitbucketPRCommentTriggerFilter;
  @Mock IssueCommentTriggerFilter issueCommentTriggerFilter;
  @Mock HeaderTriggerFilter headerTriggerFilter;
  @Mock JexlConditionsTriggerFilter jexlConditionsTriggerFilter;
  @Inject @InjectMocks TriggerFilterStore triggerFilterStore;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetList() {
    Criteria expected = new Criteria();
    expected.and(NGTriggerEntityKeys.accountId).is("acc");
    expected.and(NGTriggerEntityKeys.orgIdentifier).is("org");
    expected.and(NGTriggerEntityKeys.projectIdentifier).is("proj");
    expected.and(NGTriggerEntityKeys.targetIdentifier).is("pipeline");
    expected.and(NGTriggerEntityKeys.deleted).is(false);
    expected.and(NGTriggerEntityKeys.type).is(NGTriggerType.WEBHOOK);
    assertThat(TriggerFilterHelper
                   .createCriteriaForGetList("acc", "org", "proj", "pipeline", NGTriggerType.WEBHOOK, null, false)
                   .toString())
        .isEqualTo(expected.toString());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCreateCriteriaForCustomWebhookTriggerGetList() {
    Criteria expected = new Criteria();
    expected.and(NGTriggerEntityKeys.accountId).is("acc");
    expected.and(NGTriggerEntityKeys.orgIdentifier).is("org");
    expected.and(NGTriggerEntityKeys.projectIdentifier).is("proj");
    expected.and(NGTriggerEntityKeys.targetIdentifier).is("pipeline");
    expected.and(NGTriggerEntityKeys.identifier).is("trigger");
    expected.and(NGTriggerEntityKeys.deleted).is(false);
    expected.and(NGTriggerEntityKeys.type).is(NGTriggerType.WEBHOOK);
    expected.and(NGTriggerEntityKeys.enabled).is(true);
    expected.and("metadata.webhook.type").regex("CUSTOM", CASE_INSENSITIVE_MONGO_OPTIONS);
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId("acc")
                                                  .orgIdentifier("org")
                                                  .projectIdentifier("proj")
                                                  .pipelineIdentifier("pipeline")
                                                  .triggerIdentifier("trigger")
                                                  .build();
    assertThat(TriggerFilterHelper.createCriteriaForCustomWebhookTriggerGetList(triggerWebhookEvent, null, false, true)
                   .toString())
        .isEqualTo(expected.toString());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCreateCriteriaFormWebhookTriggerGetListByRepoType() {
    Criteria expected = new Criteria();
    expected.and(NGTriggerEntityKeys.accountId).is("acc");
    expected.and(NGTriggerEntityKeys.orgIdentifier).is("org");
    expected.and(NGTriggerEntityKeys.projectIdentifier).is("proj");
    expected.and(NGTriggerEntityKeys.deleted).is(false);
    expected.and(NGTriggerEntityKeys.enabled).is(true);
    expected.and(NGTriggerEntityKeys.type).is(NGTriggerType.WEBHOOK);
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId("acc")
                                                  .orgIdentifier("org")
                                                  .projectIdentifier("proj")
                                                  .pipelineIdentifier("pipeline")
                                                  .triggerIdentifier("trigger")
                                                  .sourceRepoType("repoType")
                                                  .build();
    expected.and("metadata.webhook.type").regex("repoType", CASE_INSENSITIVE_MONGO_OPTIONS);
    assertThat(
        TriggerFilterHelper.createCriteriaFormWebhookTriggerGetListByRepoType(triggerWebhookEvent, null, false, true)
            .toString())
        .isEqualTo(expected.toString());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCreateCriteriaFormBuildTriggerUsingAccIdAndSignature() {
    List<String> signatures = List.of("sig");
    Criteria expected = new Criteria();
    expected.and(NGTriggerEntityKeys.accountId).is("acc");
    expected.and("metadata.buildMetadata.pollingConfig.signature").in(signatures);
    expected.and(NGTriggerEntityKeys.deleted).is(false);
    expected.and(NGTriggerEntityKeys.enabled).is(true);
    assertThat(TriggerFilterHelper.createCriteriaFormBuildTriggerUsingAccIdAndSignature("acc", signatures).toString())
        .isEqualTo(expected.toString());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetUpdateOperations() {
    NGTriggerEntity triggerEntity = NGTriggerEntity.builder()
                                        .name("name")
                                        .identifier("id")
                                        .description("desc")
                                        .yaml("yaml")
                                        .lastModifiedAt(1l)
                                        .type(NGTriggerType.WEBHOOK)
                                        .metadata(null)
                                        .enabled(true)
                                        .tags(Collections.emptyList())
                                        .triggerStatus(TriggerStatus.builder().build())
                                        .pollInterval("5m")
                                        .webhookId("123")
                                        .encryptedWebhookSecretIdentifier("secret")
                                        .stagesToExecute(List.of("stage1"))
                                        .nextIterations(List.of(1l))
                                        .build();
    Update update = TriggerFilterHelper.getUpdateOperations(triggerEntity);
    Update expected = new Update();
    expected.set(NGTriggerEntityKeys.name, "name");
    expected.set(NGTriggerEntityKeys.identifier, "id");
    expected.set(NGTriggerEntityKeys.description, "desc");
    expected.set(NGTriggerEntityKeys.yaml, "yaml");
    expected.set(NGTriggerEntityKeys.lastModifiedAt,
        ((Document) update.getUpdateObject().get("$set")).get(NGTriggerEntityKeys.lastModifiedAt));
    expected.set(NGTriggerEntityKeys.type, NGTriggerType.WEBHOOK);
    expected.set(NGTriggerEntityKeys.metadata, null);
    expected.set(NGTriggerEntityKeys.enabled, true);
    expected.set(NGTriggerEntityKeys.tags, Collections.emptyList());
    expected.set(NGTriggerEntityKeys.deleted, false);
    expected.set(NGTriggerEntityKeys.triggerStatus, TriggerStatus.builder().build());
    expected.set(NGTriggerEntityKeys.pollInterval, "5m");
    expected.set(NGTriggerEntityKeys.webhookId, "123");
    expected.set(NGTriggerEntityKeys.encryptedWebhookSecretIdentifier, "secret");
    expected.set(NGTriggerEntityKeys.stagesToExecute, List.of("stage1"));
    expected.set(NGTriggerEntityKeys.nextIterations, List.of(1l));
    assertThat(update.toString()).isEqualTo(expected.toString());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetUpdateOperationsForEvent() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder().attemptCount(1).processing(true).build();
    Update expected = new Update();
    expected.set(TriggerWebhookEventsKeys.attemptCount, 1);
    expected.set(TriggerWebhookEventsKeys.processing, true);
    assertThat(TriggerFilterHelper.getUpdateOperations(triggerWebhookEvent).toString()).isEqualTo(expected.toString());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetUpdateOperationsForDelete() {
    Update expected = new Update();
    expected.set(NGTriggerEntityKeys.deleted, true);
    expected.set(NGTriggerEntityKeys.enabled, false);
    assertThat(TriggerFilterHelper.getUpdateOperationsForDelete().toString()).isEqualTo(expected.toString());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCreateCriteriaForTriggerEventCountLastNDays() {
    Criteria expected = new Criteria();
    expected.and(TriggerEventHistoryKeys.accountId).is("acc");
    expected.and(TriggerEventHistoryKeys.orgIdentifier).is("org");
    expected.and(TriggerEventHistoryKeys.projectIdentifier).is("proj");
    expected.and(TriggerEventHistoryKeys.triggerIdentifier).is("trigger");
    expected.and(TriggerEventHistoryKeys.targetIdentifier).is("pipeline");
    expected.and(TriggerEventHistoryKeys.createdAt).gte(1l);
    assertThat(
        TriggerFilterHelper.createCriteriaForTriggerEventCountLastNDays("acc", "org", "proj", "trigger", "pipeline", 1l)
            .toString())
        .isEqualTo(expected.toString());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetWebhookTriggerFilters() {
    TriggerWebhookEventBuilder originalEventBuilder = TriggerWebhookEvent.builder().sourceRepoType("CUSTOM");
    WebhookPayloadDataBuilder webhookPayloadDataBuilder = WebhookPayloadData.builder();

    List<TriggerFilter> webhookTriggerFilters = triggerFilterStore.getWebhookTriggerFilters(
        webhookPayloadDataBuilder.originalEvent(originalEventBuilder.build()).build());
    assertThat(webhookTriggerFilters).isNotNull();
    assertThat(webhookTriggerFilters)
        .containsExactlyInAnyOrder(accountCustomTriggerFilter, payloadConditionsTriggerFilter, headerTriggerFilter,
            jexlConditionsTriggerFilter);

    TriggerFilter[] triggerFiltersDefaultGit = new TriggerFilter[] {accountTriggerFilter, sourceRepoTypeTriggerFilter,
        eventActionTriggerFilter, payloadConditionsTriggerFilter, headerTriggerFilter, jexlConditionsTriggerFilter,
        gitWebhookTriggerRepoFilter, filepathTriggerFilter};

    webhookTriggerFilters = triggerFilterStore.getWebhookTriggerFilters(
        webhookPayloadDataBuilder.parseWebhookResponse(ParseWebhookResponse.newBuilder().build())
            .originalEvent(originalEventBuilder.sourceRepoType("GITLAB").build())
            .build());
    assertThat(webhookTriggerFilters).isNotNull();
    assertThat(webhookTriggerFilters).containsExactlyInAnyOrder(triggerFiltersDefaultGit);

    webhookTriggerFilters = triggerFilterStore.getWebhookTriggerFilters(
        webhookPayloadDataBuilder.parseWebhookResponse(ParseWebhookResponse.newBuilder().build())
            .originalEvent(originalEventBuilder.sourceRepoType("BITBUCKET").build())
            .build());
    assertThat(webhookTriggerFilters).isNotNull();
    assertThat(webhookTriggerFilters).containsExactlyInAnyOrder(triggerFiltersDefaultGit);

    webhookTriggerFilters = triggerFilterStore.getWebhookTriggerFilters(
        webhookPayloadDataBuilder.parseWebhookResponse(ParseWebhookResponse.newBuilder().build())
            .originalEvent(originalEventBuilder.sourceRepoType("GITHUB").build())
            .build());
    assertThat(webhookTriggerFilters).isNotNull();
    assertThat(webhookTriggerFilters).containsExactlyInAnyOrder(triggerFiltersDefaultGit);

    webhookTriggerFilters = triggerFilterStore.getWebhookTriggerFilters(
        webhookPayloadDataBuilder
            .parseWebhookResponse(
                ParseWebhookResponse.newBuilder()
                    .setComment(
                        IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().setNumber(1).build()).build())
                            .build())
                    .build())
            .originalEvent(originalEventBuilder.sourceRepoType("GITHUB").build())
            .build());
    assertThat(webhookTriggerFilters).isNotNull();
    assertThat(webhookTriggerFilters)
        .containsExactlyInAnyOrder(accountTriggerFilter, sourceRepoTypeTriggerFilter, eventActionTriggerFilter,
            gitWebhookTriggerRepoFilter, headerTriggerFilter, githubIssueCommentTriggerFilter, filepathTriggerFilter);

    webhookTriggerFilters = triggerFilterStore.getWebhookTriggerFilters(
        webhookPayloadDataBuilder
            .parseWebhookResponse(
                ParseWebhookResponse.newBuilder()
                    .setComment(
                        IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().setNumber(1).build()).build())
                            .build())
                    .build())
            .originalEvent(originalEventBuilder.sourceRepoType("GITLAB").build())
            .build());
    assertThat(webhookTriggerFilters).isNotNull();
    assertThat(webhookTriggerFilters)
        .containsExactlyInAnyOrder(accountTriggerFilter, sourceRepoTypeTriggerFilter, eventActionTriggerFilter,
            gitWebhookTriggerRepoFilter, headerTriggerFilter, gitlabMRCommentTriggerFilter, filepathTriggerFilter);

    webhookTriggerFilters = triggerFilterStore.getWebhookTriggerFilters(
        webhookPayloadDataBuilder
            .parseWebhookResponse(
                ParseWebhookResponse.newBuilder()
                    .setComment(
                        IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().setNumber(1).build()).build())
                            .build())
                    .build())
            .originalEvent(originalEventBuilder.sourceRepoType("BITBUCKET").build())
            .build());
    assertThat(webhookTriggerFilters).isNotNull();
    assertThat(webhookTriggerFilters)
        .containsExactlyInAnyOrder(accountTriggerFilter, sourceRepoTypeTriggerFilter, eventActionTriggerFilter,
            gitWebhookTriggerRepoFilter, headerTriggerFilter, bitbucketPRCommentTriggerFilter, filepathTriggerFilter);

    webhookTriggerFilters = triggerFilterStore.getWebhookTriggerFilters(
        webhookPayloadDataBuilder
            .parseWebhookResponse(
                ParseWebhookResponse.newBuilder()
                    .setComment(
                        IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().setNumber(1).build()).build())
                            .build())
                    .build())
            .originalEvent(originalEventBuilder.sourceRepoType("AZURE_REPO").build())
            .build());
    assertThat(webhookTriggerFilters).isNotNull();
    assertThat(webhookTriggerFilters)
        .containsExactlyInAnyOrder(accountTriggerFilter, sourceRepoTypeTriggerFilter, eventActionTriggerFilter,
            gitWebhookTriggerRepoFilter, headerTriggerFilter, jexlConditionsTriggerFilter, issueCommentTriggerFilter,
            filepathTriggerFilter);
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testGetCronTriggerUpdateOperations() {
    NGTriggerEntity updateEntity = NGTriggerEntity.builder()
                                       .accountId("accountId")
                                       .name("name")
                                       .identifier("identifier")
                                       .description("description")
                                       .nextIterations(Arrays.asList(1L, 2L, 3L, 4L))
                                       .build();
    assertThat(TriggerFilterHelper.getUpdateOperations(updateEntity).modifies(NGTriggerEntityKeys.nextIterations))
        .isTrue();
  }
}
