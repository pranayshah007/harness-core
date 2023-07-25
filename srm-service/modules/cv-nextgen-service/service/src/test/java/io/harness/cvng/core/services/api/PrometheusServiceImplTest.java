/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.aws.AwsDataCollectionRequest;
import io.harness.cvng.beans.prometheus.PrometheusFetchSampleDataRequest;
import io.harness.cvng.beans.prometheus.PrometheusLabelNamesFetchRequest;
import io.harness.cvng.beans.prometheus.PrometheusLabelValuesFetchRequest;
import io.harness.cvng.beans.prometheus.PrometheusMetricListFetchRequest;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.beans.PrometheusSampleData;
import io.harness.cvng.core.beans.params.PrometheusConnectionParams;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class PrometheusServiceImplTest extends CvNextGenTestBase {
  @Inject private PrometheusService prometheusService;
  @Inject private OnboardingService onboardingService;
  @Mock private NextGenService nextGenService;
  @Mock private VerificationManagerService verificationManagerService;
  private String accountId;

  private String tracingId;
  private String connectorIdentifier;
  private Map<String, List<String>> stringListMap;
  private Map<String, Object> sampleDataResponse;
  private PrometheusConnectionParams prometheusConnectionParams;

  @Before
  public void setup() throws IllegalAccessException, IOException {
    accountId = generateUuid();
    connectorIdentifier = generateUuid();
    tracingId = generateUuid();
    prometheusConnectionParams = PrometheusConnectionParams.builder().connectorIdentifier(connectorIdentifier).build();
    FieldUtils.writeField(prometheusService, "onboardingService", onboardingService, true);
    FieldUtils.writeField(onboardingService, "nextGenService", nextGenService, true);
    FieldUtils.writeField(onboardingService, "verificationManagerService", verificationManagerService, true);

    when(nextGenService.get(anyString(), anyString(), anyString(), anyString()))
        .then(invocation
            -> Optional.of(
                ConnectorInfoDTO.builder().connectorConfig(PrometheusConnectorDTO.builder().build()).build()));
    stringListMap = getSamplePrometheusMetaData();
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(stringListMap));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetMetricNames() {
    List<String> metrics = prometheusService.getMetricNames(
        accountId, generateUuid(), generateUuid(), tracingId, prometheusConnectionParams);

    assertThat(metrics).isNotEmpty();
    assertThat(metrics).hasSameElementsAs(stringListMap.get("data"));
    verify(verificationManagerService, times(1))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(PrometheusMetricListFetchRequest.class));
    verify(verificationManagerService, times(0))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(AwsDataCollectionRequest.class));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLabelNames() {
    List<String> metrics = prometheusService.getLabelNames(
        accountId, generateUuid(), generateUuid(), tracingId, prometheusConnectionParams);

    assertThat(metrics).isNotEmpty();
    assertThat(metrics).hasSameElementsAs(stringListMap.get("data"));
    verify(verificationManagerService, times(1))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(PrometheusLabelNamesFetchRequest.class));
    verify(verificationManagerService, times(0))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(AwsDataCollectionRequest.class));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLabelValues() {
    List<String> metrics = prometheusService.getLabelValues(
        accountId, generateUuid(), generateUuid(), "labelName", tracingId, prometheusConnectionParams);

    assertThat(metrics).isNotEmpty();
    assertThat(metrics).hasSameElementsAs(stringListMap.get("data"));
    verify(verificationManagerService, times(1))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(PrometheusLabelValuesFetchRequest.class));
    verify(verificationManagerService, times(0))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(AwsDataCollectionRequest.class));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetSampleData() throws IOException {
    sampleDataResponse = JsonUtils.asMap(Resources.toString(
        PrometheusServiceImplTest.class.getResource("/prometheus/sample-metric-data.json"), Charsets.UTF_8));
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(sampleDataResponse));
    List<PrometheusSampleData> sampleData = prometheusService.getSampleData(
        accountId, generateUuid(), generateUuid(), "up", tracingId, prometheusConnectionParams);
    verify(verificationManagerService, times(1))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(PrometheusFetchSampleDataRequest.class));
    verify(verificationManagerService, times(0))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(AwsDataCollectionRequest.class));
    assertThat(sampleData).isNotEmpty();
    assertThat(sampleData).hasSize(6);
    assertThat(sampleData.get(0).getMetricDetails()).hasSize(9);
    assertThat(sampleData.get(0).getData()).hasSize(27);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getSampleData() throws IOException {
    sampleDataResponse = JsonUtils.asMap(Resources.toString(
        PrometheusServiceImplTest.class.getResource("/prometheus/sample-metric-data.json"), Charsets.UTF_8));
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(sampleDataResponse));
    List<PrometheusSampleData> sampleData =
        prometheusService.getSampleData(accountId, generateUuid(), generateUuid(), "up", tracingId,
            PrometheusConnectionParams.builder()
                .connectorIdentifier(connectorIdentifier)
                .dataSourceType(DataSourceType.AWS_PROMETHEUS)
                .region("")
                .workspaceId("")
                .build());
    verify(verificationManagerService, times(1))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(AwsDataCollectionRequest.class));
    verify(verificationManagerService, times(0))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(PrometheusFetchSampleDataRequest.class));
    assertThat(sampleData).isNotEmpty();
    assertThat(sampleData).hasSize(6);
    assertThat(sampleData.get(0).getMetricDetails()).hasSize(9);
    assertThat(sampleData.get(0).getData()).hasSize(27);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getSampleData_withOlderDSL() throws IOException {
    List<Map<String, Object>> sampleDataResponseList = JsonUtils.asList(
        Resources.toString(
            PrometheusServiceImplTest.class.getResource("/prometheus/sample-metric-data-old.json"), Charsets.UTF_8),
        new TypeReference<List<Map<String, Object>>>() {

        });
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(sampleDataResponseList));
    List<PrometheusSampleData> sampleData =
        prometheusService.getSampleData(accountId, generateUuid(), generateUuid(), "up", tracingId,
            PrometheusConnectionParams.builder()
                .connectorIdentifier(connectorIdentifier)
                .dataSourceType(DataSourceType.PROMETHEUS)
                .build());
    verify(verificationManagerService, times(0))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(AwsDataCollectionRequest.class));
    verify(verificationManagerService, times(1))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(PrometheusFetchSampleDataRequest.class));
    assertThat(sampleData).isNotEmpty();
    assertThat(sampleData).hasSize(1);
    assertThat(sampleData.get(0).getMetricDetails()).hasSize(19);
    assertThat(sampleData.get(0).getData()).hasSize(27);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getSampleData_regionIsNull() {
    assertThatThrownBy(()
                           -> prometheusService.getSampleData(accountId, generateUuid(), generateUuid(), "up", "",
                               PrometheusConnectionParams.builder()
                                   .connectorIdentifier(connectorIdentifier)
                                   .dataSourceType(DataSourceType.AWS_PROMETHEUS)
                                   .workspaceId("")
                                   .build()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("region should not be null");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getSampleData_workspaceIdIsNull() {
    assertThatThrownBy(()
                           -> prometheusService.getSampleData(accountId, generateUuid(), generateUuid(), "up", "",
                               PrometheusConnectionParams.builder()
                                   .connectorIdentifier(connectorIdentifier)
                                   .dataSourceType(DataSourceType.AWS_PROMETHEUS)
                                   .region("")
                                   .build()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("workspaceId should not be null");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getLabelValues() {
    List<String> metrics =
        prometheusService.getLabelValues(accountId, generateUuid(), generateUuid(), "labelName", tracingId,
            PrometheusConnectionParams.builder()
                .connectorIdentifier(connectorIdentifier)
                .dataSourceType(DataSourceType.AWS_PROMETHEUS)
                .region("")
                .workspaceId("")
                .build());
    verify(verificationManagerService, times(1))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(AwsDataCollectionRequest.class));
    verify(verificationManagerService, times(0))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(PrometheusLabelValuesFetchRequest.class));
    assertThat(metrics).isNotEmpty();
    assertThat(metrics).hasSameElementsAs(stringListMap.get("data"));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getLabelValues_regionIsNull() {
    assertThatThrownBy(
        ()
            -> prometheusService.getLabelValues(accountId, generateUuid(), generateUuid(), "labelName", "",
                PrometheusConnectionParams.builder()
                    .connectorIdentifier(connectorIdentifier)
                    .dataSourceType(DataSourceType.AWS_PROMETHEUS)
                    .workspaceId("")
                    .build()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("region should not be null");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getLabelValues_workspaceIdIsNull() {
    assertThatThrownBy(
        ()
            -> prometheusService.getLabelValues(accountId, generateUuid(), generateUuid(), "labelName", "",
                PrometheusConnectionParams.builder()
                    .connectorIdentifier(connectorIdentifier)
                    .dataSourceType(DataSourceType.AWS_PROMETHEUS)
                    .region("")
                    .build()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("workspaceId should not be null");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getLabelNames() {
    List<String> metrics = prometheusService.getLabelNames(accountId, generateUuid(), generateUuid(), tracingId,
        PrometheusConnectionParams.builder()
            .connectorIdentifier(connectorIdentifier)
            .dataSourceType(DataSourceType.AWS_PROMETHEUS)
            .region("")
            .workspaceId("")
            .build());
    verify(verificationManagerService, times(1))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(AwsDataCollectionRequest.class));
    verify(verificationManagerService, times(0))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(PrometheusLabelNamesFetchRequest.class));
    assertThat(metrics).isNotEmpty();
    assertThat(metrics).hasSameElementsAs(stringListMap.get("data"));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getLabelNames_regionIsNull() {
    assertThatThrownBy(()
                           -> prometheusService.getLabelNames(accountId, generateUuid(), generateUuid(), "",
                               PrometheusConnectionParams.builder()
                                   .connectorIdentifier(connectorIdentifier)
                                   .dataSourceType(DataSourceType.AWS_PROMETHEUS)
                                   .workspaceId("")
                                   .build()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("region should not be null");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getLabelNames_workspaceIdIsNull() {
    assertThatThrownBy(()
                           -> prometheusService.getLabelNames(accountId, generateUuid(), generateUuid(), "",
                               PrometheusConnectionParams.builder()
                                   .connectorIdentifier(connectorIdentifier)
                                   .dataSourceType(DataSourceType.AWS_PROMETHEUS)
                                   .region("")
                                   .build()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("workspaceId should not be null");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getMetricNames() {
    List<String> metrics = prometheusService.getMetricNames(accountId, generateUuid(), generateUuid(), tracingId,
        PrometheusConnectionParams.builder()
            .connectorIdentifier(connectorIdentifier)
            .dataSourceType(DataSourceType.AWS_PROMETHEUS)
            .region("")
            .workspaceId("")
            .build());
    verify(verificationManagerService, times(1))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(AwsDataCollectionRequest.class));
    verify(verificationManagerService, times(0))
        .getDataCollectionResponse(anyString(), anyString(), anyString(), any(PrometheusMetricListFetchRequest.class));
    assertThat(metrics).isNotEmpty();
    assertThat(metrics).hasSameElementsAs(stringListMap.get("data"));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getMetricNames_regionIsNull() {
    assertThatThrownBy(()
                           -> prometheusService.getMetricNames(accountId, generateUuid(), generateUuid(), "",
                               PrometheusConnectionParams.builder()
                                   .connectorIdentifier(connectorIdentifier)
                                   .dataSourceType(DataSourceType.AWS_PROMETHEUS)
                                   .workspaceId("")
                                   .build()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("region should not be null");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testAwsPrometheus_getMetricNames_workspaceIdIsNull() {
    assertThatThrownBy(()
                           -> prometheusService.getMetricNames(accountId, generateUuid(), generateUuid(), "",
                               PrometheusConnectionParams.builder()
                                   .connectorIdentifier(connectorIdentifier)
                                   .dataSourceType(DataSourceType.AWS_PROMETHEUS)
                                   .region("")
                                   .build()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("workspaceId should not be null");
  }

  private Map<String, List<String>> getSamplePrometheusMetaData() {
    List<String> values = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      values.add("value - " + i);
    }
    Map<String, List<String>> responseMap = new HashMap<>();
    responseMap.put("data", values);
    return responseMap;
  }
}
