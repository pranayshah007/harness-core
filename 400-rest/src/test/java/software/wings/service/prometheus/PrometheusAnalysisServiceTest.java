/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.prometheus;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.VerificationOperationException;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.beans.APMValidateCollectorConfig;
import software.wings.beans.PrometheusConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.impl.analysis.APMDelegateService;
import software.wings.service.impl.analysis.SetupTestNodeData;
import software.wings.service.impl.analysis.TimeSeries;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.impl.prometheus.PrometheusAnalysisServiceImpl;
import software.wings.service.impl.prometheus.PrometheusMetricDataResponse;
import software.wings.service.impl.prometheus.PrometheusMetricDataResponse.PrometheusMetricData;
import software.wings.service.impl.prometheus.PrometheusMetricDataResponse.PrometheusMetricDataResult;
import software.wings.service.impl.prometheus.PrometheusSetupTestNodeData;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PrometheusAnalysisServiceTest extends WingsBaseTest {
  @Mock private SettingsService settingsService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private APMDelegateService apmDelegateService;
  @Mock private MLServiceUtils mlServiceUtils;
  @Mock private SecretManager secretManager;
  @InjectMocks private PrometheusAnalysisServiceImpl prometheusAnalysisService;

  String settingId;

  @Before
  public void setup() {
    settingId = generateUuid();
    MockitoAnnotations.initMocks(this);
    when(delegateProxyFactory.getV2(eq(APMDelegateService.class), any(SyncTaskContext.class)))
        .thenReturn(apmDelegateService);
    when(mlServiceUtils.getHostName(any())).thenReturn("dummyHostName");
    PrometheusConfig config = PrometheusConfig.builder().url("http://34.68.138.55:8080/").build();
    when(settingsService.get(settingId))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(config).build());
    when(secretManager.getEncryptionDetails(any())).thenReturn(new ArrayList());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeDataCheckDelegateCalledTwice() {
    PrometheusSetupTestNodeData nodeData = buildInput();
    when(apmDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenReturn(JsonUtils.asJson(createResponse()));
    prometheusAnalysisService.getMetricsWithDataForNode(nodeData);

    verify(apmDelegateService, times(1)).fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeData() {
    PrometheusSetupTestNodeData nodeData = buildInput();

    when(apmDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenReturn(JsonUtils.asJson(createResponse()));
    VerificationNodeDataSetupResponse setupResponse = prometheusAnalysisService.getMetricsWithDataForNode(nodeData);

    assertThat(setupResponse).isNotNull();
    assertThat(setupResponse.isProviderReachable()).isTrue();
    assertThat(setupResponse.getLoadResponse().isLoadPresent()).isTrue();
    assertThat(setupResponse.getDataForNode()).isNotNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeData_withMultipleMetricValuesAreReturned() {
    PrometheusSetupTestNodeData nodeData = buildInput();

    when(apmDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenReturn(JsonUtils.asJson(createResponseWithMultipleMetricsResponses()));
    assertThatThrownBy(() -> prometheusAnalysisService.getMetricsWithDataForNode(nodeData))
        .isInstanceOf(VerificationOperationException.class);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNotReachable() {
    PrometheusSetupTestNodeData nodeData = buildInput();

    when(apmDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenThrow(new DataCollectionException("unsuccessful call"));
    assertThatThrownBy(() -> prometheusAnalysisService.getMetricsWithDataForNode(nodeData))
        .isInstanceOf(DataCollectionException.class)
        .hasMessage("unsuccessful call");
  }

  private PrometheusMetricDataResponse createResponse() {
    PrometheusMetricDataResult result = PrometheusMetricDataResult.builder().build();

    PrometheusMetricData data = PrometheusMetricData.builder().build();
    data.setResult(Arrays.asList(result));

    PrometheusMetricDataResponse response = PrometheusMetricDataResponse.builder().build();
    response.setStatus("success");
    response.setData(data);
    return response;
  }

  private PrometheusMetricDataResponse createResponseWithMultipleMetricsResponses() {
    PrometheusMetricDataResult result = PrometheusMetricDataResult.builder().build();

    PrometheusMetricData data = PrometheusMetricData.builder().build();
    data.setResult(Arrays.asList(result, result));

    PrometheusMetricDataResponse response = PrometheusMetricDataResponse.builder().build();
    response.setStatus("success");
    response.setData(data);
    return response;
  }

  private PrometheusSetupTestNodeData buildInput() {
    return PrometheusSetupTestNodeData.builder()
        .timeSeriesToAnalyze(Arrays.asList(
            TimeSeries.builder()
                .metricName("cpu")
                .metricType("INFRA")
                .txnName("Hardware")
                .url(
                    "/api/v1/query_range?start=$startTime&end=$endTime&step=60s&query=container_cpu_usage_seconds_total{container_name=\"harness-example\",pod_name=\"$hostName\"}")
                .build()))
        .instanceElement(SetupTestNodeData.Instance.builder()
                             .instanceDetails(InstanceDetails.builder().hostName("dummyHostName").build())
                             .build())
        .settingId(settingId)
        .build();
  }
}
