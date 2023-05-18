/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.webhook;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class WebhookHelper {
  public static final String WEBHOOK_ENDPOINT = "webhook";

  public String generateWebhookUrl(WebhookConfigProvider webhookConfigProvider, String accountId) {
    String webhookBaseUrl = webhookConfigProvider.getWebhookApiBaseUrl();
    if (isBlank(webhookBaseUrl)) {
      return null;
    }

    StringBuilder urlBuilder = new StringBuilder(128);
    if (webhookBaseUrl.endsWith("#")) {
      urlBuilder.append(webhookBaseUrl.substring(0, webhookBaseUrl.length() - 1));
    } else {
      urlBuilder.append(webhookBaseUrl);
    }

    if (!urlBuilder.toString().endsWith("/")) {
      urlBuilder.append('/');
    }

    StringBuilder webhookUrl = new StringBuilder(urlBuilder)
                                   .append(WEBHOOK_ENDPOINT)
                                   .append('?')
                                   .append(NGCommonEntityConstants.ACCOUNT_KEY)
                                   .append('=')
                                   .append(accountId);
    return webhookUrl.toString();
  }

  public String generateCustomWebhookUrl(WebhookConfigProvider webhookConfigProvider, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String triggerIdentifier,
      String customWebhookToken) {
    String webhookUrl = webhookConfigProvider.getCustomApiBaseUrl();
    if (isBlank(webhookUrl)) {
      return null;
    }

    StringBuilder urlBuilder = new StringBuilder(128);
    if (webhookUrl.endsWith("#")) {
      urlBuilder.append(webhookUrl.substring(0, webhookUrl.length() - 1));
    } else {
      urlBuilder.append(webhookUrl);
    }

    if (!urlBuilder.toString().endsWith("/")) {
      urlBuilder.append('/');
    }

    if (customWebhookToken == null) {
      // This will change to common endpoint that will be used by all
      urlBuilder.append("webhook/custom/v2?accountIdentifier=")
          .append(accountId)
          .append("&orgIdentifier=")
          .append(orgIdentifier)
          .append("&projectIdentifier=")
          .append(projectIdentifier)
          .append("&pipelineIdentifier=")
          .append(pipelineIdentifier)
          .append("&triggerIdentifier=")
          .append(triggerIdentifier);
    } else {
      urlBuilder.append("webhook/custom/")
          .append(customWebhookToken)
          .append("/v3?accountIdentifier=")
          .append(accountId)
          .append("&orgIdentifier=")
          .append(orgIdentifier)
          .append("&projectIdentifier=")
          .append(projectIdentifier)
          .append("&pipelineIdentifier=")
          .append(pipelineIdentifier)
          .append("&triggerIdentifier=")
          .append(triggerIdentifier);
    }
    return urlBuilder.toString();
  }

  public String generateCustomWebhookCurlCommand(String webhookUrl, boolean mandatoryAuth) {
    if (mandatoryAuth) {
      return String.format(
          "curl -X POST -H 'content-type: application/json' -H 'X-Api-Key: sample_api_key' --url '%s' -d '{\"sample_key\": \"sample_value\"}'",
          webhookUrl);
    } else {
      return String.format(
          "curl -X POST -H 'content-type: application/json' --url '%s' -d '{\"sample_key\": \"sample_value\"}'",
          webhookUrl);
    }
  }
}
