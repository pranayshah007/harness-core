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
import io.harness.datacollection.exception.DataCollectionException;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CloudWatchServiceImpl implements CloudWatchService {
  @Inject private OnboardingService onboardingService;

  @Override
  public Map fetchSampleData(ProjectParams projectParams, String connectorIdentifier, String tracingId,
      String expression, String region, String metricName, String metricIdentifier) {
    try {
      Preconditions.checkNotNull(expression);
      if (StringUtils.isEmpty(metricIdentifier)) {
        metricIdentifier = "id1";
      }
      if (StringUtils.isEmpty(metricName)) {
        metricName = "metric1";
      }
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
      return JsonUtils.asMap(JsonUtils.asJson(response.getResult()));
    } catch (DataCollectionException ex) {
      return null;
    }
  }
}
