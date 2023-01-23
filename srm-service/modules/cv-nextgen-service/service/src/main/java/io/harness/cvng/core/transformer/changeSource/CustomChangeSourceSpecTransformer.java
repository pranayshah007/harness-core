/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.CustomChangeSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.changeSource.CustomChangeSource;
import io.harness.cvng.core.services.api.WebhookConfigService;

import com.google.inject.Inject;

public class CustomChangeSourceSpecTransformer
    extends ChangeSourceSpecTransformer<CustomChangeSource, CustomChangeSourceSpec> {
  @Inject WebhookConfigService webhookConfigService;

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
    return webhookConfigService.getWebhookApiBaseUrl() + "webhook/custom-change?"
        + "accountIdentifier=" + changeSource.getAccountId() + "orgIdentifier" + changeSource.getOrgIdentifier()
        + "projectIdentifier" + changeSource.getProjectIdentifier() + "monitoredServiceIdentifier"
        + changeSource.getMonitoredServiceIdentifier() + "changeSourceIdentifier" + changeSource.getIdentifier();
  }

  private String getWebhookCurlCommand(CustomChangeSource changeSource) {
    return "curl -X POST -H 'content-type: application/json' \n"
        + "-H 'X-Api-Key: sample_api_key'\n"
        + " --url " + getWebhookUrl(changeSource) + " -d \n"
        + "'{\n"
        + "   \"eventIdentifier\": \"<string>\" (optional)\n"
        + "   \"name\": \"sampleName\"\n"
        + "   \"user\": \"sampleUser\",\n"
        + "   \"startTime\": timeInMs,\n"
        + "   \"endTime\": timeInMs,\n"
        + "   \"eventDetails\": {\n"
        + "     \"description\": \"<String>\",\n"
        + "     \"changeEventDetailsLink\": \"urlString\" (optional),\n"
        + "     \"internalLinkToEntity\": \"urlString\" (optional)\n"
        + "    },\n"
        + " }'";
  }
}
