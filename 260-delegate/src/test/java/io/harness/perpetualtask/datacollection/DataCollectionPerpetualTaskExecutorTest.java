/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.CVNGRequestExecutor;
import io.harness.cvng.beans.AppDynamicsDataCollectionInfo;
import io.harness.cvng.beans.CVDataCollectionInfo;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.MetricPackDTO.MetricDefinitionDTO;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.data.structure.CollectionUtils;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.service.TimeSeriesDataStoreService;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.KryoSerializer;
import io.harness.verificationclient.CVNextGenServiceClient;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CV)
public class DataCollectionPerpetualTaskExecutorTest extends DelegateTestBase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private DataCollectionPerpetualTaskExecutor dataCollector;
  @Mock private TimeSeriesDataStoreService timeSeriesDataStoreService;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private DataCollectionDSLService dataCollectionDSLService;
  @Mock private CVNextGenServiceClient cvNextGenServiceClient;
  private CVNGRequestExecutor cvngRequestExecutor;
  private AppDynamicsConnectorDTO appDynamicsConnectorDTO;
  private String accountId;
  private DataCollectionTaskDTO dataCollectionTaskDTO;
  private AppDynamicsDataCollectionInfo dataCollectionInfo;

  private PerpetualTaskExecutionParams perpetualTaskParams;

  @Inject KryoSerializer kryoSerializer;

  @Before
  public void setup() throws IllegalAccessException, IOException {
    dataCollector = Mockito.spy(new DataCollectionPerpetualTaskExecutor());
    on(dataCollector).set("kryoSerializer", kryoSerializer);
    cvngRequestExecutor = new CVNGRequestExecutor();
    FieldUtils.writeField(dataCollector, "cvngRequestExecutor", cvngRequestExecutor, true);
    FieldUtils.writeField(cvngRequestExecutor, "executorService", Executors.newFixedThreadPool(1), true);
    FieldUtils.writeField(dataCollector, "parallelExecutor", Executors.newFixedThreadPool(1), true);
    accountId = generateUuid();
    SecretRefData secretRefData = SecretRefData.builder()
                                      .scope(Scope.ACCOUNT)
                                      .identifier("secret")
                                      .decryptedValue(generateUuid().toCharArray())
                                      .build();
    appDynamicsConnectorDTO = AppDynamicsConnectorDTO.builder()
                                  .accountname(generateUuid())
                                  .username(generateUuid())
                                  .controllerUrl(generateUuid())
                                  .passwordRef(secretRefData)
                                  .build();

    FieldUtils.writeField(dataCollector, "secretDecryptionService", secretDecryptionService, true);
    FieldUtils.writeField(dataCollector, "timeSeriesDataStoreService", timeSeriesDataStoreService, true);
    FieldUtils.writeField(dataCollector, "dataCollectionDSLService", dataCollectionDSLService, true);
    FieldUtils.writeField(dataCollector, "cvNextGenServiceClient", cvNextGenServiceClient, true);
    dataCollectionInfo =
        AppDynamicsDataCollectionInfo.builder().applicationName("cv-app").tierName("docker-tier").build();
    dataCollectionTaskDTO = DataCollectionTaskDTO.builder()
                                .accountId(accountId)
                                .startTime(Instant.now().minusSeconds(60))
                                .endTime(Instant.now())
                                .dataCollectionInfo(dataCollectionInfo)
                                .build();
    Call<RestResponse<List<DataCollectionTaskDTO>>> nextTaskCall = mock(Call.class);
    Call<RestResponse<List<DataCollectionTaskDTO>>> nullCall = mock(Call.class);
    when(nextTaskCall.clone()).thenReturn(nextTaskCall);
    when(nullCall.clone()).thenReturn(nullCall);
    when(nextTaskCall.execute())
        .thenReturn(Response.success(new RestResponse<>(Lists.newArrayList(dataCollectionTaskDTO))));
    when(nullCall.execute()).thenReturn(Response.success(new RestResponse<>(Lists.newArrayList())));

    when(cvNextGenServiceClient.getNextDataCollectionTasks(anyString(), anyString()))
        .thenReturn(nextTaskCall)
        .thenReturn(nullCall);
    Call<RestResponse<Void>> taskUpdateResult = mock(Call.class);
    when(taskUpdateResult.clone()).thenReturn(taskUpdateResult);
    when(taskUpdateResult.execute()).thenReturn(Response.success(new RestResponse<>(null)));
    when(cvNextGenServiceClient.updateTaskStatus(anyString(), any())).thenReturn(taskUpdateResult);
  }

  private void createTaskParams(String metricPackIdentifier, String dataCollectionDsl) {
    dataCollectionInfo.setMetricPack(
        MetricPackDTO.builder()
            .identifier(metricPackIdentifier)
            .metrics(
                Sets.newHashSet(MetricDefinitionDTO.builder().name(generateUuid()).path("path1").included(true).build(),
                    MetricDefinitionDTO.builder().name(generateUuid()).path("path2").included(false).build(),
                    MetricDefinitionDTO.builder().name(generateUuid()).path("path3").included(true).build()))
            .build());

    dataCollectionInfo.setDataCollectionDsl(dataCollectionDsl);
    List<List<EncryptedDataDetail>> encryptedDataDetailList = new ArrayList<>();
    encryptedDataDetailList.add(new ArrayList());
    encryptedDataDetailList.get(0).add(EncryptedDataDetail.builder().build());

    CVDataCollectionInfo cvDataCollectionInfo = CVDataCollectionInfo.builder()
                                                    .connectorConfigDTO(appDynamicsConnectorDTO)
                                                    .encryptedDataDetails(encryptedDataDetailList)
                                                    .build();
    ByteString bytes = ByteString.copyFrom(kryoSerializer.asBytes(cvDataCollectionInfo));
    perpetualTaskParams = PerpetualTaskExecutionParams.newBuilder()
                              .setCustomizedParams(Any.pack(DataCollectionPerpetualTaskParams.newBuilder()
                                                                .setAccountId(accountId)
                                                                .setDataCollectionInfo(bytes)
                                                                .build()))
                              .build();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category({UnitTests.class})
  public void testDataCollection_executeDSL() {
    createTaskParams(CVNextGenConstants.PERFORMANCE_PACK_IDENTIFIER, "dsl");
    dataCollector.runOnce(PerpetualTaskId.newBuilder().build(), perpetualTaskParams, Instant.now());
    verifyDsl("dsl");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category({UnitTests.class})
  public void testDataCollection_dslTimeOut() {
    createTaskParams(CVNextGenConstants.PERFORMANCE_PACK_IDENTIFIER, "dsl");
    dataCollector.dataCollectionTimeoutInMilliSeconds = 1;
    Mockito
        .doAnswer(invocation -> {
          Thread.sleep(1000L);
          return null;
        })
        .when(dataCollector)
        .run(any(), any(), any());
    dataCollector.runOnce(PerpetualTaskId.newBuilder().build(), perpetualTaskParams, Instant.now());
    verify(dataCollector).updateStatusWithException(any(), any(), any(TimeoutException.class));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category({UnitTests.class})
  public void testDataCollection_IfTaskReturnedIsNull() throws IOException {
    Call<RestResponse<List<DataCollectionTaskDTO>>> nextTaskCall = mock(Call.class);
    when(nextTaskCall.clone()).thenReturn(nextTaskCall);
    when(nextTaskCall.execute()).thenReturn(Response.success(new RestResponse<>(null)));
    when(cvNextGenServiceClient.getNextDataCollectionTasks(anyString(), anyString())).thenReturn(nextTaskCall);
    createTaskParams(CVNextGenConstants.PERFORMANCE_PACK_IDENTIFIER, "dsl");
    dataCollector.runOnce(PerpetualTaskId.newBuilder().build(), perpetualTaskParams, Instant.now());
    verify(dataCollectionDSLService, times(0)).execute(any(), any(), any());
  }

  private void verifyDsl(String dsl) {
    ArgumentCaptor<String> dslCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<RuntimeParameters> runtimeParams = ArgumentCaptor.forClass(RuntimeParameters.class);
    ArgumentCaptor<ThirdPartyCallHandler> apiCallHandler = ArgumentCaptor.forClass(ThirdPartyCallHandler.class);

    verify(dataCollectionDSLService).execute(dslCaptor.capture(), runtimeParams.capture(), apiCallHandler.capture());

    assertThat(dsl).isNotEmpty();
    assertThat(dslCaptor.getValue()).isEqualTo(dsl);
    RuntimeParameters runtimeParameters = runtimeParams.getValue();
    assertThat(runtimeParameters.getBaseUrl()).isEqualTo(appDynamicsConnectorDTO.getControllerUrl());
    assertThat(runtimeParameters.getCommonHeaders().size()).isEqualTo(2);
    assertThat(runtimeParameters.getCommonHeaders().get("Authorization"))
        .isEqualTo("Basic "
            + Base64.encodeBase64String(
                String
                    .format("%s@%s:%s", appDynamicsConnectorDTO.getUsername(), appDynamicsConnectorDTO.getAccountname(),
                        new String(appDynamicsConnectorDTO.getPasswordRef().getDecryptedValue()))
                    .getBytes(StandardCharsets.UTF_8)));
    Map<String, Object> otherEnvVariables = runtimeParameters.getOtherEnvVariables();
    assertThat(otherEnvVariables.size()).isEqualTo(6);
    assertThat(otherEnvVariables.get("applicationName")).isEqualTo(dataCollectionInfo.getApplicationName());
    assertThat(otherEnvVariables.get("tierName")).isEqualTo(dataCollectionInfo.getTierName());
    assertThat(otherEnvVariables.get("collectHostData")).isEqualTo("false");

    List<String> metricsToCollect = (List<String>) otherEnvVariables.get("metricsToCollect");
    assertThat(CollectionUtils.isEqualCollection(metricsToCollect, Lists.newArrayList("path1", "path3"))).isTrue();
  }
}
