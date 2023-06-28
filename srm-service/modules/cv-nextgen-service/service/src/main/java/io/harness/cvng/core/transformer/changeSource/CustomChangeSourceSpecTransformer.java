/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.CustomChangeSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.changeSource.CustomChangeSource;
import io.harness.cvng.core.services.api.WebhookConfigService;

import com.google.inject.Inject;

public class CustomChangeSourceSpecTransformer
    extends ChangeSourceSpecTransformer<CustomChangeSource, CustomChangeSourceSpec> {
  @Inject WebhookConfigService webhookConfigService;

  private static final String webhookSamplePayload =
      "{ \"eventIdentifier\": \"<string>\" (optional), \"user\": \"user@harness.io\", \"startTime\": timeInMs, \"endTime\": timeInMs, \"eventDetail\": { \"description\": \"<String>\", \"changeEventDetailsLink\": \"urlString\" (optional), \"externalLinkToEntity\": \"urlString\" (optional), \"name\": \"changeEventName\" } }";

  @Override
  public CustomChangeSource getEntity(MonitoredServiceParams monitoredServiceParams, ChangeSourceDTO changeSourceDTO) {
    return CustomChangeSource.builder()
        .accountId(monitoredServiceParams.getAccountIdentifier())
        .orgIdentifier(monitoredServiceParams.getOrgIdentifier())
        .projectIdentifier(monitoredServiceParams.getProjectIdentifier())
        .monitoredServiceIdentifier(monitoredServiceParams.getMonitoredServiceIdentifier())
        .identifier(changeSourceDTO.getIdentifier())
        .name(changeSourceDTO.getName())
        .enabled(changeSourceDTO.isEnabled())
        .type(changeSourceDTO.getType())
        .build();
  }

  @Override
  protected CustomChangeSourceSpec getSpec(CustomChangeSource changeSource) {
    return CustomChangeSourceSpec.builder()
        .name(changeSource.getName())
        .type(changeSource.getType().getChangeCategory())
        .webhookUrl(getWebhookUrl(changeSource))
        .webhookCurlCommand(getWebhookCurlCommand(changeSource))
        .build();
  }

  private String getWebhookUrl(CustomChangeSource changeSource) {
    String webhookUrl = webhookConfigService.getWebhookApiBaseUrl();
    if (isBlank(webhookUrl)) {
      return null;
    }

    StringBuilder urlBuilder = new StringBuilder(128);
    urlBuilder.append(webhookUrl);

    if (!urlBuilder.toString().endsWith("/")) {
      urlBuilder.append('/');
    }

    urlBuilder.append("account/")
        .append(changeSource.getAccountId())
        .append("/org/")
        .append(changeSource.getOrgIdentifier())
        .append("/project/")
        .append(changeSource.getProjectIdentifier());
    urlBuilder.append("/webhook/custom-change?")
        .append("monitoredServiceIdentifier=")
        .append(changeSource.getMonitoredServiceIdentifier())
        .append("&changeSourceIdentifier=")
        .append(changeSource.getIdentifier());

    return urlBuilder.toString();
  }

  private String getWebhookCurlCommand(CustomChangeSource changeSource) {
    return String.format(
        "curl -X POST -H 'content-type: application/json' -H 'X-Api-Key: sample_api_key' --url '%s' -d '%s'",
        getWebhookUrl(changeSource), webhookSamplePayload);
  }
}
