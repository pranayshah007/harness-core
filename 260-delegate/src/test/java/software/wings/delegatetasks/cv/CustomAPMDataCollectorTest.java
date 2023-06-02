/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static software.wings.sm.StateType.APM_VERIFICATION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.beans.ApmResponseMapping;
import software.wings.beans.apm.Method;
import software.wings.beans.apm.ResponseType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.metrics.MetricType;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.impl.apm.CustomAPMDataCollectionInfo;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.verification.apm.APMCVServiceConfiguration;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class CustomAPMDataCollectorTest extends WingsBaseTest {
  @Mock EncryptionService mockEncryptionService;
  @Mock DataCollectionExecutionContext dataCollectionExecutionContext;
  CustomAPMDataCollector dataCollector;

  @Before
  public void setup() throws Exception {
    dataCollector = spy(new CustomAPMDataCollector());
    FieldUtils.writeField(dataCollector, "encryptionService", mockEncryptionService, true);
    when(mockEncryptionService.getDecryptedValue(any(), eq(false)))
        .thenReturn(new char[] {'a', 'p', 'i'})
        .thenReturn(new char[] {'a', 'p', 'p'});
    MockitoAnnotations.initMocks(this);

    when(dataCollectionExecutionContext.getActivityLogger())
        .thenReturn(mock(DelegateCVActivityLogService.Logger.class));
    ThirdPartyApiCallLog thirdPartyApiCallLog = mock(ThirdPartyApiCallLog.class);
    when(dataCollectionExecutionContext.createApiCallLog()).thenReturn(thirdPartyApiCallLog);
    when(dataCollectionExecutionContext.executeRequest(any(), any(), any())).then(invocation -> {
      String title = invocation.getArgument(0, String.class);
      Call<?> call = invocation.getArgument(1, Call.class);
      String output = Resources.toString(
          CustomAPMDataCollectorTest.class.getResource("/apm/datadog_sample_response_load.json"), Charsets.UTF_8);
      Response<Object> response = Response.success(output);
      return response.body();
    });
  }

  private CustomAPMDataCollectionInfo createDataCollectionInfo() {
    APMCVServiceConfiguration apmcvServiceConfiguration = new APMCVServiceConfiguration();
    apmcvServiceConfiguration.setName("APM config " + generateUuid());
    apmcvServiceConfiguration.setAccountId(generateUuid());
    apmcvServiceConfiguration.setAppId(generateUuid());
    apmcvServiceConfiguration.setEnvId(generateUuid());
    apmcvServiceConfiguration.setServiceId(generateUuid());
    apmcvServiceConfiguration.setEnabled24x7(true);
    apmcvServiceConfiguration.setConnectorId(generateUuid());
    apmcvServiceConfiguration.setStateType(APM_VERIFICATION);
    apmcvServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.MEDIUM);

    List<ApmMetricCollectionInfo> metricCollectionInfos = new ArrayList<>();
    ApmResponseMapping responseMapping = new ApmResponseMapping(
        "myhardcodedtxnName", null, null, "series[*].pointlist[*].[1]", null, null, "series[*].pointlist[*].[0]", null);

    ApmMetricCollectionInfo metricCollectionInfo =
        new ApmMetricCollectionInfo("metricName", MetricType.INFRA, "randomtag", "dummyuri", null,
            "{\"bodycollection\":\"body\"}", ResponseType.JSON, responseMapping, Method.POST);

    metricCollectionInfos.add(metricCollectionInfo);
    apmcvServiceConfiguration.setMetricCollectionInfos(metricCollectionInfos);

    DataCollectionInfoV2 dataCollectionInfov2 = apmcvServiceConfiguration.toDataCollectionInfo();
    CustomAPMDataCollectionInfo dataCollectionInfo = (CustomAPMDataCollectionInfo) dataCollectionInfov2;
    APMVerificationConfig apmVerificationConfig = new APMVerificationConfig();
    List<APMVerificationConfig.KeyValues> headers = new ArrayList<>();
    headers.add(APMVerificationConfig.KeyValues.builder().key("api_key").value("123").encrypted(true).build());
    headers.add(APMVerificationConfig.KeyValues.builder().key("api_key_plain").value("123").encrypted(false).build());
    apmVerificationConfig.setHeadersList(headers);
    apmVerificationConfig.setAccountId("111");
    apmVerificationConfig.setUrl("http://baseUrl.com/");
    apmVerificationConfig.setValidationUrl("suffix");

    dataCollectionInfo.setApmConfig(apmVerificationConfig);
    dataCollectionInfo.setEncryptedDataDetails(Arrays.asList(EncryptedDataDetail.builder().fieldName("api_key").build(),
        EncryptedDataDetail.builder().fieldName("appplication_key").build()));

    dataCollectionInfo.setStartTime(Instant.ofEpochMilli(System.currentTimeMillis()));
    dataCollectionInfo.setEndTime(Instant.ofEpochMilli(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)));

    return dataCollectionInfo;
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetch() {
    dataCollector.init(dataCollectionExecutionContext, createDataCollectionInfo());
    List<MetricElement> metricElements = dataCollector.fetchMetrics(Arrays.asList("hostName"));
    assertThat(metricElements.size()).isEqualTo(40);
    metricElements.forEach(element -> assertThat(element.getName()).isEqualTo("myhardcodedtxnName"));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetchNoHost() {
    dataCollector.init(dataCollectionExecutionContext, createDataCollectionInfo());
    List<MetricElement> metricElements = dataCollector.fetchMetrics();
    assertThat(metricElements.size()).isEqualTo(40);
    metricElements.forEach(element -> assertThat(element.getName()).isEqualTo("myhardcodedtxnName"));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testHostBatchSize() {
    assertThat(dataCollector.getHostBatchSize()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateRestCallFromDataCollectionInfo() {
    CustomAPMDataCollectionInfo dataCollectionInfo = createDataCollectionInfo();
    dataCollector.init(dataCollectionExecutionContext, dataCollectionInfo);
    List<Call<Object>> calls = dataCollector.createRestCallFromDataCollectionInfo(
        null, dataCollectionInfo, dataCollectionInfo.getMetricEndpoints());
    assertThat(calls.size()).isNotEqualTo(0);

    assertThat(calls.get(0).request().url().toString()).isEqualTo("http://baseurl.com/dummyuri");
    assertThat(calls.get(0).request().body()).isNotNull();
  }
}
