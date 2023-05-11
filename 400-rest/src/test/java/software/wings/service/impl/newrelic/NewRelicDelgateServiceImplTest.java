/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.service.intfc.newrelic.NewRelicDelegateService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import retrofit2.Call;

@Slf4j
public class NewRelicDelgateServiceImplTest extends WingsBaseTest {
  @Inject private NewRelicDelegateService newRelicDelegateService;
  @Mock private RequestExecutor requestExecutor;

  private NewRelicConfig newRelicConfig;
  private Long newRelicApplicationId;
  private ThirdPartyApiCallLog thirdPartyApiCallLog;

  @Before
  public void setUp() throws IllegalAccessException {
    newRelicApplicationId = 1L;
    newRelicConfig =
        NewRelicConfig.builder().newRelicUrl("http://api.newrelic.com/").apiKey("key".toCharArray()).build();
    thirdPartyApiCallLog = ThirdPartyApiCallLog.builder().build();
    when(requestExecutor.executeRequest(any(), any())).thenReturn(NewRelicMetricDataResponse.builder().build());
    FieldUtils.writeField(newRelicDelegateService, "requestExecutor", requestExecutor, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricDataApplication() throws IOException {
    long startTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15);
    long endTime = System.currentTimeMillis();
    newRelicDelegateService.getMetricDataApplication(newRelicConfig, new ArrayList<>(), newRelicApplicationId,
        Lists.newArrayList("cpu"), startTime, endTime, false, thirdPartyApiCallLog);

    ArgumentCaptor<ThirdPartyApiCallLog> apiCallLogArgumentCaptor = ArgumentCaptor.forClass(ThirdPartyApiCallLog.class);
    ArgumentCaptor<Call> callArgumentCaptor = ArgumentCaptor.forClass(Call.class);

    verify(requestExecutor, times(1)).executeRequest(apiCallLogArgumentCaptor.capture(), callArgumentCaptor.capture());

    ThirdPartyApiCallLog callLog = apiCallLogArgumentCaptor.getValue();
    Call<NewRelicMetricDataResponse> call = callArgumentCaptor.getValue();

    assertThat(callLog).isNotNull();
    assertThat(callLog.getTitle())
        .isEqualTo(
            "Fetching application metric data for 1 transactions from http://api.newrelic.com//v2/applications/1/metrics/data.json");
    assertThat(callLog.getRequestTimeStamp()).isNotNull();
    assertThat(call).isNotNull();
    assertThat(call.request().url().url().toString())
        .startsWith("http://api.newrelic.com/v2/applications/1/metrics/data.json?summarize=false");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricDataApplicationInstance() throws IOException {
    long startTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15);
    long endTime = System.currentTimeMillis();
    newRelicDelegateService.getMetricDataApplicationInstance(newRelicConfig, new ArrayList<>(), newRelicApplicationId,
        10L, Lists.newArrayList("cpu"), startTime, endTime, thirdPartyApiCallLog);

    ArgumentCaptor<ThirdPartyApiCallLog> apiCallLogArgumentCaptor = ArgumentCaptor.forClass(ThirdPartyApiCallLog.class);
    ArgumentCaptor<Call> callArgumentCaptor = ArgumentCaptor.forClass(Call.class);

    verify(requestExecutor, times(1)).executeRequest(apiCallLogArgumentCaptor.capture(), callArgumentCaptor.capture());

    ThirdPartyApiCallLog callLog = apiCallLogArgumentCaptor.getValue();
    Call<NewRelicMetricDataResponse> call = callArgumentCaptor.getValue();

    assertThat(callLog).isNotNull();
    assertThat(callLog.getTitle())
        .isEqualTo(
            "Fetching instance metric data for 1 transactions from http://api.newrelic.com//v2/applications/1/instances/10/metrics/data.json");
    assertThat(callLog.getRequestTimeStamp()).isNotNull();
    assertThat(call).isNotNull();
    assertThat(call.request().url().url().toString())
        .startsWith("http://api.newrelic.com/v2/applications/1/instances/10/metrics/data.json");
  }
}
