/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static software.wings.common.VerificationConstants.AZURE_BASE_URL;
import static software.wings.common.VerificationConstants.AZURE_TOKEN_URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.time.Timestamp;

import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(HarnessTeam.CV)
public class CustomLogDataCollectionTaskTest extends CategoryTest {
  CustomLogDataCollectionInfo dataCollectionInfo;
  @Mock private LogAnalysisStoreService logAnalysisStoreService;
  @Mock private ScheduledFuture future;
  @Mock private EncryptionService encryptionService;
  @Mock private RequestExecutor requestExecutor;
  private CustomLogDataCollectionTask dataCollectionTask;

  public void setup(Map<String, Map<String, CustomLogResponseMapper>> logDefinition, Set<String> hosts)
      throws Exception {
    String delegateId = UUID.randomUUID().toString();
    String appId = UUID.randomUUID().toString();
    String envId = UUID.randomUUID().toString();
    String waitId = UUID.randomUUID().toString();
    String accountId = UUID.randomUUID().toString();
    String infrastructureMappingId = UUID.randomUUID().toString();
    String timeDuration = "10";
    dataCollectionInfo = getDataCollectionInfo(logDefinition, hosts);

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.CUSTOM_LOG_COLLECTION_TASK.name())
                            .parameters(new Object[] {dataCollectionInfo})
                            .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(timeDuration) + 120))
                            .build();

    dataCollectionTask = new CustomLogDataCollectionTask(
        DelegateTaskPackage.builder().delegateId(delegateId).data(taskData).build(), null, null, null);

    MockitoAnnotations.initMocks(this);
    setupMocks();
  }

  private void setupMocks() throws Exception {
    when(future.cancel(anyBoolean())).thenReturn(true);
    FieldUtils.writeField(dataCollectionTask, "future", future, true);
    FieldUtils.writeField(dataCollectionTask, "logAnalysisStoreService", logAnalysisStoreService, true);
    FieldUtils.writeField(dataCollectionTask, "encryptionService", encryptionService, true);
    FieldUtils.writeField(dataCollectionTask, "requestExecutor", requestExecutor, true);

    when(encryptionService.getDecryptedValue(any(), eq(false))).thenReturn("decryptedApiKey".toCharArray());
  }

  private CustomLogDataCollectionInfo getDataCollectionInfo(
      Map<String, Map<String, CustomLogResponseMapper>> logDefinition, Set<String> hosts) {
    Map<String, String> header = new HashMap<>();
    header.put("Content-Type", "application/json");
    return CustomLogDataCollectionInfo.builder()
        .startTime(12312321123L)
        .collectionFrequency(1)
        .hosts(hosts)
        .encryptedDataDetails(Arrays.asList(EncryptedDataDetail.builder().fieldName("apiKey").build()))
        .startMinute(0)
        .responseDefinition(logDefinition)
        .headers(header)
        .stateType(DelegateStateType.LOG_VERIFICATION)
        .stateExecutionId("12345asdaf")
        .baseUrl("http://ec2-34-227-84-170.compute-1.amazonaws.com:9200/integration-test/")
        .shouldDoHostBasedFiltering(true)
        .build();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetchElkLogs() throws Exception {
    // setup
    String textLoad = Resources.toString(
        CustomLogDataCollectionTaskTest.class.getResource("/apm/elkMultipleHitsResponse.json"), Charsets.UTF_8);
    String searchUrl = "_search?pretty=true&q=*&size=5";
    Map<String, CustomLogResponseMapper> responseMappers = new HashMap<>();
    responseMappers.put("timestamp",
        CustomLogResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath(Arrays.asList("hits.hits[*]._source.timestamp"))
            .timestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .build());
    responseMappers.put("host",
        CustomLogResponseMapper.builder()
            .fieldName("host")
            .jsonPath(Arrays.asList("hits.hits[*]._source.host"))
            .build());
    responseMappers.put("logMessage",
        CustomLogResponseMapper.builder()
            .fieldName("logMessage")
            .jsonPath(Arrays.asList("hits.hits[*]._source.title"))
            .build());
    Map<String, Map<String, CustomLogResponseMapper>> logDefinition = new HashMap<>();
    logDefinition.put(searchUrl, responseMappers);
    setup(logDefinition, new HashSet<>(Arrays.asList("test.hostname.2", "test.hostname.22", "test.hostname.12")));
    when(logAnalysisStoreService.save(any(DelegateStateType.class), anyString(), anyString(), anyString(), anyString(),
             anyString(), anyString(), anyString(), anyString(), any(List.class)))
        .thenReturn(true);
    when(requestExecutor.executeRequest(any(), any(), any())).thenReturn(textLoad);
    // execute
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    Runnable r = dataCollectionTask.getDataCollector(taskResult);
    r.run();

    // verify
    // verify
    ArgumentCaptor<Map> maskPatternsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Call> requestCaptor = ArgumentCaptor.forClass(Call.class);

    verify(requestExecutor, times(3))
        .executeRequest(any(ThirdPartyApiCallLog.class), requestCaptor.capture(), maskPatternsCaptor.capture());
    List<Map> maskPatterns = maskPatternsCaptor.getAllValues();
    List<Call> callsList = requestCaptor.getAllValues();
    maskPatterns.forEach(
        maskPatternsMap -> assertThat(((Map<String, String>) maskPatternsMap).containsKey("decryptedApiKey")).isTrue());
    callsList.forEach(call -> assertThat(call.request().url().toString().contains("apiKey=decryptedApiKey")));

    verify(logAnalysisStoreService, times(1))
        .save(any(DelegateStateType.class), any(), any(), any(), any(), any(), any(), any(), any(), any(List.class));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetchDatadogLogs_validateStartEndTime() throws Exception {
    // setup
    long startMin = TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary());
    String textLoad = Resources.toString(
        CustomLogDataCollectionTaskTest.class.getResource("/apm/elkMultipleHitsResponse.json"), Charsets.UTF_8);
    String searchUrl = "_search?pretty=true&q=*&size=5&startTime=${start_time}&endTime=${end_time}";
    Map<String, CustomLogResponseMapper> responseMappers = new HashMap<>();
    responseMappers.put("timestamp",
        CustomLogResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath(Arrays.asList("hits.hits[*]._source.timestamp"))
            .timestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .build());
    responseMappers.put("host",
        CustomLogResponseMapper.builder()
            .fieldName("host")
            .jsonPath(Arrays.asList("hits.hits[*]._source.host"))
            .build());
    responseMappers.put("logMessage",
        CustomLogResponseMapper.builder()
            .fieldName("logMessage")
            .jsonPath(Arrays.asList("hits.hits[*]._source.title"))
            .build());
    Map<String, Map<String, CustomLogResponseMapper>> logDefinition = new HashMap<>();
    logDefinition.put(searchUrl, responseMappers);
    setup(logDefinition, new HashSet<>(Arrays.asList("test.hostname.2", "test.hostname.22", "test.hostname.12")));
    dataCollectionInfo.setStateType(DelegateStateType.DATA_DOG_LOG);
    dataCollectionInfo.setStartMinute((int) startMin);
    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.CUSTOM_LOG_COLLECTION_TASK.name())
                            .parameters(new Object[] {dataCollectionInfo})
                            .timeout(TimeUnit.MINUTES.toMillis(1 + 120))
                            .build();

    dataCollectionTask = new CustomLogDataCollectionTask(
        DelegateTaskPackage.builder().delegateId(generateUuid()).data(taskData).build(), null, null, null);
    setupMocks();
    when(logAnalysisStoreService.save(any(DelegateStateType.class), anyString(), anyString(), anyString(), anyString(),
             anyString(), anyString(), anyString(), anyString(), any(List.class)))
        .thenReturn(true);
    when(requestExecutor.executeRequest(any(), any(), any())).thenReturn(textLoad);
    // execute
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    Runnable r = dataCollectionTask.getDataCollector(taskResult);
    r.run();

    // verify
    // verify
    ArgumentCaptor<Map> maskPatternsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Call> requestCaptor = ArgumentCaptor.forClass(Call.class);

    verify(requestExecutor, times(3))
        .executeRequest(any(ThirdPartyApiCallLog.class), requestCaptor.capture(), maskPatternsCaptor.capture());
    List<Map> maskPatterns = maskPatternsCaptor.getAllValues();
    List<Call> callsList = requestCaptor.getAllValues();
    maskPatterns.forEach(
        maskPatternsMap -> assertThat(((Map<String, String>) maskPatternsMap).containsKey("decryptedApiKey")).isTrue());
    callsList.forEach(call -> {
      String startTimeParam = call.request().url().queryParameter("startTime");
      String endTimeParam = call.request().url().queryParameter("endTime");
      assertThat(startTimeParam).isEqualTo(String.valueOf(startMin * 60000));
      assertThat(endTimeParam).isEqualTo(String.valueOf((startMin + 1) * 60000));
    });

    verify(logAnalysisStoreService, times(1))
        .save(any(DelegateStateType.class), any(), any(), any(), any(), any(), any(), any(), any(), any(List.class));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetchLogs_shouldNotInspectHosts() throws Exception {
    // setup
    String textLoad = Resources.toString(
        CustomLogDataCollectionTaskTest.class.getResource("/apm/elkMultipleHitsResponse.json"), Charsets.UTF_8);
    String searchUrl = "_search?pretty=true&q=*&size=5";
    Map<String, CustomLogResponseMapper> responseMappers = new HashMap<>();
    responseMappers.put("timestamp",
        CustomLogResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath(Arrays.asList("hits.hits[*]._source.timestamp"))
            .timestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .build());
    responseMappers.put("host",
        CustomLogResponseMapper.builder()
            .fieldName("host")
            .jsonPath(Arrays.asList("hits.hits[*]._source.host"))
            .build());
    responseMappers.put("logMessage",
        CustomLogResponseMapper.builder()
            .fieldName("logMessage")
            .jsonPath(Arrays.asList("hits.hits[*]._source.title"))
            .build());
    Map<String, Map<String, CustomLogResponseMapper>> logDefinition = new HashMap<>();
    logDefinition.put(searchUrl, responseMappers);
    setup(logDefinition, new HashSet<>(Arrays.asList("test.hostname.2", "test.hostname.22", "test.hostname.12")));
    when(logAnalysisStoreService.save(any(DelegateStateType.class), anyString(), anyString(), anyString(), anyString(),
             anyString(), anyString(), anyString(), anyString(), any(List.class)))
        .thenReturn(true);
    when(requestExecutor.executeRequest(any(), any(), any())).thenReturn(textLoad);
    dataCollectionInfo.setShouldDoHostBasedFiltering(false);
    // execute
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    Runnable r = dataCollectionTask.getDataCollector(taskResult);
    r.run();

    // verify
    // verify
    ArgumentCaptor<Map> maskPatternsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Call> requestCaptor = ArgumentCaptor.forClass(Call.class);

    // since the flag is set in datacollectionInfo, the number of invocations should be 1 only.
    verify(requestExecutor, times(1))
        .executeRequest(any(ThirdPartyApiCallLog.class), requestCaptor.capture(), maskPatternsCaptor.capture());
    List<Map> maskPatterns = maskPatternsCaptor.getAllValues();
    List<Call> callsList = requestCaptor.getAllValues();
    maskPatterns.forEach(
        maskPatternsMap -> assertThat(((Map<String, String>) maskPatternsMap).containsKey("decryptedApiKey")).isTrue());
    callsList.forEach(call -> assertThat(call.request().url().toString().contains("apiKey=decryptedApiKey")));

    verify(logAnalysisStoreService, times(1))
        .save(any(DelegateStateType.class), any(), any(), any(), any(), any(), any(), any(), any(), any(List.class));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testFetchLogs_azureRefreshToken() throws Exception {
    // setup
    String textLoad =
        Resources.toString(CustomLogDataCollectionTaskTest.class.getResource("/apm/azuresample.json"), Charsets.UTF_8);
    String searchUrl = "_search?pretty=true&q=*&size=5";
    Map<String, CustomLogResponseMapper> responseMappers = new HashMap<>();
    responseMappers.put("timestamp",
        CustomLogResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath(Arrays.asList("tables[*].rows[*].[1]"))
            .timestampFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'")
            .build());
    responseMappers.put(
        "host", CustomLogResponseMapper.builder().fieldName("host").fieldValue("samplehostname").build());
    responseMappers.put("logMessage",
        CustomLogResponseMapper.builder()
            .fieldName("logMessage")
            .jsonPath(Arrays.asList("tables[*].rows[*].[0]"))
            .build());

    Map<String, Map<String, CustomLogResponseMapper>> logDefinition = new HashMap<>();
    logDefinition.put(searchUrl, responseMappers);
    setup(logDefinition, new HashSet<>(Arrays.asList("test.hostname.2", "test.hostname.22", "test.hostname.12")));

    when(logAnalysisStoreService.save(any(DelegateStateType.class), anyString(), anyString(), anyString(), anyString(),
             anyString(), anyString(), anyString(), anyString(), any(List.class)))
        .thenReturn(true);
    Map tokenResponse = new HashMap();
    tokenResponse.put("access_token", "accessToken");
    when(requestExecutor.executeRequest(any())).thenReturn(tokenResponse);
    when(requestExecutor.executeRequest(any(), any(), any())).thenReturn(textLoad);
    dataCollectionInfo.setShouldDoHostBasedFiltering(false);
    dataCollectionInfo.setBaseUrl(AZURE_BASE_URL);
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().fieldName("client_id").build();
    EncryptedDataDetail encryptedDataDetail2 = EncryptedDataDetail.builder().fieldName("client_secret").build();
    EncryptedDataDetail encryptedDataDetail3 = EncryptedDataDetail.builder().fieldName("tenant_id").build();
    when(encryptionService.getDecryptedValue(encryptedDataDetail, false)).thenReturn("clientId".toCharArray());
    when(encryptionService.getDecryptedValue(encryptedDataDetail2, false)).thenReturn("clientSecret".toCharArray());
    when(encryptionService.getDecryptedValue(encryptedDataDetail3, false)).thenReturn("tenantId".toCharArray());

    dataCollectionInfo.setEncryptedDataDetails(
        Arrays.asList(encryptedDataDetail, encryptedDataDetail2, encryptedDataDetail3));
    Map<String, String> options = new HashMap<>();
    options.put("client_id", "${client_id}");
    options.put("tenant_id", "${tenant_id}");
    options.put("client_secret", "${client_secret}");
    dataCollectionInfo.setOptions(options);

    // execute
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    Runnable r = dataCollectionTask.getDataCollector(taskResult);
    r.run();

    // verify
    // verify
    ArgumentCaptor<Map> maskPatternsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Call> requestCaptor = ArgumentCaptor.forClass(Call.class);
    ArgumentCaptor<Call> tokenRequestCaptor = ArgumentCaptor.forClass(Call.class);

    // since the flag is set in datacollectionInfo, the number of invocations should be 1 only.
    verify(requestExecutor).executeRequest(tokenRequestCaptor.capture());

    Call<Object> request = tokenRequestCaptor.getValue();
    assertThat(request.request().url().toString()).contains(AZURE_TOKEN_URL);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  //@Ignore("Ignored until this test is moved to not use ELK server")
  public void testFetchElkLogsRetry() throws Exception {
    String textLoad = Resources.toString(
        CustomLogDataCollectionTaskTest.class.getResource("/apm/elkMultipleHitsResponse.json"), Charsets.UTF_8);

    // setup

    String searchUrl = "_search?pretty=true&q=*&size=5&apiKey=${apiKey}";
    Map<String, CustomLogResponseMapper> responseMappers = new HashMap<>();
    responseMappers.put("timestamp",
        CustomLogResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath(Arrays.asList("hits.hits[*]._source.timestamp"))
            .timestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .build());
    responseMappers.put("host",
        CustomLogResponseMapper.builder()
            .fieldName("host")
            .jsonPath(Arrays.asList("hits.hits[*]._source.host"))
            .build());
    responseMappers.put("logMessage",
        CustomLogResponseMapper.builder()
            .fieldName("logMessage")
            .jsonPath(Arrays.asList("hits.hits[*]._source.title"))
            .build());
    Map<String, Map<String, CustomLogResponseMapper>> logDefinition = new HashMap<>();
    logDefinition.put(searchUrl, responseMappers);
    setup(logDefinition, new HashSet<>(Arrays.asList("test.hostname.2", "test.hostname.22", "test.hostname.12")));
    when(logAnalysisStoreService.save(
             any(DelegateStateType.class), any(), any(), any(), any(), any(), any(), any(), any(), any(List.class)))
        .thenThrow(new IOException("This is bad"))
        .thenReturn(true);
    when(requestExecutor.executeRequest(any(), any(), any())).thenReturn(textLoad);
    // execute
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    Runnable r = dataCollectionTask.getDataCollector(taskResult);
    r.run();

    // verify
    ArgumentCaptor<Map> maskPatternsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Call> requestCaptor = ArgumentCaptor.forClass(Call.class);

    verify(requestExecutor, times(6))
        .executeRequest(any(ThirdPartyApiCallLog.class), requestCaptor.capture(), maskPatternsCaptor.capture());
    List<Map> maskPatterns = maskPatternsCaptor.getAllValues();
    List<Call> callsList = requestCaptor.getAllValues();
    maskPatterns.forEach(
        maskPatternsMap -> assertThat(((Map<String, String>) maskPatternsMap).containsKey("decryptedApiKey")).isTrue());
    callsList.forEach(call -> assertThat(call.request().url().toString().contains("apiKey=decryptedApiKey")));

    verify(logAnalysisStoreService, times(2))
        .save(any(DelegateStateType.class), any(), any(), any(), any(), any(), any(), any(), any(), any(List.class));
  }
}
