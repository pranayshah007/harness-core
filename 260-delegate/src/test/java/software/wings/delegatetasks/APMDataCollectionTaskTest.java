/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.rule.OwnerRule.ANJAN;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static software.wings.beans.TaskType.APM_METRIC_DATA_COLLECTION_TASK;
import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.metrics.MetricType;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.apm.APMDataCollectionInfo;
import software.wings.service.impl.apm.APMMetricInfo;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class APMDataCollectionTaskTest extends WingsBaseTest {
  APMDataCollectionInfo dataCollectionInfo;
  @Mock private RequestExecutor requestExecutor;
  @Mock private EncryptionService encryptionService;
  @Mock private MetricDataStoreService metricStoreService;
  @Inject private DataCollectionExecutorService dataCollectionService;
  private APMDataCollectionTask dataCollectionTask;

  private void setup(Map<String, List<APMMetricInfo>> metricInfo) throws Exception {
    MockitoAnnotations.initMocks(this);
    String delegateId = UUID.randomUUID().toString();
    String appId = UUID.randomUUID().toString();
    String envId = UUID.randomUUID().toString();
    String waitId = UUID.randomUUID().toString();
    String accountId = UUID.randomUUID().toString();
    String infrastructureMappingId = UUID.randomUUID().toString();
    String timeDuration = "10";
    dataCollectionInfo =
        APMDataCollectionInfo.builder()
            .startTime(12312321123L)
            .stateType(DelegateStateType.APM_VERIFICATION)
            .dataCollectionFrequency(2)
            .hosts(ImmutableMap.<String, String>builder()
                       .put("test.host.node1", DEFAULT_GROUP_NAME)
                       .put("test.host.node2", DEFAULT_GROUP_NAME)
                       .build())
            .encryptedDataDetails(Arrays.asList(EncryptedDataDetail.builder().fieldName("apiKey").build()))
            .metricEndpoints(metricInfo)
            .dataCollectionMinute(0)
            .baseUrl("http://api.datadog.com/v1/")
            .build();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(APM_METRIC_DATA_COLLECTION_TASK.name())
                            .parameters(new Object[] {dataCollectionInfo})
                            .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(timeDuration) + 120))
                            .build();

    dataCollectionTask = new APMDataCollectionTask(
        DelegateTaskPackage.builder().delegateId(delegateId).data(taskData).build(), null, null, null);
    FieldUtils.writeField(dataCollectionTask, "metricStoreService", metricStoreService, true);
    FieldUtils.writeField(dataCollectionTask, "encryptionService", encryptionService, true);
    FieldUtils.writeField(dataCollectionTask, "requestExecutor", requestExecutor, true);
    FieldUtils.writeField(dataCollectionTask, "dataCollectionService", dataCollectionService, true);
    when(encryptionService.getDecryptedValue(any(), eq(false))).thenReturn("decryptedApiKey".toCharArray());
    when(metricStoreService.saveNewRelicMetrics(any(), anyString(), any(), any(), any())).thenReturn(true);
  }
  private Method useReflectionToMakeInnerClassVisible() throws Exception {
    Class[] innerClasses = dataCollectionTask.getClass().getDeclaredClasses();
    log.info("" + innerClasses);
    Class[] parameterTypes = new Class[1];
    parameterTypes[0] = String.class;
    Method m = innerClasses[0].getDeclaredMethod("resolveBatchHosts", parameterTypes);
    m.setAccessible(true);
    return m;
  }
  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testBatchingHosts() throws Exception {
    setup(null);
    DataCollectionTaskResult tr =
        dataCollectionTask.initDataCollection((TaskParameters) dataCollectionTask.getParameters()[0]);
    String batchUrl = "urlData{$harness_batch{pod_name:${host},'|'}}";
    List<String> batchedHosts =
        (List<String>) useReflectionToMakeInnerClassVisible().invoke(dataCollectionTask.getDataCollector(tr), batchUrl);
    assertThat(batchedHosts).hasSize(1);
    assertThat(batchedHosts.get(0)).isEqualTo("urlData{pod_name:test.host.node1|pod_name:test.host.node2}");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testMoreThanFiftyHostsInBatch() throws Exception {
    setup(null);
    Map<String, String> hostList = new HashMap<>();
    for (int i = 0; i < 52; i++) {
      hostList.put("test.host.node" + i, DEFAULT_GROUP_NAME);
    }

    dataCollectionInfo.setHosts(hostList);
    DataCollectionTaskResult tr =
        dataCollectionTask.initDataCollection((TaskParameters) dataCollectionTask.getParameters()[0]);
    String batchUrl = "urlData{$harness_batch{pod_name:${host},'|'}}";
    List<String> batchedHosts =
        (List<String>) useReflectionToMakeInnerClassVisible().invoke(dataCollectionTask.getDataCollector(tr), batchUrl);
    assertThat(batchedHosts).hasSize(4);
    // Since hostList in the CollectionTask class is a set, the order isn't maintained. So wecant compare directly.
    int occurrenceCount1 = StringUtils.countMatches(batchedHosts.get(0), "test.host.node");
    int occurrenceCount2 = StringUtils.countMatches(batchedHosts.get(1), "test.host.node");
    int occurrenceCount3 = StringUtils.countMatches(batchedHosts.get(2), "test.host.node");
    int occurrenceCount4 = StringUtils.countMatches(batchedHosts.get(3), "test.host.node");
    assertThat(occurrenceCount1 == 15).isTrue();
    assertThat(occurrenceCount2 == 15).isTrue();
    assertThat(occurrenceCount3 == 15).isTrue();
    assertThat(occurrenceCount4 == 7).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testEmptyEncryptedCredentialsInitDataCollection() throws Exception {
    setup(null);
    APMDataCollectionInfo info = (APMDataCollectionInfo) dataCollectionTask.getParameters()[0];
    info.setEncryptedDataDetails(null);
    DataCollectionTaskResult tr =
        dataCollectionTask.initDataCollection((TaskParameters) dataCollectionTask.getParameters()[0]);
    assertThat(dataCollectionTask.getTaskType()).isEqualTo(APM_METRIC_DATA_COLLECTION_TASK.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testDataCollection() throws Exception {
    String text500 = Resources.toString(
        APMDataCollectionTaskTest.class.getResource("/apm/insights_sample_response.json"), Charsets.UTF_8);

    Map<String, APMMetricInfo.ResponseMapper> responseMapperMap = new HashMap<>();
    responseMapperMap.put(
        "host", APMMetricInfo.ResponseMapper.builder().fieldName("host").jsonPath("facets[*].name[1]").build());
    responseMapperMap.put("timestamp",
        APMMetricInfo.ResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath("facets[*].timeSeries[*].endTimeSeconds")
            .build());
    responseMapperMap.put("value",
        APMMetricInfo.ResponseMapper.builder()
            .fieldName("value")
            .jsonPath("facets[*].timeSeries[*].results[*].count")
            .build());
    responseMapperMap.put(
        "txnName", APMMetricInfo.ResponseMapper.builder().fieldName("txnName").jsonPath("facets[*].name[0]").build());

    List<APMMetricInfo> metricInfos = Lists.newArrayList(APMMetricInfo.builder()
                                                             .metricName("HttpErrors")
                                                             .metricType(MetricType.ERROR)
                                                             .tag("NRHTTP")
                                                             .responseMappers(responseMapperMap)
                                                             .build());
    Map<String, List<APMMetricInfo>> infoMap = new HashMap<>();
    infoMap.put("?query=data+123&apiKey=${apiKey}", metricInfos);
    setup(infoMap);
    FieldUtils.writeField(dataCollectionTask, "metricStoreService", metricStoreService, true);
    when(requestExecutor.executeRequest(any(), any(), any())).thenReturn(text500);
    DataCollectionTaskResult tr =
        dataCollectionTask.initDataCollection((TaskParameters) dataCollectionTask.getParameters()[0]);
    dataCollectionTask.getDataCollector(tr).run();

    ArgumentCaptor<Map> maskPatternsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Call> requestCaptor = ArgumentCaptor.forClass(Call.class);
    verify(requestExecutor).executeRequest(any(), requestCaptor.capture(), maskPatternsCaptor.capture());

    List<Map> maskPatterns = maskPatternsCaptor.getAllValues();
    maskPatterns.forEach(
        maskPatternsMap -> assertThat(((Map<String, String>) maskPatternsMap).containsKey("decryptedApiKey")).isTrue());
    assertThat(requestCaptor.getValue().request().url().toString().contains("apiKey=decryptedApiKey")).isTrue();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testDataCollection_withBase64EncodedHeader() throws Exception {
    String text500 = Resources.toString(
        APMDataCollectionTaskTest.class.getResource("/apm/insights_sample_response.json"), Charsets.UTF_8);

    Map<String, APMMetricInfo.ResponseMapper> responseMapperMap = new HashMap<>();
    responseMapperMap.put(
        "host", APMMetricInfo.ResponseMapper.builder().fieldName("host").jsonPath("facets[*].name[1]").build());
    responseMapperMap.put("timestamp",
        APMMetricInfo.ResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath("facets[*].timeSeries[*].endTimeSeconds")
            .build());
    responseMapperMap.put("value",
        APMMetricInfo.ResponseMapper.builder()
            .fieldName("value")
            .jsonPath("facets[*].timeSeries[*].results[*].count")
            .build());
    responseMapperMap.put(
        "txnName", APMMetricInfo.ResponseMapper.builder().fieldName("txnName").jsonPath("facets[*].name[0]").build());

    List<APMMetricInfo> metricInfos = Lists.newArrayList(APMMetricInfo.builder()
                                                             .metricName("HttpErrors")
                                                             .metricType(MetricType.ERROR)
                                                             .tag("NRHTTP")
                                                             .responseMappers(responseMapperMap)
                                                             .build());
    Map<String, List<APMMetricInfo>> infoMap = new HashMap<>();
    infoMap.put("?query=data+123&apiKey=${apiKey}", metricInfos);
    setup(infoMap);

    // base 64 encoding header
    dataCollectionInfo.setBase64EncodingRequired(true);
    ArrayList<EncryptedDataDetail> encryptedDataDetailArrayList =
        new ArrayList<>(dataCollectionInfo.getEncryptedDataDetails());
    encryptedDataDetailArrayList.add(EncryptedDataDetail.builder().fieldName("password").build());
    dataCollectionInfo.setEncryptedDataDetails(encryptedDataDetailArrayList);
    HashMap<String, String> headersMap = new HashMap<>();
    headersMap.put("Authorization", "Basic encodeWithBase64(user:${password})");
    dataCollectionInfo.setHeaders(headersMap);

    FieldUtils.writeField(dataCollectionTask, "metricStoreService", metricStoreService, true);
    when(requestExecutor.executeRequest(any(), any(), any())).thenReturn(text500);
    DataCollectionTaskResult tr =
        dataCollectionTask.initDataCollection((TaskParameters) dataCollectionTask.getParameters()[0]);
    dataCollectionTask.getDataCollector(tr).run();

    ArgumentCaptor<Map> maskPatternsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Call> requestCaptor = ArgumentCaptor.forClass(Call.class);
    verify(requestExecutor).executeRequest(any(), requestCaptor.capture(), maskPatternsCaptor.capture());

    List<Map> maskPatterns = maskPatternsCaptor.getAllValues();

    maskPatterns.forEach(
        maskPatternsMap -> assertThat(((Map<String, String>) maskPatternsMap).containsKey("decryptedApiKey")).isTrue());
    assertThat(requestCaptor.getValue().request().url().toString().contains("apiKey=decryptedApiKey")).isTrue();
  }
}
