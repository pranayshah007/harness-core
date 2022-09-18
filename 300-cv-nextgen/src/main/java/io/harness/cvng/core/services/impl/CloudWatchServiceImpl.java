/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.cloudwatch.CloudWatchMetricFetchSampleDataRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.CloudWatchService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.utils.CloudWatchUtils;
import io.harness.datacollection.exception.DataCollectionException;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudWatchServiceImpl implements CloudWatchService {
  @Inject private OnboardingService onboardingService;

  @Override
  public Map fetchSampleData(ProjectParams projectParams, String connectorIdentifier, String tracingId,
      String expression, String region, String metricName, String metricIdentifier) {
    try {
      Preconditions.checkNotNull(expression);
      expression = expression.trim();

      DataCollectionRequest request = CloudWatchMetricFetchSampleDataRequest.builder()
                                          .type(DataCollectionRequestType.CLOUDWATCH_METRIC_SAMPLE_DATA_REQUEST)
                                          .metricName(metricName)
                                          .metricIdentifier(metricIdentifier)
                                          .expression(expression)
                                          .tracingId(tracingId)
                                          .region(region)
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
      Type type = new TypeToken<Map<String, Object>>() {}.getType();
      // Todo: Add validation for response data. Should not have multiple time-series responses.
      Map<String, Object> result = gson.fromJson(JsonUtils.asJson(response.getResult()), type);
      List timeseries = (List) result.get("MetricDataResults");
      Preconditions.checkState(timeseries.size() == 1, "Single time-series expected.");
      return result;
    } catch (DataCollectionException ex) {
      return null;
    }
  }

  @Override
  public List<String> fetchRegions() {
    return CloudWatchUtils.getAwsRegions();
  }
}
