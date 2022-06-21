/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.CUSTOM;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.GITHUB;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.AccountCustomTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.AccountTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.EventActionTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.FilepathTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.GitWebhookTriggerRepoFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.GithubIssueCommentTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.HeaderTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.JexlConditionsTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.PayloadConditionsTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.SourceRepoTypeTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.buildtrigger.BuildTriggerEventConditionsFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.buildtrigger.BuildTriggerSignatureFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.buildtrigger.BuildTriggerValidationFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class TriggerFilterStore {
  private final GitWebhookTriggerRepoFilter gitWebhookTriggerRepoFilter;
  private final AccountTriggerFilter accountTriggerFilter;
  private final AccountCustomTriggerFilter accountCustomTriggerFilter;
  private final SourceRepoTypeTriggerFilter sourceRepoTypeTriggerFilter;
  private final EventActionTriggerFilter eventActionTriggerFilter;
  private final PayloadConditionsTriggerFilter payloadConditionsTriggerFilter;
  private final GithubIssueCommentTriggerFilter githubIssueCommentTriggerFilter;
  private final HeaderTriggerFilter headerTriggerFilter;
  private final JexlConditionsTriggerFilter jexlConditionsTriggerFilter;
  private final FilepathTriggerFilter filepathTriggerFilter;
  private final BuildTriggerEventConditionsFilter buildConditionsTriggerFilter;
  private final BuildTriggerValidationFilter buildTriggerValidationFilter;
  private final BuildTriggerSignatureFilter buildTriggerSignatureFilter;

  public List<TriggerFilter> getWebhookTriggerFilters(WebhookPayloadData webhookPayloadData) {
    if (CUSTOM.name().equals(webhookPayloadData.getOriginalEvent().getSourceRepoType())) {
      return Arrays.asList(
          accountCustomTriggerFilter, payloadConditionsTriggerFilter, headerTriggerFilter, jexlConditionsTriggerFilter);
    }

    // When it github and comment on a pr event
    // webhookPayloadData.getParseWebhookResponse().getComment().getIssue().getPr() will be null,
    // when its comment on the issue
    if (webhookPayloadData.getParseWebhookResponse().hasComment()
        && webhookPayloadData.getParseWebhookResponse().getComment().getIssue() != null
        && webhookPayloadData.getParseWebhookResponse().getComment().getIssue().getPr() != null
        && GITHUB.name().equals(webhookPayloadData.getOriginalEvent().getSourceRepoType())) {
      return getTriggerFiltersGithubIssueCommentList();
    }

    return getWebhookGitTriggerFiltersDefaultList();
  }

  public List<TriggerFilter> getBuildTriggerFiltersDefaultList() {
    return Arrays.asList(buildTriggerSignatureFilter, buildConditionsTriggerFilter, buildTriggerValidationFilter);
  }

  List<TriggerFilter> getWebhookGitTriggerFiltersDefaultList() {
    return Arrays.asList(accountTriggerFilter, sourceRepoTypeTriggerFilter, eventActionTriggerFilter,
        payloadConditionsTriggerFilter, headerTriggerFilter, jexlConditionsTriggerFilter, gitWebhookTriggerRepoFilter,
        filepathTriggerFilter);
  }

  List<TriggerFilter> getTriggerFiltersGithubIssueCommentList() {
    return Arrays.asList(accountTriggerFilter, sourceRepoTypeTriggerFilter, eventActionTriggerFilter,
        headerTriggerFilter, gitWebhookTriggerRepoFilter, githubIssueCommentTriggerFilter, filepathTriggerFilter);
  }
}
