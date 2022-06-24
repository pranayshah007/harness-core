/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.SplunkSavedSearch;
import io.harness.cvng.beans.splunk.SplunkLatestHistogramDataCollectionRequest;
import io.harness.cvng.beans.splunk.SplunkMetricSampleDataCollectionRequest;
import io.harness.cvng.beans.splunk.SplunkSampleDataCollectionRequest;
import io.harness.cvng.beans.splunk.SplunkSavedSearchRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.SplunkService;
import io.harness.serializer.JsonUtils;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class SplunkServiceImpl implements SplunkService {
  @Inject private OnboardingService onboardingService;

  @Override
  public List<SplunkSavedSearch> getSavedSearches(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String requestGuid) {
    DataCollectionRequest request = SplunkSavedSearchRequest.builder().build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .tracingId(requestGuid)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<List<SplunkSavedSearch>>() {}.getType();
    return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
  }

  @Override
  public List<LinkedHashMap> getSampleData(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String query, String requestGuid) {
    DataCollectionRequest request = SplunkSampleDataCollectionRequest.builder().query(query).build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .tracingId(requestGuid)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<List<LinkedHashMap>>() {}.getType();
    return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
  }

  @Override
  public SortedSet<TimeSeriesSampleDTO> getMetricSampleData(String accountId, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, String query, String requestGuid) {
    DataCollectionRequest request = SplunkMetricSampleDataCollectionRequest.builder().query(query).build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .tracingId(requestGuid)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<List<TimeSeriesSampleDTO>>() {}.getType();
    List<TimeSeriesSampleDTO> dataPoints = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    return new TreeSet<>(dataPoints);
  }

  @Override
  public List<LinkedHashMap> getLatestHistogram(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String query, String requestGuid) {
    DataCollectionRequest request = SplunkLatestHistogramDataCollectionRequest.builder().query(query).build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .tracingId(requestGuid)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<List<LinkedHashMap>>() {}.getType();
    return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
  }

  @Override
  public void checkConnectivity(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, String tracingId) {
    getSavedSearches(accountId, connectorIdentifier, orgIdentifier, projectIdentifier, tracingId);
  }
}
