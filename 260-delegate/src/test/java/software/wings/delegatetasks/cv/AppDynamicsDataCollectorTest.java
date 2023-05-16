/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AppDynamicsConfig;
import software.wings.helpers.ext.appdynamics.AppdynamicsRestClient;
import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.impl.appdynamics.AppDynamicsDataCollectionInfoV2;
import software.wings.service.impl.appdynamics.AppdynamicsMetric;
import software.wings.service.impl.appdynamics.AppdynamicsMetric.AppdynamicsMetricType;
import software.wings.service.impl.appdynamics.AppdynamicsMetricData;
import software.wings.service.impl.appdynamics.AppdynamicsMetricDataValue;
import software.wings.service.impl.appdynamics.AppdynamicsTier;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AppDynamicsDataCollectorTest extends WingsBaseTest {
  @Spy private AppDynamicsDataCollector appDynamicsDataCollector;
  @Inject private DataCollectionExecutorService dataCollectionService;

  private AppDynamicsDataCollectionInfoV2 dataCollectionInfo;
  private DataCollectionExecutionContext dataCollectionExecutionContext;
  private AppdynamicsTier appdynamicsTier;
  private List<AppdynamicsMetric> tierMetrics;
  private AppdynamicsMetricData response;
  private AppdynamicsRestClient appdynamicsRestClient;

  @Before
  public void setUp() throws IllegalAccessException {
    dataCollectionExecutionContext = Mockito.mock(DataCollectionExecutionContext.class);
    appdynamicsRestClient = Mockito.mock(AppdynamicsRestClient.class);
    dataCollectionInfo = AppDynamicsDataCollectionInfoV2.builder()
                             .accountId(generateUuid())
                             .appDynamicsTierId(10L)
                             .appDynamicsApplicationId(9L)
                             .hosts(new HashSet<>(Collections.singletonList("host")))
                             .startTime(Instant.now())
                             .endTime(Instant.now())
                             .appDynamicsConfig(AppDynamicsConfig.builder()
                                                    .username("username")
                                                    .accountname("accountname")
                                                    .password("password".toCharArray())
                                                    .build())
                             .build();
    response =
        AppdynamicsMetricData.builder()
            .metricId(4L)
            .metricName("/todolist")
            .metricPath(
                "Business Transaction Performance|Business Transactions|tier|/todolist|Individual Nodes|host|Calls per Minute")
            .metricValues(Collections.singletonList(AppdynamicsMetricDataValue.builder().value(0.2).build()))
            .build();
    appdynamicsTier = AppdynamicsTier.builder().id(10L).name("tier").build();
    tierMetrics = Collections.singletonList(
        AppdynamicsMetric.builder().name("Calls per Minute").type(AppdynamicsMetricType.leaf).build());
    doReturn(appdynamicsTier).when(appDynamicsDataCollector).getAppDynamicsTier();
    doReturn(tierMetrics).when(appDynamicsDataCollector).getTierBusinessTransactionMetrics();

    appDynamicsDataCollector.init(dataCollectionExecutionContext, dataCollectionInfo);
    FieldUtils.writeField(appDynamicsDataCollector, "dataCollectionService", dataCollectionService, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testInit() {
    verify(appDynamicsDataCollector, times(1)).getAppDynamicsTier();
    verify(appDynamicsDataCollector, times(1)).getTierBusinessTransactionMetrics();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testFetchMetrics_withHost() {
    doReturn(appdynamicsRestClient).when(appDynamicsDataCollector).getAppDynamicsRestClient();
    doReturn("").when(appDynamicsDataCollector).getHeaderWithCredentials();
    ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
    appDynamicsDataCollector.fetchMetrics(new ArrayList<>(dataCollectionInfo.getHosts()));
    verify(dataCollectionExecutionContext, times(1)).executeRequest(titleCaptor.capture(), any());

    String title = titleCaptor.getValue();
    assertThat(title).isEqualTo(
        "Fetching data for metric path: Business Transaction Performance|Business Transactions|tier|Calls per Minute|Individual Nodes|host|*");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testFetchMetrics_withoutHost() {
    doReturn(appdynamicsRestClient).when(appDynamicsDataCollector).getAppDynamicsRestClient();
    doReturn("").when(appDynamicsDataCollector).getHeaderWithCredentials();
    ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
    appDynamicsDataCollector.fetchMetrics();
    verify(dataCollectionExecutionContext, times(1)).executeRequest(titleCaptor.capture(), any());

    String title = titleCaptor.getValue();
    assertThat(title).isEqualTo(
        "Fetching data for metric path: Business Transaction Performance|Business Transactions|tier|Calls per Minute|*");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testParseAppDynamicsResponse() {
    List<Optional<List<AppdynamicsMetricData>>> appDynamicsMetricDataOptionalList = new ArrayList<>();
    appDynamicsMetricDataOptionalList.add(Optional.of(Collections.singletonList(response)));

    List<MetricElement> metricElements =
        appDynamicsDataCollector.parseAppDynamicsResponse(appDynamicsMetricDataOptionalList, "host");

    assertThat(metricElements.size()).isEqualTo(1);
    assertThat(metricElements.get(0).getName()).isEqualTo("/todolist");
    assertThat(metricElements.get(0).getGroupName()).isEqualTo("tier");
    assertThat(metricElements.get(0).getValues().keySet())
        .isEqualTo(new HashSet<>(Collections.singletonList("Calls per Minute")));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetHeaderWithCredentials() {
    String header = appDynamicsDataCollector.getHeaderWithCredentials();
    assertThat(header).isEqualTo("Basic dXNlcm5hbWVAYWNjb3VudG5hbWU6cGFzc3dvcmQ=");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetAppDynamicsTier() {
    doReturn(appdynamicsRestClient).when(appDynamicsDataCollector).getAppDynamicsRestClient();
    doReturn("").when(appDynamicsDataCollector).getHeaderWithCredentials();
    doCallRealMethod().when(appDynamicsDataCollector).getAppDynamicsTier();
    doReturn(Collections.singletonList(appdynamicsTier))
        .when(dataCollectionExecutionContext)
        .executeRequest(any(), any());

    ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
    AppdynamicsTier tier = appDynamicsDataCollector.getAppDynamicsTier();

    verify(dataCollectionExecutionContext, times(1)).executeRequest(titleCaptor.capture(), any());
    String title = titleCaptor.getValue();
    assertThat(title).isEqualTo("Fetching tiers from null");
    assertThat(tier).isEqualTo(appdynamicsTier);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetTierBusinessTransactionMetrics() {
    doReturn(appdynamicsRestClient).when(appDynamicsDataCollector).getAppDynamicsRestClient();
    doReturn("").when(appDynamicsDataCollector).getHeaderWithCredentials();
    doCallRealMethod().when(appDynamicsDataCollector).getTierBusinessTransactionMetrics();
    doReturn(tierMetrics).when(dataCollectionExecutionContext).executeRequest(any(), any());

    ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
    List<AppdynamicsMetric> metrics = appDynamicsDataCollector.getTierBusinessTransactionMetrics();

    verify(dataCollectionExecutionContext, times(1)).executeRequest(titleCaptor.capture(), any());
    String title = titleCaptor.getValue();
    assertThat(title).isEqualTo("Fetching business transactions for tier from null");
    assertThat(metrics).isEqualTo(tierMetrics);
  }
}
