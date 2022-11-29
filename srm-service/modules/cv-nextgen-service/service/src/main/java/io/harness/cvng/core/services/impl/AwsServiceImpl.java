/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.aws.AwsDataCollectionRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.aws.AwsPrometheusWorkspaceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.AwsService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.utils.AwsUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AwsServiceImpl implements AwsService {
  @Inject OnboardingService onboardingService;
  @Override
  public List<String> fetchRegions() {
    return AwsUtils.getAwsRegions();
  }

  @Override
  public List<AwsPrometheusWorkspaceDTO> fetchAllWorkspaces(
      ProjectParams projectParams, String connectorIdentifier, String region, String tracingId) {
    List<AwsPrometheusWorkspaceDTO> workspaces = new ArrayList<>();
    Map<String, String> queryMap = new HashMap<>();
    queryMap.put("maxResults", "1000");
    DataCollectionRequest request = AwsDataCollectionRequest.builder()
                                        .type(DataCollectionRequestType.AWS_GENERIC_DATA_COLLECTION_REQUEST)
                                        .tracingId(tracingId)
                                        .region(region)
                                        .awsService("aps")
                                        .urlServicePrefix("aps")
                                        .urlServiceSuffix("workspaces")
                                        .queryParameters(queryMap)
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
    Map<String, Object> resultMap = (Map<String, Object>) response.getResult();
    List<Map<String, String>> allWorkspaces = (List<Map<String, String>>) resultMap.get("workspaces");
    workspaces.addAll(allWorkspaces.stream()
                          .map(workspaceMap
                              -> AwsPrometheusWorkspaceDTO.builder()
                                     .name(workspaceMap.get("alias"))
                                     .workspaceId(workspaceMap.get("workspaceId"))
                                     .build())
                          .collect(Collectors.toList()));
    return workspaces;
  }
}
