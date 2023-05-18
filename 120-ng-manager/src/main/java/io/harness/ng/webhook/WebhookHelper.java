/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.X_AMZ_SNS_MESSAGE_TYPE;
import static io.harness.constants.Constants.X_BIT_BUCKET_EVENT;
import static io.harness.constants.Constants.X_GIT_HUB_EVENT;
import static io.harness.constants.Constants.X_GIT_LAB_EVENT;
import static io.harness.constants.Constants.X_HARNESS_TRIGGER;
import static io.harness.constants.Constants.X_VSS_HEADER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_BRANCH_HOOK_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PR_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PUSH_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.AWS_CODECOMMIT;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.AZURE;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.BITBUCKET;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.GITHUB;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.GITLAB;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.HARNESS;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.UNRECOGNIZED;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.CREATE_BRANCH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.DELETE_BRANCH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.ISSUE_COMMENT;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PR;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PUSH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.RELEASE;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType.CUSTOM;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType.GIT;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.webhookpayloads.webhookdata.EventHeader;
import io.harness.eventsframework.webhookpayloads.webhookdata.GitDetails;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.entities.WebhookEvent.WebhookEventBuilder;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class WebhookHelper {
  @Inject @Named(WEBHOOK_EVENTS_STREAM) private Producer webhookEventProducer;
  @Inject @Named(GIT_PUSH_EVENT_STREAM) private Producer gitPushEventProducer;
  @Inject @Named(GIT_PR_EVENT_STREAM) private Producer gitPrEventProducer;
  @Inject @Named(GIT_BRANCH_HOOK_EVENT_STREAM) private Producer gitBranchHookEventProducer;

  public WebhookEvent toNGTriggerWebhookEvent(
      String accountIdentifier, String payload, MultivaluedMap<String, String> httpHeaders) {
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.forEach((k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));

    WebhookEventBuilder webhookEventBuilder =
        WebhookEvent.builder().accountId(accountIdentifier).headers(headerConfigs).payload(payload);

    return webhookEventBuilder.build();
  }

  public boolean containsHeaderKey(Map<String, List<String>> headers, String key) {
    Set<String> headerKeys = headers.keySet();
    if (isEmpty(headerKeys) || isBlank(key)) {
      return false;
    }

    return headerKeys.contains(key) || headerKeys.contains(key.toLowerCase())
        || headerKeys.stream().anyMatch(key::equalsIgnoreCase);
  }

  public WebhookDTO generateWebhookDTO(
      WebhookEvent event, ParseWebhookResponse parseWebhookResponse, SourceRepoType sourceRepoType) {
    WebhookDTO.Builder builder = WebhookDTO.newBuilder()
                                     .setJsonPayload(event.getPayload())
                                     .addAllHeaders(generateEventHeaders(event))
                                     .setAccountId(event.getAccountId())
                                     .setEventId(event.getUuid())
                                     .setTime(event.getCreatedAt());

    if (parseWebhookResponse == null) {
      builder.setWebhookTriggerType(CUSTOM);
    } else {
      GitDetails gitDetails = generateGitDetails(parseWebhookResponse, sourceRepoType);
      builder.setParsedResponse(parseWebhookResponse)
          .setWebhookTriggerType(GIT)
          .setWebhookEventType(gitDetails.getEvent())
          .setGitDetails(gitDetails);
    }

    return builder.build();
  }

  private List<EventHeader> generateEventHeaders(WebhookEvent event) {
    return event.getHeaders()
        .stream()
        .map(headerConfig
            -> EventHeader.newBuilder().setKey(headerConfig.getKey()).addAllValues(headerConfig.getValues()).build())
        .collect(toList());
  }

  public GitDetails generateGitDetails(ParseWebhookResponse parseWebhookResponse, SourceRepoType sourceRepoType) {
    GitDetails.Builder builder = GitDetails.newBuilder().setSourceRepoType(sourceRepoType);
    if (parseWebhookResponse.hasPush()) {
      builder.setEvent(PUSH);
    } else if (parseWebhookResponse.hasPr()) {
      builder.setEvent(PR);
    } else if (parseWebhookResponse.hasComment()) {
      builder.setEvent(ISSUE_COMMENT);
    } else if (parseWebhookResponse.hasBranch()) {
      if (parseWebhookResponse.getBranch().getAction() == Action.CREATE) {
        builder.setEvent(CREATE_BRANCH);
      } else if (parseWebhookResponse.getBranch().getAction() == Action.DELETE) {
        builder.setEvent(DELETE_BRANCH);
      }
    } else if (parseWebhookResponse.hasRelease()) {
      builder.setEvent(RELEASE);
    }

    return builder.build();
  }

  public SourceRepoType getSourceRepoType(WebhookEvent event) {
    Map<String, List<String>> headers =
        event.getHeaders().stream().collect(Collectors.toMap(HeaderConfig::getKey, HeaderConfig::getValues));

    SourceRepoType sourceRepoType = UNRECOGNIZED;
    if (containsHeaderKey(headers, X_GIT_HUB_EVENT)) {
      sourceRepoType = GITHUB;
    } else if (containsHeaderKey(headers, X_GIT_LAB_EVENT)) {
      sourceRepoType = GITLAB;
    } else if (containsHeaderKey(headers, X_BIT_BUCKET_EVENT)) {
      sourceRepoType = BITBUCKET;
    } else if (containsHeaderKey(headers, X_AMZ_SNS_MESSAGE_TYPE)) {
      sourceRepoType = AWS_CODECOMMIT;
    } else if (containsHeaderKey(headers, X_VSS_HEADER)) {
      sourceRepoType = AZURE;
    } else if (containsHeaderKey(headers, X_HARNESS_TRIGGER)) {
      sourceRepoType = HARNESS;
    } else {
      log.info("Got unrecognized source repo type for the webhook {}", event.getUuid());
    }

    return sourceRepoType;
  }

  public List<Producer> getProducerListForEvent(WebhookDTO webhookDTO) {
    List<Producer> producers = new ArrayList<>();
    producers.add(webhookEventProducer);

    if (webhookDTO.hasParsedResponse() && webhookDTO.hasGitDetails()) {
      if (PUSH == webhookDTO.getGitDetails().getEvent()) {
        producers.add(gitPushEventProducer);
      } else if (PR == webhookDTO.getGitDetails().getEvent()) {
        producers.add(gitPrEventProducer);
      } else if (CREATE_BRANCH == webhookDTO.getGitDetails().getEvent()
          || DELETE_BRANCH == webhookDTO.getGitDetails().getEvent()) {
        producers.add(gitBranchHookEventProducer);
      }

      // Here we can add more logic if need to add more event topics.
    }

    return producers;
  }
}
