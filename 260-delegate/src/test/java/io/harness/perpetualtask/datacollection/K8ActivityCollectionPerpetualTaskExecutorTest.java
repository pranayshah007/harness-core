/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.CVNGRequestExecutor;
import io.harness.cvng.beans.DataCollectionType;
import io.harness.cvng.beans.K8ActivityDataCollectionInfo;
import io.harness.cvng.beans.activity.KubernetesActivitySourceDTO;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.datacollection.k8s.ChangeIntelSharedInformerFactory;
import io.harness.perpetualtask.k8s.watch.K8sWatchServiceDelegate;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.delegatetasks.cvng.K8InfoDataService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

public class K8ActivityCollectionPerpetualTaskExecutorTest extends DelegateTestBase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private K8ActivityCollectionPerpetualTaskExecutor dataCollector = new K8ActivityCollectionPerpetualTaskExecutor();
  @Mock private K8InfoDataService k8InfoDataService;
  @Mock private ApiClientFactory apiClientFactory;
  @Mock private ChangeIntelSharedInformerFactory changeIntelSharedInformerFactory;
  @Mock private ApiClient apiClient;
  @Mock private CVNGRequestExecutor cvngRequestExecutor;
  @Inject private Injector injector;
  private KubernetesClusterConfigDTO kubernetesClusterConfigDTO;
  private String accountId;

  private PerpetualTaskExecutionParams perpetualTaskParams;

  @Inject KryoSerializer kryoSerializer;

  @Before
  public void setup() throws IllegalAccessException, IOException {
    apiClient = Mockito.mock(ApiClient.class);
    when(apiClient.setVerifyingSsl(anyBoolean())).thenReturn(apiClient);
    when(apiClient.escapeString(anyString())).thenReturn(generateUuid());
    when(apiClientFactory.getClient(any())).thenReturn(apiClient);
    on(dataCollector).set("kryoSerializer", kryoSerializer);
    accountId = generateUuid();

    SecretRefData secretRefData = SecretRefData.builder()
                                      .scope(Scope.ACCOUNT)
                                      .identifier("secret")
                                      .decryptedValue(generateUuid().toCharArray())
                                      .build();
    kubernetesClusterConfigDTO =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(KubernetesCredentialType.MANUAL_CREDENTIALS)
                            .config(KubernetesClusterDetailsDTO.builder()
                                        .masterUrl(generateUuid())
                                        .auth(KubernetesAuthDTO.builder()
                                                  .authType(KubernetesAuthType.USER_PASSWORD)
                                                  .credentials(KubernetesUserNamePasswordDTO.builder()
                                                                   .username(generateUuid())
                                                                   .passwordRef(secretRefData)
                                                                   .build())
                                                  .build())
                                        .build())
                            .build())
            .build();
    when(cvngRequestExecutor.executeWithRetry(any(Call.class)))
        .thenReturn(new RestResponse<>(KubernetesActivitySourceDTO.builder()
                                           .activitySourceConfigs(Sets.newHashSet(
                                               KubernetesActivitySourceDTO.KubernetesActivitySourceConfig.builder()
                                                   .namespace(generateUuid())
                                                   .workloadName(generateUuid())
                                                   .build()))
                                           .build()));
    when(changeIntelSharedInformerFactory.createInformerFactoryWithHandlers(any(), anyString(), any(), any()))
        .thenReturn(new SharedInformerFactory());
    FieldUtils.writeField(dataCollector, "k8InfoDataService", k8InfoDataService, true);
    FieldUtils.writeField(dataCollector, "changeIntelSharedInformerFactory", changeIntelSharedInformerFactory, true);
    FieldUtils.writeField(dataCollector, "apiClientFactory", apiClientFactory, true);
    FieldUtils.writeField(dataCollector, "injector", injector, true);
  }

  private void createTaskParams() {
    List<List<EncryptedDataDetail>> encryptedDataDetailList = new ArrayList<>();
    encryptedDataDetailList.add(new ArrayList());
    encryptedDataDetailList.get(0).add(EncryptedDataDetail.builder().build());

    K8ActivityDataCollectionInfo k8ActivityDataCollectionInfo =
        K8ActivityDataCollectionInfo.builder()
            .connectorConfigDTO(kubernetesClusterConfigDTO)
            .encryptedDataDetails(encryptedDataDetailList)
            .dataCollectionType(DataCollectionType.KUBERNETES)
            .activitySourceDTO(KubernetesActivitySourceDTO.builder()
                                   .activitySourceConfigs(Sets.newHashSet(
                                       KubernetesActivitySourceDTO.KubernetesActivitySourceConfig.builder()
                                           .namespace(generateUuid())
                                           .workloadName(generateUuid())
                                           .build()))
                                   .build())
            .build();
    ByteString bytes = ByteString.copyFrom(kryoSerializer.asBytes(k8ActivityDataCollectionInfo));
    perpetualTaskParams = PerpetualTaskExecutionParams.newBuilder()
                              .setCustomizedParams(Any.pack(K8ActivityCollectionPerpetualTaskParams.newBuilder()
                                                                .setAccountId(accountId)
                                                                .setDataCollectionInfo(bytes)
                                                                .build()))
                              .build();
  }

  @Test
  @Owner(developers = OwnerRule.RAGHU)
  @Category({UnitTests.class})
  public void testDataCollection_executeWatch() {
    //    createTaskParams();
    //    dataCollector.runOnce(PerpetualTaskId.newBuilder().build(), perpetualTaskParams, Instant.now());
    //    verifyCalls();
  }

  @Test
  @Owner(developers = OwnerRule.RAGHU)
  @Category({UnitTests.class})
  public void testDataCollection_cleanUp() throws IllegalAccessException {
    final Map<String, K8sWatchServiceDelegate.WatcherGroup> watchMap = new ConcurrentHashMap<>();
    FieldUtils.writeField(dataCollector, "watchMap", watchMap, true);
    String taskId = generateUuid();
    createTaskParams();
    dataCollector.runOnce(PerpetualTaskId.newBuilder().setId(taskId).build(), perpetualTaskParams, Instant.now());
    assertThat(watchMap.containsKey(taskId)).isTrue();
    dataCollector.cleanup(PerpetualTaskId.newBuilder().setId(taskId).build(), perpetualTaskParams);
    assertThat(watchMap.containsKey(taskId)).isFalse();
  }

  private void verifyCalls() {
    ArgumentCaptor<KubernetesConfig> kubernetesConfigArgumentCaptor = ArgumentCaptor.forClass(KubernetesConfig.class);
    ArgumentCaptor<RuntimeParameters> runtimeParams = ArgumentCaptor.forClass(RuntimeParameters.class);
    verify(apiClientFactory).getClient(kubernetesConfigArgumentCaptor.capture());
  }
}
