/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.stackdriver.StackDriverMetricDefinition;
import io.harness.cvng.beans.stackdriver.StackdriverDashboardDetailsRequest;
import io.harness.cvng.beans.stackdriver.StackdriverDashboardRequest;
import io.harness.cvng.beans.stackdriver.StackdriverLogSampleDataRequest;
import io.harness.cvng.beans.stackdriver.StackdriverSampleDataRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.stackdriver.StackdriverDashboardDTO;
import io.harness.cvng.core.beans.stackdriver.StackdriverDashboardDetail;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.StackdriverService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.exception.OnboardingException;
import io.harness.ng.beans.PageResponse;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PageUtils;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StackdriverServiceImpl implements StackdriverService {
  @Inject private OnboardingService onboardingService;
  @Override
  public PageResponse<StackdriverDashboardDTO> listDashboards(String accountId, String connectorIdentifier,
      String orgIdentifier, String projectIdentifier, int pageSize, int offset, String filter, String tracingId) {
    DataCollectionRequest request =
        StackdriverDashboardRequest.builder().type(DataCollectionRequestType.STACKDRIVER_DASHBOARD_LIST).build();

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
    Type type = new TypeToken<List<StackdriverDashboardDTO>>() {}.getType();
    List<StackdriverDashboardDTO> dashboardDTOS = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    List<StackdriverDashboardDTO> returnList = new ArrayList<>();
    if (isNotEmpty(filter)) {
      returnList = dashboardDTOS.stream()
                       .filter(dashboardDto -> dashboardDto.getName().toLowerCase().contains(filter.toLowerCase()))
                       .collect(Collectors.toList());
    } else {
      returnList = dashboardDTOS;
    }
    return PageUtils.offsetAndLimit(returnList, offset, pageSize);
  }

  @Override
  public List<StackdriverDashboardDetail> getDashboardDetails(String accountId, String connectorIdentifier,
      String orgIdentifier, String projectIdentifier, String path, String tracingId) {
    DataCollectionRequest request = StackdriverDashboardDetailsRequest.builder()
                                        .type(DataCollectionRequestType.STACKDRIVER_DASHBOARD_GET)
                                        .path(path)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .tracingId(tracingId)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);

    final Gson gson = new Gson();
    Type type = new TypeToken<List<StackdriverDashboardDetail>>() {}.getType();
    List<StackdriverDashboardDetail> dashboardDetails = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    dashboardDetails.forEach(StackdriverDashboardDetail::transformDataSets);
    return dashboardDetails;
  }

  @Override
  public List<LinkedHashMap> getSampleLogData(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, String query, String tracingId) {
    Instant now = DateTimeUtils.roundDownTo1MinBoundary(Instant.now());

    DataCollectionRequest request = StackdriverLogSampleDataRequest.builder()
                                        .type(DataCollectionRequestType.STACKDRIVER_LOG_SAMPLE_DATA)
                                        .query(query)
                                        .startTime(now.minus(Duration.ofMinutes(60)))
                                        .endTime(now)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .tracingId(tracingId)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);

    final Gson gson = new Gson();
    Type type = new TypeToken<List<LinkedHashMap>>() {}.getType();
    return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
  }

  @Override
  public Set<TimeSeriesSampleDTO> getSampleData(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, Object metricDefinitionDTO, String tracingId) {
    try {
      StackDriverMetricDefinition metricDefinition =
          StackDriverMetricDefinition.extractFromJson(JsonUtils.asJson(metricDefinitionDTO));

      Instant now = DateTimeUtils.roundDownTo1MinBoundary(Instant.now());

      DataCollectionRequest request = StackdriverSampleDataRequest.builder()
                                          .metricDefinition(metricDefinition)
                                          .startTime(now.minus(Duration.ofMinutes(60)))
                                          .endTime(now)
                                          .type(DataCollectionRequestType.STACKDRIVER_SAMPLE_DATA)
                                          .build();

      OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                      .dataCollectionRequest(request)
                                                      .connectorIdentifier(connectorIdentifier)
                                                      .accountId(accountId)
                                                      .tracingId(tracingId)
                                                      .orgIdentifier(orgIdentifier)
                                                      .projectIdentifier(projectIdentifier)
                                                      .build();

      OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);

      final Gson gson = new Gson();
      Type type = new TypeToken<List<TimeSeriesSampleDTO>>() {}.getType();
      List<TimeSeriesSampleDTO> dataPoints = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
      return new TreeSet<>(dataPoints);

    } catch (Exception ex) {
      String msg = "Exception while trying to fetch sample data. Please ensure that the metric definition corresponds "
          + "to a line chart in the dashboard.";
      log.error(msg, ex);
      throw new OnboardingException(msg);
    }
  }

  @Override
  public void checkConnectivity(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, String tracingId) {
    listDashboards(accountId, connectorIdentifier, orgIdentifier, projectIdentifier, 1, 0, null, tracingId);
  }
}
