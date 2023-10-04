/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.annotations.dev.HarnessTeam.CV;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.pagerduty.PagerDutyRegisterWebhookRequest;
import io.harness.cvng.beans.pagerduty.PagerDutyServiceDetail;
import io.harness.cvng.beans.pagerduty.PagerDutyServicesRequest;
import io.harness.cvng.beans.pagerduty.PagerdutyDeleteWebhookRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.PagerDutyWebhook;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.PagerDutyService;
import io.harness.cvng.core.services.api.WebhookService;
import io.harness.serializer.JsonUtils;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.lang.reflect.Type;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CV)
@Slf4j
public class PagerDutyServiceImpl implements PagerDutyService {
  @Inject private OnboardingService onboardingService;
  @Inject private WebhookService webhookService;
  @Inject @Named("portalUrl") String portalUrl;

  @Override
  public List<PagerDutyServiceDetail> getPagerDutyServices(
      ProjectParams projectParams, String connectorIdentifier, String requestGuid, String query) {
    DataCollectionRequest request = PagerDutyServicesRequest.builder().query(query).build();
    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(projectParams.getAccountIdentifier())
                                                    .tracingId(requestGuid)
                                                    .orgIdentifier(projectParams.getOrgIdentifier())
                                                    .projectIdentifier(projectParams.getProjectIdentifier())
                                                    .build();

    OnboardingResponseDTO response =
        onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<List<PagerDutyServiceDetail>>() {}.getType();
    return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
  }

  @Override
  public void registerPagerDutyWebhook(
      MonitoredServiceParams monitoredServiceParams, PagerDutyChangeSource pagerDutyChangeSource) {
    String token = randomAlphabetic(20);
    String url = portalUrl.concat("cv/api/webhook/pagerduty/")
                     .concat(token)
                     .concat("?routingId=")
                     .concat(monitoredServiceParams.getAccountIdentifier())
                     .concat("&accountId=")
                     .concat(monitoredServiceParams.getAccountIdentifier());
    DataCollectionRequest request = PagerDutyRegisterWebhookRequest.builder()
                                        .type(DataCollectionRequestType.PAGERDUTY_REGISTER_WEBHOOK)
                                        .url(url)
                                        .pagerDutyServiceId(pagerDutyChangeSource.getPagerDutyServiceId())
                                        .build();
    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(pagerDutyChangeSource.getConnectorIdentifier())
                                                    .accountId(monitoredServiceParams.getAccountIdentifier())
                                                    .tracingId("pagerduty_register_" + randomAlphabetic(20))
                                                    .orgIdentifier(monitoredServiceParams.getOrgIdentifier())
                                                    .projectIdentifier(monitoredServiceParams.getProjectIdentifier())
                                                    .build();

    OnboardingResponseDTO response =
        onboardingService.getOnboardingResponse(monitoredServiceParams.getAccountIdentifier(), onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<String>() {}.getType();
    String webhookId = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    webhookService.createPagerdutyWebhook(
        monitoredServiceParams, token, webhookId, pagerDutyChangeSource.getIdentifier());
  }

  @Override
  public void deletePagerdutyWebhook(ProjectParams projectParams, PagerDutyChangeSource pagerDutyChangeSource) {
    PagerDutyWebhook pagerDutyWebhook =
        webhookService.getPagerdutyWebhook(projectParams, pagerDutyChangeSource.getIdentifier());
    DataCollectionRequest request = PagerdutyDeleteWebhookRequest.builder()
                                        .type(DataCollectionRequestType.PAGERDUTY_DELETE_WEBHOOK)
                                        .webhookId(pagerDutyWebhook.getWebhookId())
                                        .build();
    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(pagerDutyChangeSource.getConnectorIdentifier())
                                                    .accountId(projectParams.getAccountIdentifier())
                                                    .tracingId("pagerduty_deregister_" + randomAlphabetic(20))
                                                    .orgIdentifier(projectParams.getOrgIdentifier())
                                                    .projectIdentifier(projectParams.getProjectIdentifier())
                                                    .build();

    onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);
    webhookService.deleteWebhook(pagerDutyWebhook);
  }
}
