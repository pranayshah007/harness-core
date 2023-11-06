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

import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.WebhookPayload;
import io.harness.ngtriggers.beans.dto.WebhookEventHeaderData;
import io.harness.ngtriggers.beans.dto.WebhookEventHeaderData.WebhookEventHeaderDataBuilder;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.BranchHook;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.GitProvider;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Signature;
import io.harness.service.WebhookParserSCMService;
import io.harness.utils.PmsFeatureFlagHelper;

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
  PmsFeatureFlagHelper pmsFeatureFlagHelper;

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
    parseWebhookResponse = overrideParseWebhookResponse(triggerWebhookEvent, parseWebhookResponse);
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

  private ParseWebhookResponse overrideParseWebhookResponse(
      TriggerWebhookEvent triggerWebhookEvent, ParseWebhookResponse parseWebhookResponse) {
    if (parseWebhookResponse.hasBranch() && Action.CREATE.equals(parseWebhookResponse.getBranch().getAction())
        && GitProvider.STASH.equals(webhookParserSCMService.obtainWebhookSource(triggerWebhookEvent.getHeaders()))) {
      String accountId = triggerWebhookEvent.getAccountId();
      log.info("Received BitBucket on-prem branchHook with action CREATE for accountId: {}", accountId);
      if (pmsFeatureFlagHelper.isEnabled(
              accountId, FeatureName.CDS_NG_CONVERT_BRANCH_TO_PUSH_WEBHOOK_BITBUCKET_ON_PREM)) {
        /*
           For BitBucket On-Prem (which has GitProvider = STASH), whenever we create/push to a new branch,
           we don't receive any `push` webhooks, but rather just a `branch` webhook with action CREATE.
           For other providers, we always receive a `push` webhook in this case.
           This means that BitBucket on-prem push triggers would not fire on the first push to a new branch. This
           behavior is inconsistent with triggers for other providers.

           As a workaround, here we convert on a best-effort basis the BitBucket on-prem's BranchHook to a PushHook,
           which makes trigger's behavior consistent across providers.
        */
        parseWebhookResponse = convertBranchToPushResponseForBitBucketOnPrem(parseWebhookResponse);
      }
    }
    return parseWebhookResponse;
  }

  private ParseWebhookResponse convertBranchToPushResponseForBitBucketOnPrem(
      ParseWebhookResponse parseWebhookResponse) {
    BranchHook branchHook = parseWebhookResponse.getBranch();
    Signature author = Signature.newBuilder()
                           .setEmail(branchHook.getSender().getEmail())
                           .setLogin(branchHook.getSender().getLogin())
                           .setName(branchHook.getSender().getName())
                           .setAvatar(branchHook.getSender().getAvatar())
                           .build();
    Commit commit = Commit.newBuilder().setSha(branchHook.getRef().getSha()).setAuthor(author).build();
    return ParseWebhookResponse.newBuilder()
        .setPush(PushHook.newBuilder()
                     .setSender(branchHook.getSender())
                     .setRepo(branchHook.getRepo())
                     .setRef(branchHook.getRef().getName())
                     .setAfter(branchHook.getRef().getSha())
                     .setCommit(Commit.newBuilder().setAuthor(author).build())
                     .addCommits(commit)
                     .build())
        .build();
  }
}
