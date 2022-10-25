/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.ThirdPartyApiResponseStatus;
import io.harness.cvng.beans.newrelic.NewRelicApplication;
import io.harness.cvng.beans.newrelic.NewRelicApplicationFetchRequest;
import io.harness.cvng.beans.newrelic.NewRelicFetchSampleDataRequest;
import io.harness.cvng.beans.newrelic.NewRelicMetricPackValidationRequest;
import io.harness.cvng.core.beans.MetricPackValidationResponse;
import io.harness.cvng.core.beans.MetricPackValidationResponse.MetricValidationResponse;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.NewRelicService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.datacollection.exception.DataCollectionException;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NewRelicServiceImpl implements NewRelicService {
  private static final List<String> NEW_RELIC_ENDPOINTS =
      Arrays.asList("https://insights-api.newrelic.com/", "https://insights-api.eu.newrelic.com/");

  @Inject private OnboardingService onboardingService;

  @Override
  public List<String> getNewRelicEndpoints() {
    return NEW_RELIC_ENDPOINTS;
  }

  @Override
  public List<NewRelicApplication> getNewRelicApplications(String accountId, String connectorIdentifier,
      String orgIdentifier, String projectIdentifier, String filter, String tracingId) {
    DataCollectionRequest request = NewRelicApplicationFetchRequest.builder()
                                        .type(DataCollectionRequestType.NEWRELIC_APPS_REQUEST)
                                        .filter(filter)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .orgIdentifier(orgIdentifier)
                                                    .tracingId(tracingId)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<List<NewRelicApplication>>() {}.getType();
    return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
  }

  @Override
  public MetricPackValidationResponse validateData(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, String appName, String appId, List<MetricPackDTO> metricPacks, String tracingId) {
    try {
      DataCollectionRequest request = NewRelicMetricPackValidationRequest.builder()
                                          .type(DataCollectionRequestType.NEWRELIC_VALIDATION_REQUEST)
                                          .applicationName(appName)
                                          .applicationId(appId)
                                          .metricPackDTOSet(new HashSet(metricPacks))
                                          .build();
      OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                      .dataCollectionRequest(request)
                                                      .connectorIdentifier(connectorIdentifier)
                                                      .accountId(accountId)
                                                      .orgIdentifier(orgIdentifier)
                                                      .tracingId(tracingId)
                                                      .projectIdentifier(projectIdentifier)
                                                      .build();

      OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
      final Gson gson = new Gson();
      Type type = new TypeToken<List<MetricValidationResponse>>() {}.getType();
      List<MetricValidationResponse> metricValidationResponseList =
          gson.fromJson(JsonUtils.asJson(response.getResult()), type);
      MetricPackValidationResponse validationResponse = MetricPackValidationResponse.builder()
                                                            .overallStatus(ThirdPartyApiResponseStatus.SUCCESS)
                                                            .metricValidationResponses(metricValidationResponseList)
                                                            .metricPackName("Performance")
                                                            .build();
      validationResponse.updateStatus();
      return validationResponse;
    } catch (DataCollectionException ex) {
      return MetricPackValidationResponse.builder().overallStatus(ThirdPartyApiResponseStatus.FAILED).build();
    }
  }

  @Override
  public LinkedHashMap fetchSampleData(
      ProjectParams projectParams, String connectorIdentifier, String query, String tracingId) {
    try {
      Preconditions.checkNotNull(query);
      query = query.trim();
      Preconditions.checkState(!query.contains(" SINCE "),
          "Query should not contain any time duration. Please remove SINCE or any time related keywords");
      Preconditions.checkState(query.endsWith("TIMESERIES"), "Query should end with the TIMESERIES keyword");

      Instant now = Instant.now();
      query += " SINCE " + now.minus(Duration.ofMinutes(30)).toEpochMilli() + " UNTIL " + now.toEpochMilli();
      DataCollectionRequest request = NewRelicFetchSampleDataRequest.builder()
                                          .type(DataCollectionRequestType.NEWRELIC_SAMPLE_FETCH_REQUEST)
                                          .query(query)
                                          .build();
      OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                      .dataCollectionRequest(request)
                                                      .connectorIdentifier(connectorIdentifier)
                                                      .accountId(projectParams.getAccountIdentifier())
                                                      .orgIdentifier(projectParams.getOrgIdentifier())
                                                      .tracingId(tracingId)
                                                      .projectIdentifier(projectParams.getProjectIdentifier())
                                                      .build();

      OnboardingResponseDTO response =
          onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);

      final Gson gson = new Gson();
      Type type = new TypeToken<LinkedHashMap>() {}.getType();
      return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    } catch (DataCollectionException ex) {
      return null;
    }
  }
}
