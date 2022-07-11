/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.constants.Constants.X_BIT_BUCKET_EVENT;
import static io.harness.constants.Constants.X_GIT_HUB_EVENT;
import static io.harness.constants.Constants.X_GIT_LAB_EVENT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.beans.HeaderConfig;
import io.harness.beans.WebhookPayload;
import io.harness.ngtriggers.beans.dto.WebhookEventHeaderData;
import io.harness.ngtriggers.beans.dto.WebhookEventHeaderData.WebhookEventHeaderDataBuilder;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.service.WebhookParserSCMService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class WebhookEventPayloadParser {
  WebhookParserSCMService webhookParserSCMService;

  public WebhookPayloadData parseEvent(TriggerWebhookEvent triggerWebhookEvent) {
    ParseWebhookResponse parseWebhookResponse = invokeScmService(triggerWebhookEvent);
    return convertWebhookResponse(parseWebhookResponse, triggerWebhookEvent);
  }

  public ParseWebhookResponse invokeScmService(TriggerWebhookEvent triggerWebhookEvent) {
    return webhookParserSCMService.parseWebhookUsingSCMAPI(
        triggerWebhookEvent.getHeaders(), triggerWebhookEvent.getPayload());
  }

  public WebhookPayloadData convertWebhookResponse(
      ParseWebhookResponse parseWebhookResponse, TriggerWebhookEvent triggerWebhookEvent) {
    WebhookPayload parsedPayload = webhookParserSCMService.parseWebhookPayload(parseWebhookResponse);
    return WebhookPayloadData.builder()
        .originalEvent(triggerWebhookEvent)
        .parseWebhookResponse(parseWebhookResponse)
        .webhookGitUser(parsedPayload.getWebhookGitUser())
        .repository(parsedPayload.getRepository())
        .webhookEvent(parsedPayload.getWebhookEvent())
        .build();
  }

  public boolean containsHeaderKey(Map<String, List<String>> headers, String key) {
    Set<String> headerKeys = headers.keySet();
    if (isEmpty(headerKeys) || isBlank(key)) {
      return false;
    }

    return headerKeys.contains(key) || headerKeys.contains(key.toLowerCase())
        || headerKeys.stream().anyMatch(key::equalsIgnoreCase);
  }

  public List<String> getHeaderValue(Map<String, List<String>> headers, String key) {
    Set<String> headerKeys = headers.keySet();
    if (isEmpty(headerKeys) || isBlank(key)) {
      return Collections.emptyList();
    }
    Optional<String> caseInsensitiveKey = headerKeys.stream().filter(key::equalsIgnoreCase).findFirst();
    if (caseInsensitiveKey.isPresent()) {
      return headers.get(caseInsensitiveKey.get());
    } else {
      return Collections.emptyList();
    }
  }

  public WebhookEventHeaderData obtainWebhookSourceKeyData(List<HeaderConfig> headerConfigs) {
    HeaderConfig headerConfig = headerConfigs.stream()
                                    .filter(config
                                        -> config.getKey().equalsIgnoreCase(X_GIT_HUB_EVENT)
                                            || config.getKey().equalsIgnoreCase(X_GIT_LAB_EVENT)
                                            || config.getKey().equalsIgnoreCase(X_BIT_BUCKET_EVENT))
                                    .findFirst()
                                    .orElse(null);

    WebhookEventHeaderDataBuilder builder = WebhookEventHeaderData.builder().dataFound(false);
    if (headerConfig != null) {
      return WebhookEventHeaderData.builder()
          .sourceKey(headerConfig.getKey())
          .sourceKeyVal(headerConfig.getValues())
          .dataFound(true)
          .build();
    }

    return builder.build();
  }
}
