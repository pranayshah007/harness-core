/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instana;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.InstanaConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.service.intfc.instana.InstanaDelegateService;
import software.wings.service.intfc.security.EncryptionService;

import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import retrofit2.Call;

public class InstanaDelegateServiceImplTest extends WingsBaseTest {
  @Mock private EncryptionService encryptionService;
  @Mock private RequestExecutor requestExecutor;
  private InstanaDelegateService instanaDelegateService;
  private InstanaConfig instanaConfig;
  private String accountId = UUID.randomUUID().toString();

  @Before
  public void setup() throws IllegalAccessException {
    initMocks(this);
    instanaDelegateService = new InstanaDelegateServiceImpl();
    FieldUtils.writeField(instanaDelegateService, "encryptionService", encryptionService, true);
    FieldUtils.writeField(instanaDelegateService, "requestExecutor", requestExecutor, true);

    instanaConfig = InstanaConfig.builder()
                        .instanaUrl("https://instana-example.com/")
                        .accountId(accountId)
                        .apiToken(UUID.randomUUID().toString().toCharArray())
                        .build();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetInfraMetrics() {
    InstanaInfraMetricRequest infraMetricRequest = InstanaInfraMetricRequest.builder().build();
    String stateExecutionId = UUID.randomUUID().toString();
    ThirdPartyApiCallLog apiCallLog = ThirdPartyApiCallLog.createApiCallLog(accountId, stateExecutionId);
    InstanaInfraMetrics instanaInfraMetrics = mock(InstanaInfraMetrics.class);
    when(requestExecutor.executeRequest(any(), any())).thenReturn(instanaInfraMetrics);

    InstanaInfraMetrics result =
        instanaDelegateService.getInfraMetrics(instanaConfig, mock(List.class), infraMetricRequest, apiCallLog);
    ArgumentCaptor<Call> argumentCaptor = ArgumentCaptor.forClass(Call.class);
    verify(requestExecutor).executeRequest(eq(apiCallLog), argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isInstanceOf(Call.class);
    Call<InstanaInfraMetrics> call = argumentCaptor.getValue();
    assertThat(call.request().url().toString())
        .isEqualTo("https://instana-example.com/api/infrastructure-monitoring/metrics/");
    assertThat(call.request().method()).isEqualTo("POST");
    assertThat(call.request().header("Authorization")).isEqualTo("apiToken " + new String(instanaConfig.getApiToken()));
    assertThat(result).isEqualTo(instanaInfraMetrics);
    assertThat(apiCallLog.getTitle()).isEqualTo("Fetching Infrastructure metrics from https://instana-example.com/");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateConfig_whenSuccessful() {
    InstanaInfraMetrics instanaInfraMetrics = mock(InstanaInfraMetrics.class);
    when(requestExecutor.executeRequest(any())).thenReturn(instanaInfraMetrics);

    boolean result = instanaDelegateService.validateConfig(instanaConfig, mock(List.class));
    ArgumentCaptor<Call> argumentCaptor = ArgumentCaptor.forClass(Call.class);
    verify(requestExecutor).executeRequest(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isInstanceOf(Call.class);
    Call<InstanaInfraMetrics> call = argumentCaptor.getValue();
    assertThat(call.request().url().toString())
        .isEqualTo("https://instana-example.com/api/infrastructure-monitoring/metrics/");
    assertThat(call.request().method()).isEqualTo("POST");
    assertThat(call.request().header("Authorization")).isEqualTo("apiToken " + new String(instanaConfig.getApiToken()));
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateConfig_whenExecuteFails() {
    DataCollectionException dataCollectionException = new DataCollectionException("exception from executeRequest");
    when(requestExecutor.executeRequest(any())).thenThrow(dataCollectionException);

    assertThatThrownBy(() -> instanaDelegateService.validateConfig(instanaConfig, mock(List.class)))
        .isInstanceOf(DataCollectionException.class);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetInstanaTraceMetrics() {
    InstanaAnalyzeMetricRequest instanaAnalyzeMetricRequest = InstanaAnalyzeMetricRequest.builder().build();
    String stateExecutionId = UUID.randomUUID().toString();
    ThirdPartyApiCallLog apiCallLog = ThirdPartyApiCallLog.createApiCallLog(accountId, stateExecutionId);
    InstanaAnalyzeMetrics instanaAnalyzeMetrics = mock(InstanaAnalyzeMetrics.class);
    when(requestExecutor.executeRequest(any(), any())).thenReturn(instanaAnalyzeMetrics);

    InstanaAnalyzeMetrics result = instanaDelegateService.getInstanaTraceMetrics(
        instanaConfig, mock(List.class), instanaAnalyzeMetricRequest, apiCallLog);
    ArgumentCaptor<Call> argumentCaptor = ArgumentCaptor.forClass(Call.class);
    verify(requestExecutor).executeRequest(eq(apiCallLog), argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isInstanceOf(Call.class);
    Call<InstanaInfraMetrics> call = argumentCaptor.getValue();
    assertThat(call.request().url().toString())
        .isEqualTo("https://instana-example.com/api/application-monitoring/analyze/trace-groups");
    assertThat(call.request().method()).isEqualTo("POST");
    assertThat(call.request().header("Authorization")).isEqualTo("apiToken " + new String(instanaConfig.getApiToken()));
    assertThat(result).isEqualTo(instanaAnalyzeMetrics);
    assertThat(apiCallLog.getTitle()).isEqualTo("Fetching application call metrics from https://instana-example.com/");
  }
}
