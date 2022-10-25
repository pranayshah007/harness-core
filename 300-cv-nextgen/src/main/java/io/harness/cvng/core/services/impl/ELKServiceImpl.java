/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.elk.ELKIndexCollectionRequest;
import io.harness.cvng.beans.elk.ELKSampleDataCollectionRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.ELKService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.serializer.JsonUtils;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;

public class ELKServiceImpl implements ELKService {
  @Inject private OnboardingService onboardingService;
  @Override
  public void checkConnectivity(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, String tracingId) {}

  @Override
  public List<String> getLogIndexes(ProjectParams projectParams, String connectorIdentifier, String tracingId) {
    DataCollectionRequest request = ELKIndexCollectionRequest.builder().build();

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
    Type type = new TypeToken<List<String>>() {}.getType();
    return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
  }

  @Override
  public List<LinkedHashMap> getSampleData(
      ProjectParams projectParams, String connectorIdentifier, String query, String index, String tracingId) {
    DataCollectionRequest request = ELKSampleDataCollectionRequest.builder().query(query).index(index).build();

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
    Type type = new TypeToken<List<LinkedHashMap>>() {}.getType();
    return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
  }
}
