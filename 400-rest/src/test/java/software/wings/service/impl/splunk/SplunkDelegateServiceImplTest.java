/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.splunk;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.SRIRAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.SplunkSavedSearch;
import io.harness.cvng.beans.SplunkValidationResponse;
import io.harness.cvng.beans.SplunkValidationResponse.Histogram;
import io.harness.cvng.beans.SplunkValidationResponse.SampleLog;
import io.harness.cvng.beans.SplunkValidationResponse.SplunkSampleResponse;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.JsonUtils;

import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.delegatetasks.DelegateLogService;

import com.splunk.Args;
import com.splunk.Job;
import com.splunk.JobCollection;
import com.splunk.SavedSearch;
import com.splunk.SavedSearchCollection;
import com.splunk.Service;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class SplunkDelegateServiceImplTest extends CategoryTest {
  private SplunkConnectorDTO splunkConnectorDTO;
  private String requestGuid;
  private String accountId;
  @Mock private DelegateLogService delegateLogService;
  @Mock private SecretDecryptionService secretDecryptionService;
  private SplunkDelegateServiceImpl splunkDelegateService;
  @Before
  public void setUp() throws IllegalAccessException {
    accountId = generateUuid();
    splunkConnectorDTO = SplunkConnectorDTO.builder()
                             .accountId(accountId)
                             .splunkUrl("https://input-prd-p-429h4vj2lsng.cloud.splunk.com:8089")
                             .username("123")
                             .passwordRef(SecretRefData.builder()
                                              .identifier("identifier")
                                              .scope(Scope.ACCOUNT)
                                              .decryptedValue("123".toCharArray())
                                              .build())
                             .build();
    requestGuid = generateUuid();
    splunkDelegateService = new SplunkDelegateServiceImpl();
    FieldUtils.writeField(splunkDelegateService, "delegateLogService", delegateLogService, true);
    FieldUtils.writeField(splunkDelegateService, "secretDecryptionService", secretDecryptionService, true);
    splunkDelegateService = spy(splunkDelegateService);
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void initSplunkService() {
    splunkDelegateService.initSplunkService(splunkConnectorDTO);
    verify(splunkDelegateService, times(1))
        .initSplunkServiceWithToken(splunkConnectorDTO.getUsername(),
            splunkConnectorDTO.getPasswordRef().getDecryptedValue(), splunkConnectorDTO.getSplunkUrl());
    verify(splunkDelegateService, times(1))
        .initSplunkServiceWithBasicAuth(splunkConnectorDTO.getUsername(),
            splunkConnectorDTO.getPasswordRef().getDecryptedValue(), splunkConnectorDTO.getSplunkUrl());
  }

  @Test(expected = Exception.class)
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void initSplunkServiceOnlyToken() {
    when(splunkDelegateService.initSplunkServiceWithToken(splunkConnectorDTO.getUsername(),
             splunkConnectorDTO.getPasswordRef().getDecryptedValue(), splunkConnectorDTO.getSplunkUrl()))
        .thenReturn(mock(Service.class));
    splunkDelegateService.initSplunkService(splunkConnectorDTO);
    verify(splunkDelegateService, times(1))
        .initSplunkServiceWithToken(splunkConnectorDTO.getUsername(),
            splunkConnectorDTO.getPasswordRef().getDecryptedValue(), splunkConnectorDTO.getSplunkUrl());
    verify(splunkDelegateService, times(1))
        .initSplunkServiceWithBasicAuth(splunkConnectorDTO.getUsername(),
            splunkConnectorDTO.getPasswordRef().getDecryptedValue(), splunkConnectorDTO.getSplunkUrl());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSavedSearches_withCorrectResults() {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any());
    SavedSearchCollection savedSearchCollection = mock(SavedSearchCollection.class);
    SavedSearch savedSearch = mock(SavedSearch.class);
    when(savedSearch.getSearch()).thenReturn("search query");
    when(savedSearch.getTitle()).thenReturn("search query title");
    when(savedSearchCollection.values()).thenReturn(Lists.newArrayList(savedSearch));
    when(service.getSavedSearches(any(Args.class))).thenReturn(savedSearchCollection);
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(splunkConnectorDTO);
    List<SplunkSavedSearch> splunkSavedSearches =
        splunkDelegateService.getSavedSearches(splunkConnectorDTO, new ArrayList<>(), requestGuid);
    assertThat(splunkSavedSearches).hasSize(1);
    assertThat(splunkSavedSearches.get(0).getSearchQuery()).isEqualTo("search query");
    assertThat(splunkSavedSearches.get(0).getTitle()).isEqualTo("search query title");
    ThirdPartyApiCallLog apiCallLog = captureThirdPartyAPICallLog();
    assertThat(apiCallLog.getTitle()).isEqualTo("Fetch request to " + splunkConnectorDTO.getSplunkUrl());
    assertThat(apiCallLog.getStateExecutionId()).isEqualTo(requestGuid);
    assertThat(apiCallLog.getRequest())
        .contains(ThirdPartyApiCallField.builder()
                      .name("Url")
                      .type(FieldType.URL)
                      .value("https:/input-prd-p-429h4vj2lsng.cloud.splunk.com:8089/saved/searches")
                      .build());
    assertThat(apiCallLog.getRequest())
        .contains(
            ThirdPartyApiCallField.builder().name("Query").type(FieldType.JSON).value("{\"app\":\"search\"}").build());
    assertThat(apiCallLog.getResponse())
        .contains(ThirdPartyApiCallField.builder()
                      .name("Response Body")
                      .type(FieldType.JSON)
                      .value("[{\"title\":\"search query title\",\"searchQuery\":\"search query\"}]")
                      .build());
    assertThat(apiCallLog.getResponse()).hasSize(2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSavedSearches_withException() {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any());
    SavedSearchCollection savedSearchCollection = mock(SavedSearchCollection.class);
    SavedSearch savedSearch = mock(SavedSearch.class);
    when(savedSearch.getSearch()).thenReturn("search query");
    when(savedSearch.getTitle()).thenReturn("search query title");
    when(savedSearchCollection.values()).thenReturn(Lists.newArrayList(savedSearch));
    when(service.getSavedSearches(any(Args.class))).thenThrow(new RuntimeException("from test"));
    assertThatThrownBy(() -> splunkDelegateService.getSavedSearches(splunkConnectorDTO, new ArrayList<>(), requestGuid))
        .hasMessage("from test");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetHistogram() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any());
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any()))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_histogram_query.json"));
    Histogram histogram = splunkDelegateService.getHistogram(splunkConnectorDTO, "exception", requestGuid);
    verify(jobCollection).create(eq("search exception | timechart count span=6h | table _time, count"), any());
    assertThat(histogram.getQuery()).isEqualTo("exception");
    assertThat(histogram.getBars()).hasSize(29);
    assertThat(histogram.getBars().get(0))
        .isEqualTo(Histogram.Bar.builder().count(10).timestamp(1589889600000L).build());
    assertThat(histogram.getCount()).isEqualTo(389L);
    ThirdPartyApiCallLog apiCallLog = captureThirdPartyAPICallLog();
    assertThat(apiCallLog.getTitle()).isEqualTo("Fetch request to " + splunkConnectorDTO.getSplunkUrl());
    assertThat(apiCallLog.getStateExecutionId()).isEqualTo(requestGuid);
    assertThat(apiCallLog.getRequest())
        .contains(ThirdPartyApiCallField.builder()
                      .name("Url")
                      .type(FieldType.URL)
                      .value(splunkConnectorDTO.getSplunkUrl())
                      .build());
    assertThat(apiCallLog.getRequest())
        .contains(ThirdPartyApiCallField.builder()
                      .name("Query")
                      .type(FieldType.TEXT)
                      .value("search exception | timechart count span=6h | table _time, count")
                      .build());
    assertThat(apiCallLog.getResponse()).hasSize(2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSamples() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any());
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any()))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_samples_query.json"));
    SplunkSampleResponse samples = splunkDelegateService.getSamples(splunkConnectorDTO, "exception", requestGuid);
    verify(jobCollection).create(eq("search exception | head 10"), any());
    assertThat(samples.getRawSampleLogs()).hasSize(10);
    assertThat(samples.getRawSampleLogs().get(0))
        .isEqualTo(
            SampleLog.builder()
                .raw(
                    "2020-05-26 18:10:39,278 [GitChangeSet] INFO  software.wings.yaml.gitSync.GitChangeSetRunnable - Not continuing with GitChangeSetRunnable job")
                .timestamp(1590059151845L)
                .build());
    assertThat(samples.getSample())
        .isEqualTo(JsonUtils.asObject("{\n"
                + "            \"linecount\": \"1\",\n"
                + "            \"splunk_server\": \"splunk-dev\",\n"
                + "            \"host\": \"splunk-dev\",\n"
                + "            \"index\": \"main\",\n"
                + "            \"sourcetype\": \"delegate-logs-local\",\n"
                + "            \"source\": \"delegate.2020-05-21.log\"\n"
                + "        }",
            HashMap.class));
    ThirdPartyApiCallLog apiCallLog = captureThirdPartyAPICallLog();
    assertThat(apiCallLog.getTitle()).isEqualTo("Fetch request to " + splunkConnectorDTO.getSplunkUrl());
    assertThat(apiCallLog.getStateExecutionId()).isEqualTo(requestGuid);
    assertThat(apiCallLog.getRequest())
        .contains(ThirdPartyApiCallField.builder()
                      .name("Url")
                      .type(FieldType.URL)
                      .value(splunkConnectorDTO.getSplunkUrl())
                      .build());
    assertThat(apiCallLog.getRequest())
        .contains(ThirdPartyApiCallField.builder()
                      .name("Query")
                      .type(FieldType.TEXT)
                      .value("search exception | head 10")
                      .build());
    assertThat(apiCallLog.getResponse()).hasSize(2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetValidationResponse() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any());
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any()))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_samples_query.json"))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_histogram_query.json"));
    SplunkValidationResponse validationResponse =
        splunkDelegateService.getValidationResponse(splunkConnectorDTO, new ArrayList<>(), "exception", requestGuid);
    verify(jobCollection).create(eq("search exception | head 10"), any());
    assertThat(validationResponse.getQueryDurationMillis()).isEqualTo(Duration.ofDays(7).toMillis());
    assertThat(validationResponse.getHistogram()).isNotNull();
    assertThat(validationResponse.getSamples()).isNotNull();
    assertThat(validationResponse.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetValidationResponse_emptyQuery() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any());
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any()))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_samples_query.json"))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_histogram_query.json"));

    assertThatThrownBy(
        () -> splunkDelegateService.getValidationResponse(splunkConnectorDTO, new ArrayList<>(), "", requestGuid))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("query can not be empty");
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetValidationResponse_withErrorMessage() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any());
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any()))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_samples_invalid_query.json"))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_histogram_query.json"));
    SplunkValidationResponse validationResponse =
        splunkDelegateService.getValidationResponse(splunkConnectorDTO, new ArrayList<>(), "exception", requestGuid);
    verify(jobCollection).create(eq("search exception | head 10"), any());
    assertThat(validationResponse.getQueryDurationMillis()).isEqualTo(Duration.ofDays(7).toMillis());
    assertThat(validationResponse.getHistogram()).isNotNull();
    assertThat(validationResponse.getSamples()).isNotNull();
    assertThat(validationResponse.getErrorMessage())
        .isEqualTo("Wrong kind of splunk query. Query should have raw(_raw field) message. Please check the query");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSamples_withoutRawLogs() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any());
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any()))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_samples_invalid_query.json"));
    SplunkSampleResponse samples = splunkDelegateService.getSamples(splunkConnectorDTO,
        "index=_internal source=\"*metrics.log\" eps \"group=per_source_thruput\" NOT filetracker | eval events=eps*kb/kbps | timechart fixedrange=t span=1m limit=5 sum(events) by series",
        requestGuid);
    verify(jobCollection)
        .create(
            eq("search index=_internal source=\"*metrics.log\" eps \"group=per_source_thruput\" NOT filetracker | eval events=eps*kb/kbps | timechart fixedrange=t span=1m limit=5 sum(events) by series | head 10"),
            any());
    assertThat(samples.getRawSampleLogs()).hasSize(0);
    assertThat(samples.getSample())
        .isEqualTo(JsonUtils.asObject("{\n"
                + "  \"/opt/splunk/var/log/introspection/resource_usage.log\": \"27.0000000\",\n"
                + "  \"/opt/splunk/var/log/splunk/health.log\": \"6.00000000\",\n"
                + "  \"/opt/splunk/var/log/splunk/metrics.log\": \"80.00000000\",\n"
                + "  \"OTHER\": \"15.0000000000\",\n"
                + "  \"http:httpcollector\": \"9.00000000000\"\n"
                + "}",
            HashMap.class));
    assertThat(samples.getErrorMessage())
        .isEqualTo("Wrong kind of splunk query. Query should have raw(_raw field) message. Please check the query");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSample_withEmptyResults() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any());
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any())).thenReturn(getSplunkJsonResponseInputStream("splunk_json_search_response_empty.json"));
    SplunkSampleResponse samples = splunkDelegateService.getSamples(splunkConnectorDTO, "exception", requestGuid);
    verify(jobCollection).create(eq("search exception | head 10"), any());
    assertThat(samples.getRawSampleLogs()).isEmpty();
    assertThat(samples.getSample()).isEmpty();
  }

  private ThirdPartyApiCallLog captureThirdPartyAPICallLog() {
    ArgumentCaptor<ThirdPartyApiCallLog> captor = ArgumentCaptor.forClass(ThirdPartyApiCallLog.class);
    verify(delegateLogService, times(1)).save(eq(accountId), captor.capture());
    return captor.getValue();
  }

  private InputStream getSplunkJsonResponseInputStream(String jsonFile) throws IOException {
    String splunkResponse =
        IOUtils.toString(SplunkDelegateServiceImpl.class.getResourceAsStream(jsonFile), StandardCharsets.UTF_8.name());
    return IOUtils.toInputStream(splunkResponse, Charset.defaultCharset());
  }
}
