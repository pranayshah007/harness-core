/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.ThirdPartyApiResponseStatus;
import io.harness.cvng.beans.newrelic.NewRelicApplication;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.RequestExecutor;
import io.harness.cvng.client.VerificationManagerClient;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.beans.MetricPackValidationResponse;
import io.harness.cvng.core.beans.MetricPackValidationResponse.MetricValidationResponse;
import io.harness.datacollection.exception.DataCollectionException;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class NewRelicServiceImplTest extends CvNextGenTestBase {
  @Inject private NewRelicService newRelicService;
  @Inject OnboardingService onboardingService;
  @Mock VerificationManagerClient verificationManagerClient;
  @Mock NextGenService nextGenService;
  @Mock VerificationManagerService verificationManagerService;
  @Mock private RequestExecutor requestExecutor;
  private String accountId;
  private String connectorIdentifier;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    accountId = generateUuid();
    connectorIdentifier = generateUuid();
    builderFactory = BuilderFactory.getDefault();
    FieldUtils.writeField(newRelicService, "onboardingService", onboardingService, true);
    FieldUtils.writeField(onboardingService, "nextGenService", nextGenService, true);
    FieldUtils.writeField(onboardingService, "verificationManagerService", verificationManagerService, true);

    when(nextGenService.get(anyString(), anyString(), anyString(), anyString()))
        .then(invocation
            -> Optional.of(ConnectorInfoDTO.builder().connectorConfig(NewRelicConnectorDTO.builder().build()).build()));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetEndpoints() {
    List<String> endpoints = newRelicService.getNewRelicEndpoints();

    assertThat(endpoints.size()).isEqualTo(2);
    assertThat(endpoints).contains("https://insights-api.newrelic.com/", "https://insights-api.eu.newrelic.com/");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNewRelicApplications() {
    List<NewRelicApplication> newRelicApplications = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      newRelicApplications.add(
          NewRelicApplication.builder().applicationName("application - " + i).applicationId(i).build());
    }
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(newRelicApplications));

    List<NewRelicApplication> applications = newRelicService.getNewRelicApplications(
        accountId, connectorIdentifier, generateUuid(), generateUuid(), "", generateUuid());

    assertThat(applications).isNotEmpty();
    assertThat(applications.size()).isEqualTo(100);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateMetricData() {
    List<MetricValidationResponse> metricValidationResponseList = new ArrayList<>();
    metricValidationResponseList.add(
        MetricValidationResponse.builder().metricName("Average Response Time").value(100.0).build());
    metricValidationResponseList.add(
        MetricValidationResponse.builder().metricName("Calls Per Minute").value(500.0).build());
    metricValidationResponseList.add(MetricValidationResponse.builder().metricName("Errors").value(10.0).build());
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(metricValidationResponseList));

    MetricPackValidationResponse metricPackValidationResponse =
        newRelicService.validateData(accountId, connectorIdentifier, generateUuid(), generateUuid(), "appName", "appId",
            Arrays.asList(MetricPackDTO.builder().build()), generateUuid());

    assertThat(metricPackValidationResponse.getOverallStatus().name())
        .isEqualTo(ThirdPartyApiResponseStatus.SUCCESS.name());
    assertThat(metricPackValidationResponse.getMetricValidationResponses().size()).isEqualTo(3);
    metricPackValidationResponse.getMetricValidationResponses().forEach(metricValidationResponse -> {
      assertThat(metricValidationResponse.getStatus().name()).isEqualTo(ThirdPartyApiResponseStatus.SUCCESS.name());
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateMetricData_noDataForOneMetric() {
    List<MetricValidationResponse> metricValidationResponseList = new ArrayList<>();
    metricValidationResponseList.add(
        MetricValidationResponse.builder().metricName("Average Response Time").value(100.0).build());
    metricValidationResponseList.add(
        MetricValidationResponse.builder().metricName("Calls Per Minute").value(null).build());
    metricValidationResponseList.add(MetricValidationResponse.builder().metricName("Errors").value(10.0).build());
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(metricValidationResponseList));

    MetricPackValidationResponse metricPackValidationResponse =
        newRelicService.validateData(accountId, connectorIdentifier, generateUuid(), generateUuid(), "appName", "appId",
            Arrays.asList(MetricPackDTO.builder().build()), generateUuid());

    assertThat(metricPackValidationResponse.getOverallStatus().name())
        .isEqualTo(ThirdPartyApiResponseStatus.SUCCESS.name());
    assertThat(metricPackValidationResponse.getMetricValidationResponses().size()).isEqualTo(3);
    metricPackValidationResponse.getMetricValidationResponses().forEach(metricValidationResponse -> {
      if (metricValidationResponse.getMetricName().equals("Calls Per Minute")) {
        assertThat(metricValidationResponse.getStatus().name()).isEqualTo(ThirdPartyApiResponseStatus.NO_DATA.name());
      } else {
        assertThat(metricValidationResponse.getStatus().name()).isEqualTo(ThirdPartyApiResponseStatus.SUCCESS.name());
      }
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateMetricData_noDataForAllMetrics() {
    List<MetricValidationResponse> metricValidationResponseList = new ArrayList<>();
    metricValidationResponseList.add(
        MetricValidationResponse.builder().metricName("Average Response Time").value(null).build());
    metricValidationResponseList.add(
        MetricValidationResponse.builder().metricName("Calls Per Minute").value(null).build());
    metricValidationResponseList.add(MetricValidationResponse.builder().metricName("Errors").value(null).build());
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(metricValidationResponseList));

    MetricPackValidationResponse metricPackValidationResponse =
        newRelicService.validateData(accountId, connectorIdentifier, generateUuid(), generateUuid(), "appName", "appId",
            Arrays.asList(MetricPackDTO.builder().build()), generateUuid());

    assertThat(metricPackValidationResponse.getOverallStatus().name())
        .isEqualTo(ThirdPartyApiResponseStatus.NO_DATA.name());
    assertThat(metricPackValidationResponse.getMetricValidationResponses().size()).isEqualTo(3);
    metricPackValidationResponse.getMetricValidationResponses().forEach(metricValidationResponse -> {
      assertThat(metricValidationResponse.getStatus().name()).isEqualTo(ThirdPartyApiResponseStatus.NO_DATA.name());
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateMetricData_exception() {
    List<MetricValidationResponse> metricValidationResponseList = new ArrayList<>();
    metricValidationResponseList.add(
        MetricValidationResponse.builder().metricName("Average Response Time").value(null).build());
    metricValidationResponseList.add(
        MetricValidationResponse.builder().metricName("Calls Per Minute").value(null).build());
    metricValidationResponseList.add(MetricValidationResponse.builder().metricName("Errors").value(null).build());
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenThrow(new DataCollectionException("Exception while validating data"));

    MetricPackValidationResponse metricPackValidationResponse =
        newRelicService.validateData(accountId, connectorIdentifier, generateUuid(), generateUuid(), "appName", "appId",
            Arrays.asList(MetricPackDTO.builder().build()), generateUuid());

    assertThat(metricPackValidationResponse.getOverallStatus().name())
        .isEqualTo(ThirdPartyApiResponseStatus.FAILED.name());
    assertThat(metricPackValidationResponse.getMetricValidationResponses()).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetchSampleData() {
    List<NewRelicApplication> newRelicApplications = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      newRelicApplications.add(
          NewRelicApplication.builder().applicationName("application - " + i).applicationId(i).build());
    }
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(
            NewRelicApplication.builder().applicationName("application - ").applicationId(12).build()));
    String query =
        "SELECT average(`apm.service.transaction.duration`) FROM Metric WHERE appName = 'My Application' TIMESERIES";
    LinkedHashMap response =
        newRelicService.fetchSampleData(builderFactory.getProjectParams(), connectorIdentifier, query, generateUuid());
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetchSampleData_badQueryWithTime() {
    List<NewRelicApplication> newRelicApplications = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      newRelicApplications.add(
          NewRelicApplication.builder().applicationName("application - " + i).applicationId(i).build());
    }
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(newRelicApplications));
    String query =
        "SELECT average(`apm.service.transaction.duration`) FROM Metric WHERE appName = 'My Application' TIMESERIES SINCE 30 MINUTES AGO";
    assertThatThrownBy(()
                           -> newRelicService.fetchSampleData(
                               builderFactory.getProjectParams(), connectorIdentifier, query, generateUuid()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining(
            "Query should not contain any time duration. Please remove SINCE or any time related keywords");
  }
}
