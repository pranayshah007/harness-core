/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.rule.OwnerRule.ANJAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.customhealth.CustomHealthFetchSampleDataRequest;
import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.cvng.core.beans.CustomHealthSampleDataRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class CustomHealthServiceImplTest extends CvNextGenTestBase {
  @Mock OnboardingService onboardingServiceMock;
  @Inject CustomHealthService customHealthService;

  String accountId = "1234_accountId";
  String connectorIdentifier = "1234_connectorIdentifer";
  String orgIdentifier = "1234_orgIdentifier";
  String projectIdentifier = "1234_projectIdentifier";
  String tracingId = "1234_tracingId";

  @Before
  public void setup() throws IllegalAccessException {
    FieldUtils.writeField(customHealthService, "onboardingService", onboardingServiceMock, true);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testFetchSampleData() {
    String requestBody = "postBody";
    CustomHealthMethod requestMethod = CustomHealthMethod.POST;
    String urlPath = "asdasd?asdad";
    TimestampInfo startTime = TimestampInfo.builder().build();
    TimestampInfo endTime = TimestampInfo.builder().build();

    CustomHealthFetchSampleDataRequest customHealthSampleDataRequest =
        CustomHealthFetchSampleDataRequest.builder()
            .type(DataCollectionRequestType.CUSTOM_HEALTH_SAMPLE_DATA)
            .body(requestBody)
            .method(requestMethod)
            .startTime(startTime)
            .endTime(endTime)
            .urlPath(urlPath)
            .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(customHealthSampleDataRequest)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .orgIdentifier(orgIdentifier)
                                                    .tracingId(tracingId)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    CustomHealthSampleDataRequest request = CustomHealthSampleDataRequest.builder()
                                                .body(requestBody)
                                                .method(CustomHealthMethod.POST)
                                                .urlPath(urlPath)
                                                .startTime(startTime)
                                                .endTime(endTime)
                                                .build();

    OnboardingResponseDTO responseDTO = OnboardingResponseDTO.builder().result(new HashMap<>()).build();
    when(onboardingServiceMock.getOnboardingResponse(accountId, onboardingRequestDTO)).thenReturn(responseDTO);
    assertThat(customHealthService.fetchSampleData(
                   accountId, connectorIdentifier, orgIdentifier, projectIdentifier, tracingId, request))
        .isEqualTo(new HashMap<>());
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testFetchSampleData_withArrayList() {
    String requestBody = "postBody";
    CustomHealthMethod requestMethod = CustomHealthMethod.POST;
    String urlPath = "asdasd?asdad";
    TimestampInfo startTime = TimestampInfo.builder().build();
    TimestampInfo endTime = TimestampInfo.builder().build();

    CustomHealthFetchSampleDataRequest customHealthSampleDataRequest =
        CustomHealthFetchSampleDataRequest.builder()
            .type(DataCollectionRequestType.CUSTOM_HEALTH_SAMPLE_DATA)
            .body(requestBody)
            .method(requestMethod)
            .startTime(startTime)
            .endTime(endTime)
            .urlPath(urlPath)
            .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(customHealthSampleDataRequest)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .orgIdentifier(orgIdentifier)
                                                    .tracingId(tracingId)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    CustomHealthSampleDataRequest request = CustomHealthSampleDataRequest.builder()
                                                .body(requestBody)
                                                .method(CustomHealthMethod.POST)
                                                .urlPath(urlPath)
                                                .startTime(startTime)
                                                .endTime(endTime)
                                                .build();

    OnboardingResponseDTO responseDTO = OnboardingResponseDTO.builder().result(new ArrayList<>()).build();
    when(onboardingServiceMock.getOnboardingResponse(accountId, onboardingRequestDTO)).thenReturn(responseDTO);
    assertThat(customHealthService.fetchSampleData(
                   accountId, connectorIdentifier, orgIdentifier, projectIdentifier, tracingId, request))
        .isEqualTo(new ArrayList<>());
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testFetchSampleData_whenResponseIsNull() {
    String requestBody = "postBody";
    CustomHealthMethod requestMethod = CustomHealthMethod.POST;
    String urlPath = "asdasd?asdad";
    TimestampInfo startTime = TimestampInfo.builder().build();
    TimestampInfo endTime = TimestampInfo.builder().build();

    CustomHealthFetchSampleDataRequest customHealthSampleDataRequest =
        CustomHealthFetchSampleDataRequest.builder()
            .type(DataCollectionRequestType.CUSTOM_HEALTH_SAMPLE_DATA)
            .body(requestBody)
            .method(requestMethod)
            .startTime(startTime)
            .endTime(endTime)
            .urlPath(urlPath)
            .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(customHealthSampleDataRequest)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .orgIdentifier(orgIdentifier)
                                                    .tracingId(tracingId)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    CustomHealthSampleDataRequest request = CustomHealthSampleDataRequest.builder()
                                                .body(requestBody)
                                                .method(CustomHealthMethod.POST)
                                                .urlPath(urlPath)
                                                .startTime(startTime)
                                                .endTime(endTime)
                                                .build();

    OnboardingResponseDTO responseDTO = OnboardingResponseDTO.builder().result(null).build();
    when(onboardingServiceMock.getOnboardingResponse(accountId, onboardingRequestDTO)).thenReturn(responseDTO);
    assertThat(customHealthService.fetchSampleData(
                   accountId, connectorIdentifier, orgIdentifier, projectIdentifier, tracingId, request))
        .isEqualTo(null);
  }
}
