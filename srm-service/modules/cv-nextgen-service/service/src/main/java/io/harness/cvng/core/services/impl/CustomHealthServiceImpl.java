/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.customhealth.CustomHealthFetchSampleDataRequest;
import io.harness.cvng.core.beans.CustomHealthSampleDataRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.services.api.CustomHealthService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.serializer.JsonUtils;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomHealthServiceImpl implements CustomHealthService {
  @Inject OnboardingService onboardingService;

  @Override
  public Object fetchSampleData(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, String tracingId, CustomHealthSampleDataRequest request) {
    CustomHealthFetchSampleDataRequest customHealthSampleDataRequest =
        CustomHealthFetchSampleDataRequest.builder()
            .type(DataCollectionRequestType.CUSTOM_HEALTH_SAMPLE_DATA)
            .body(request.getBody())
            .method(request.getMethod())
            .urlPath(request.getUrlPath())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(customHealthSampleDataRequest)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .orgIdentifier(orgIdentifier)
                                                    .tracingId(tracingId)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
    final Gson gson = new Gson();

    Object resultObj = response.getResult();
    if (resultObj == null) {
      return null;
    }

    if (response.getResult().getClass() == ArrayList.class) {
      Type type = new TypeToken<ArrayList<Object>>() {}.getType();
      return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    }

    return gson.fromJson(JsonUtils.asJson(response.getResult()), new TypeToken<Map<String, Object>>() {}.getType());
  }
}
