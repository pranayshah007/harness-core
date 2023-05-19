/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.ElkConfig;
import software.wings.helpers.ext.elk.ElkRestClient;
import software.wings.helpers.ext.elk.KibanaRestClient;
import software.wings.service.impl.analysis.ElkConnector;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.elk.ElkDataCollectionInfoV2;
import software.wings.service.impl.elk.ElkQueryType;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Retrofit;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ElkDataCollectorTest extends CategoryTest {
  private ElkConfig config;
  private ElkDataCollector elkDataCollector;

  @Before
  public void setUp() throws IllegalAccessException {
    initMocks(this);
    config = ElkConfig.builder()
                 .accountId("123")
                 .elkUrl("https://elk-test-host.com:8089/")
                 .username("123")
                 .password("123".toCharArray())
                 .elkConnector(ElkConnector.ELASTIC_SEARCH_SERVER)
                 .build();
    elkDataCollector = spy(new ElkDataCollector());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchLogs_withHostFilters() throws IOException {
    List<String> hosts = new ArrayList<>();
    hosts.add("harness-example-deployment-fb4f6557b-2pk78");
    ElkDataCollectionInfoV2 elkDataCollectionInfo = createDataCollectionInfo();
    DataCollectionExecutionContext dataCollectionExecutionContext = mock(DataCollectionExecutionContext.class);
    ElkRestClient elkRestClient = mock(ElkRestClient.class);
    doReturn(elkRestClient).when(elkDataCollector).getElkRestClient(eq(config));
    when(dataCollectionExecutionContext.executeRequest(any(), any())).thenReturn(elkJsonResponse());
    elkDataCollector.init(dataCollectionExecutionContext, elkDataCollectionInfo);
    List<LogElement> logElements = elkDataCollector.fetchLogs(hosts);
    assertThat(logElements.size()).isEqualTo(6);
    logElements.forEach(logElement -> {
      assertThat(logElement.getClusterLabel()).isNotBlank();
      assertThat(logElement.getQuery()).isEqualTo("exception");
      assertThat(logElement.getLogMessage()).isNotBlank();
    });
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchLogs_withHostFiltersV7() throws IOException {
    List<String> hosts = new ArrayList<>();
    hosts.add("harness-example-deployment-fb4f6557b-2pk78");
    ElkDataCollectionInfoV2 elkDataCollectionInfo = createDataCollectionInfo();
    DataCollectionExecutionContext dataCollectionExecutionContext = mock(DataCollectionExecutionContext.class);
    ElkRestClient elkRestClient = mock(ElkRestClient.class);
    doReturn(elkRestClient).when(elkDataCollector).getElkRestClient(eq(config));
    when(dataCollectionExecutionContext.executeRequest(any(), any())).thenReturn(elkJsonResponseV7());
    elkDataCollector.init(dataCollectionExecutionContext, elkDataCollectionInfo);
    List<LogElement> logElements = elkDataCollector.fetchLogs(hosts);
    assertThat(logElements.size()).isEqualTo(6);
    logElements.forEach(logElement -> {
      assertThat(logElement.getClusterLabel()).isNotBlank();
      assertThat(logElement.getQuery()).isEqualTo("exception");
      assertThat(logElement.getLogMessage()).isNotBlank();
    });
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchLogs_withoutHostFilters() throws IOException {
    ElkDataCollectionInfoV2 elkDataCollectionInfo = createDataCollectionInfo();
    DataCollectionExecutionContext dataCollectionExecutionContext = mock(DataCollectionExecutionContext.class);
    ElkRestClient elkRestClient = mock(ElkRestClient.class);
    doReturn(elkRestClient).when(elkDataCollector).getElkRestClient(eq(config));
    when(dataCollectionExecutionContext.executeRequest(any(), any())).thenReturn(elkJsonResponse());
    elkDataCollector.init(dataCollectionExecutionContext, elkDataCollectionInfo);
    List<LogElement> logElements = elkDataCollector.fetchLogs();
    assertThat(logElements.size()).isEqualTo(61);
    logElements.forEach(logElement -> {
      assertThat(logElement.getClusterLabel()).isNotBlank();
      assertThat(logElement.getQuery()).isEqualTo("exception");
      assertThat(logElement.getLogMessage()).isNotBlank();
    });
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchLogs_withoutHostFiltersWithKibanaServer() throws IOException {
    ElkDataCollectionInfoV2 elkDataCollectionInfo = createDataCollectionInfo();
    DataCollectionExecutionContext dataCollectionExecutionContext = mock(DataCollectionExecutionContext.class);
    KibanaRestClient kibanaRestClient = mock(KibanaRestClient.class);
    config.setElkConnector(ElkConnector.KIBANA_SERVER);
    doReturn(kibanaRestClient).when(elkDataCollector).getKibanaRestClient(eq(config));
    when(dataCollectionExecutionContext.executeRequest(any(), any())).thenReturn(elkJsonResponse());
    elkDataCollector.init(dataCollectionExecutionContext, elkDataCollectionInfo);
    List<LogElement> logElements = elkDataCollector.fetchLogs();
    assertThat(logElements.size()).isEqualTo(61);
    logElements.forEach(logElement -> {
      assertThat(logElement.getClusterLabel()).isNotBlank();
      assertThat(logElement.getQuery()).isEqualTo("exception");
      assertThat(logElement.getLogMessage()).isNotBlank();
    });
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateRetrofit() throws IOException {
    ElkDataCollectionInfoV2 elkDataCollectionInfo = createDataCollectionInfo();
    DataCollectionExecutionContext dataCollectionExecutionContext = mock(DataCollectionExecutionContext.class);
    KibanaRestClient kibanaRestClient = mock(KibanaRestClient.class);
    config.setElkConnector(ElkConnector.KIBANA_SERVER);
    doReturn(kibanaRestClient).when(elkDataCollector).getKibanaRestClient(eq(config));
    when(dataCollectionExecutionContext.executeRequest(any(), any())).thenReturn(elkJsonResponse());
    elkDataCollector.init(dataCollectionExecutionContext, elkDataCollectionInfo);
    Retrofit retrofit = elkDataCollector.createRetrofit(config);
    assertThat(retrofit.baseUrl().toString()).isEqualTo(config.getElkUrl());
  }

  private Object elkJsonResponse() throws IOException {
    String json = IOUtils.toString(
        ElkDataCollector.class.getResourceAsStream("elk-response.json"), StandardCharsets.UTF_8.name());
    return new ObjectMapper().readValue(json, HashMap.class);
  }
  private Object elkJsonResponseV7() throws IOException {
    String json = IOUtils.toString(
        ElkDataCollector.class.getResourceAsStream("elk-responsev7.json"), StandardCharsets.UTF_8.name());
    return new ObjectMapper().readValue(json, HashMap.class);
  }

  public ElkDataCollectionInfoV2 createDataCollectionInfo() {
    return ElkDataCollectionInfoV2.builder()
        .elkConfig(config)
        .query("exception")
        .queryType(ElkQueryType.MATCH)
        .startTime(Instant.ofEpochMilli(1571915589423L).minus(Duration.ofMinutes(10)))
        .endTime(Instant.ofEpochMilli(1571915589423L).plus(Duration.ofMinutes(10)))
        .indices("integration-test")
        .hostnameField("hostname")
        .timestampField("@timestamp")
        .messageField("message")
        .timestampFieldFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
        .build();
  }
}
