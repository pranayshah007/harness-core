/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.EXCEPTION_WHILE_PROCESSING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.service.WebhookParserSCMService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(CI)
public class GitlabIssueCommentTriggerFilter implements TriggerFilter {
    private PayloadConditionsTriggerFilter payloadConditionsTriggerFilter;
    private WebhookParserSCMService webhookParserSCMService;

    @Override
    public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
        WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);

        try {
            filterRequestData.setWebhookPayloadData(
                    generateUpdateWebhookPayloadDataWithPrHook(filterRequestData, mappingResponseBuilder));
        } catch (Exception e) {
            String errorMsg = new StringBuilder(128)
                    .append("Failed while updating Webhook payload with PR details for IssueComment event. ")
                    .append("Account : ")
                    .append(filterRequestData.getAccountId())
                    .append(", with Exception")
                    .append(e.getMessage())
                    .toString();
            log.error(errorMsg);
            return mappingResponseBuilder.failedToFindTrigger(true)
                    .webhookEventResponse(TriggerEventResponseHelper.toResponse(EXCEPTION_WHILE_PROCESSING,
                            filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
                            "Failed to update Webhook payload with PR Details: " + e, null))
                    .build();
        }

        return payloadConditionsTriggerFilter.applyFilter(filterRequestData);
    }

    private WebhookPayloadData generateUpdateWebhookPayloadDataWithPrHook(FilterRequestData filterRequestData,
                                                                          WebhookEventMappingResponseBuilder mappingResponseBuilder) throws Exception {
        ParseWebhookResponse originalParseWebhookResponse =
                filterRequestData.getWebhookPayloadData().getParseWebhookResponse();

        PullRequest pullRequest = originalParseWebhookResponse.getComment().getIssue().getPr();
        PullRequestHook pullRequestHook = PullRequestHook.newBuilder()
                .setRepo(originalParseWebhookResponse.getComment().getRepo())
                .setSender(originalParseWebhookResponse.getComment().getSender())
                .setPr(pullRequest)
                .build();

        ParseWebhookResponse newParseWebhookResponse =
                ParseWebhookResponse.newBuilder(originalParseWebhookResponse).setPr(pullRequestHook).build();

        mappingResponseBuilder.parseWebhookResponse(newParseWebhookResponse);
        WebhookPayloadData originalWebhookPayloadData = filterRequestData.getWebhookPayloadData();

        return WebhookPayloadData.builder()
                .repository(originalWebhookPayloadData.getRepository())
                .originalEvent(originalWebhookPayloadData.getOriginalEvent())
                .webhookGitUser(originalWebhookPayloadData.getWebhookGitUser())
                .parseWebhookResponse(newParseWebhookResponse)
                .webhookEvent(webhookParserSCMService.convertPRWebhookEvent(pullRequestHook))
                .build();
    }
}
