/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.AppdynamicsValidationResponse;
import io.harness.cvng.beans.AppdynamicsValidationResponse.AppdynamicsMetricValueValidationResponse;
import io.harness.cvng.beans.AppdynamicsValidationResponse.AppdynamicsValidationResponseBuilder;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.ThirdPartyApiResponseStatus;
import io.harness.cvng.beans.appd.AppDynamicFetchFileStructureRequest;
import io.harness.cvng.beans.appd.AppDynamicSingleMetricDataRequest;
import io.harness.cvng.beans.appd.AppDynamicsApplication;
import io.harness.cvng.beans.appd.AppDynamicsFetchAppRequest;
import io.harness.cvng.beans.appd.AppDynamicsFetchTiersRequest;
import io.harness.cvng.beans.appd.AppDynamicsFileDefinition;
import io.harness.cvng.beans.appd.AppDynamicsFileDefinition.FileType;
import io.harness.cvng.beans.appd.AppDynamicsMetricDataValidationRequest;
import io.harness.cvng.beans.appd.AppDynamicsTier;
import io.harness.cvng.beans.appd.AppdynamicsMetricDataResponse;
import io.harness.cvng.beans.appd.AppdynamicsMetricDataResponse.DataPoint;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.AppDynamicsService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.CorrelationContext;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PageUtils;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
@OwnedBy(CV)
public class AppDynamicsServiceImpl implements AppDynamicsService {
  @Inject private OnboardingService onboardingService;
  @Inject private Clock clock;

  @Override
  // TODO: We need to find a testing strategy for Retrofit interfaces. The current way of mocking Call is too cumbersome
  public Set<AppdynamicsValidationResponse> getMetricPackData(String accountId, String connectorIdentifier,
      String orgIdentifier, String projectIdentifier, String appName, String tierName, String requestGuid,
      List<MetricPackDTO> metricPacks) {
    // TODO: move this logic to one call when we have ability to iterate through a map in DSL
    Set<AppdynamicsValidationResponse> validationResponses = new HashSet<>();
    metricPacks.forEach(metricPack -> {
      DataCollectionRequest request = AppDynamicsMetricDataValidationRequest.builder()
                                          .applicationName(appName)
                                          .tierName(tierName)
                                          .metricPack(metricPack)
                                          .type(DataCollectionRequestType.APPDYNAMICS_GET_METRIC_DATA)
                                          .build();

      OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                      .dataCollectionRequest(request)
                                                      .connectorIdentifier(connectorIdentifier)
                                                      .accountId(accountId)
                                                      .orgIdentifier(orgIdentifier)
                                                      .projectIdentifier(projectIdentifier)
                                                      .tracingId(requestGuid)
                                                      .build();

      try {
        OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
        final Gson gson = new Gson();
        Type type = new TypeToken<List<TimeSeriesRecord>>() {}.getType();
        List<TimeSeriesRecord> timeSeriesRecords = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
        AppdynamicsValidationResponseBuilder validationResponseBuilder =
            AppdynamicsValidationResponse.builder().metricPackName(metricPack.getIdentifier());
        AtomicReference<ThirdPartyApiResponseStatus> overAllStatus =
            new AtomicReference<>(ThirdPartyApiResponseStatus.SUCCESS);
        metricPack.getMetrics()
            .stream()
            .filter(metricDefinitionDTO -> metricDefinitionDTO.isIncluded())
            .forEach(metricDefinition -> {
              TimeSeriesRecord timeSeriesRecord =
                  timeSeriesRecords.stream()
                      .filter(record
                          -> record.getMetricName().equals(
                              getMetricNameFromValidationPath(metricDefinition.getValidationPath())))
                      .findFirst()
                      .orElse(null);

              if (timeSeriesRecord == null) {
                validationResponseBuilder.addValidationResponse(
                    AppdynamicsMetricValueValidationResponse.builder()
                        .metricName(metricDefinition.getName())
                        .apiResponseStatus(ThirdPartyApiResponseStatus.NO_DATA)
                        .build());
                overAllStatus.set(ThirdPartyApiResponseStatus.NO_DATA);
              } else {
                validationResponseBuilder.addValidationResponse(
                    AppdynamicsMetricValueValidationResponse.builder()
                        .metricName(metricDefinition.getName())
                        .apiResponseStatus(ThirdPartyApiResponseStatus.SUCCESS)
                        .value(timeSeriesRecord.getMetricValue())
                        .build());
              }
            });
        validationResponses.add(validationResponseBuilder.overallStatus(overAllStatus.get()).build());
      } catch (Exception e) {
        validationResponses.add(AppdynamicsValidationResponse.builder()
                                    .metricPackName(metricPack.getIdentifier())
                                    .overallStatus(ThirdPartyApiResponseStatus.FAILED)
                                    .build());
      }
    });

    return validationResponses;
  }

  private String getMetricNameFromValidationPath(String validationPath) {
    return validationPath.substring(validationPath.lastIndexOf('|') + 1);
  }

  @Override
  public PageResponse<AppDynamicsApplication> getApplications(String accountId, String connectorIdentifier,
      String orgIdentifier, String projectIdentifier, int offset, int pageSize, String filter) {
    DataCollectionRequest request =
        AppDynamicsFetchAppRequest.builder().type(DataCollectionRequestType.APPDYNAMICS_FETCH_APPS).build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .orgIdentifier(orgIdentifier)
                                                    .tracingId(CorrelationContext.getCorrelationId())
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);

    final Gson gson = new Gson();
    Type type = new TypeToken<List<AppDynamicsApplication>>() {}.getType();
    List<AppDynamicsApplication> applications = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    if (isNotEmpty(filter)) {
      applications = applications.stream()
                         .filter(appDynamicsApplication
                             -> appDynamicsApplication.getName().toLowerCase().contains(filter.trim().toLowerCase()))
                         .collect(Collectors.toList());
    }
    Collections.sort(applications);

    return PageUtils.offsetAndLimit(applications, offset, pageSize);
  }

  @Override
  public PageResponse<AppDynamicsTier> getTiers(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, String appName, int offset, int pageSize, String filter) {
    DataCollectionRequest request = AppDynamicsFetchTiersRequest.builder()
                                        .appName(appName)
                                        .type(DataCollectionRequestType.APPDYNAMICS_FETCH_TIERS)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .tracingId(CorrelationContext.getCorrelationId())
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);

    final Gson gson = new Gson();
    Type type = new TypeToken<List<AppDynamicsTier>>() {}.getType();
    List<AppDynamicsTier> tiers = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    List<AppDynamicsTier> appDynamicsTiers = new ArrayList<>();
    tiers.forEach(appDynamicsTier -> {
      if (isEmpty(filter) || appDynamicsTier.getName().toLowerCase().contains(filter.trim().toLowerCase())) {
        appDynamicsTiers.add(appDynamicsTier);
      }
    });
    Collections.sort(appDynamicsTiers);
    return PageUtils.offsetAndLimit(appDynamicsTiers, offset, pageSize);
  }

  @Override
  public List<String> getBaseFolders(
      ProjectParams projectParams, String connectorIdentifier, String appName, String path, String tracingId) {
    // Base folders are at empty path
    List<AppDynamicsFileDefinition> fileDefinitions =
        getMetricStructure(projectParams, connectorIdentifier, appName, path, tracingId);
    return fileDefinitions.stream()
        .filter(fileDefinition -> fileDefinition.getType().equals(FileType.FOLDER))
        .map(AppDynamicsFileDefinition::getName)
        .collect(Collectors.toList());
  }

  @Override
  public List<AppDynamicsFileDefinition> getMetricStructure(ProjectParams projectParams, String connectorIdentifier,
      String appName, String baseFolder, String tier, String metricPath, String tracingId) {
    return getMetricStructure(
        projectParams, connectorIdentifier, appName, getCompletePath(baseFolder, tier, metricPath), tracingId);
  }

  @Override
  public AppdynamicsMetricDataResponse getMetricData(ProjectParams projectParams, String connectorIdentifier,
      String appName, String baseFolder, String tier, String metricPath, String tracingId) {
    Instant endTime = clock.instant();
    Instant startTime = endTime.minus(Duration.ofHours(1));
    DataCollectionRequest request = AppDynamicSingleMetricDataRequest.builder()
                                        .applicationName(appName)
                                        .startTime(startTime)
                                        .endTime(endTime)
                                        .metricPath(getCompletePath(baseFolder, tier, metricPath))
                                        .type(DataCollectionRequestType.APPDYNAMICS_GET_SINGLE_METRIC_DATA)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(projectParams.getAccountIdentifier())
                                                    .orgIdentifier(projectParams.getOrgIdentifier())
                                                    .projectIdentifier(projectParams.getProjectIdentifier())
                                                    .tracingId(tracingId)
                                                    .build();

    OnboardingResponseDTO response =
        onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<List<TimeSeriesRecord>>() {}.getType();
    List<TimeSeriesRecord> timeSeriesRecords = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    if (CollectionUtils.isEmpty(timeSeriesRecords)) {
      return AppdynamicsMetricDataResponse.builder()
          .startTime(startTime.toEpochMilli())
          .endTime(endTime.toEpochMilli())
          .responseStatus(ThirdPartyApiResponseStatus.NO_DATA)
          .build();
    }
    return AppdynamicsMetricDataResponse.builder()
        .responseStatus(ThirdPartyApiResponseStatus.SUCCESS)
        .startTime(startTime.toEpochMilli())
        .endTime(endTime.toEpochMilli())
        .dataPoints(timeSeriesRecords.stream()
                        .map(timeSeriesRecord
                            -> DataPoint.builder()
                                   .timestamp(timeSeriesRecord.getTimestamp())
                                   .value(timeSeriesRecord.getMetricValue())
                                   .build())
                        .collect(Collectors.toList()))
        .build();
  }

  @Override
  public String getServiceInstanceMetricPath(ProjectParams projectParams, String connectorIdentifier, String appName,
      String baseFolder, String tier, String metricPath, String tracingId) {
    String[] metricPathFolders = metricPath.split("\\|");
    StringBuilder metricPathSoFar = new StringBuilder(512);
    int index = 0;

    for (; index < metricPathFolders.length - 1; index++) {
      if (containsIndividualNode(
              projectParams, connectorIdentifier, appName, baseFolder, tier, metricPathSoFar.toString(), tracingId)) {
        break;
      }
      metricPathSoFar.append('|').append(metricPathFolders[index]);
    }
    metricPathSoFar.append("|Individual Nodes|*");
    for (; index < metricPathFolders.length; index++) {
      { metricPathSoFar.append('|').append(metricPathFolders[index]); }
    }
    return metricPathSoFar.substring(1, metricPathSoFar.length());
  }

  @Override
  public void checkConnectivity(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, String tracingId) {
    getApplications(accountId, connectorIdentifier, orgIdentifier, projectIdentifier, 0, 1, null);
  }

  private boolean containsIndividualNode(ProjectParams projectParams, String connectorIdentifier, String appName,
      String baseFolder, String tier, String metricPath, String tracingId) {
    List<AppDynamicsFileDefinition> appDynamicsFileDefinitions =
        getMetricStructure(projectParams, connectorIdentifier, appName, baseFolder, tier, metricPath, tracingId);
    return appDynamicsFileDefinitions.stream()
        .map(AppDynamicsFileDefinition::getName)
        .anyMatch(name -> name.equals("Individual Nodes"));
  }

  private List<AppDynamicsFileDefinition> getMetricStructure(
      ProjectParams projectParams, String connectorIdentifier, String appName, String metricPath, String tracingId) {
    DataCollectionRequest request = AppDynamicFetchFileStructureRequest.builder()
                                        .appName(appName)
                                        .path(metricPath)
                                        .type(DataCollectionRequestType.APPDYNAMICS_FETCH_METRIC_STRUCTURE)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(projectParams.getAccountIdentifier())
                                                    .tracingId(tracingId)
                                                    .orgIdentifier(projectParams.getOrgIdentifier())
                                                    .projectIdentifier(projectParams.getProjectIdentifier())
                                                    .build();

    OnboardingResponseDTO response =
        onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);

    final Gson gson = new Gson();
    Type type = new TypeToken<List<AppDynamicsFileDefinition>>() {}.getType();
    return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
  }

  private String getCompletePath(String baseFolder, String tier, String metricPath) {
    return baseFolder + "|" + tier + "|" + metricPath;
  }
}
