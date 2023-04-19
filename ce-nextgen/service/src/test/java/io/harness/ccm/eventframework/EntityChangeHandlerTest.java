/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.eventframework;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.K8sEventCollectionBundle;
import io.harness.ccm.cluster.NGClusterRecordHandler;
import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.perpetualtask.K8sWatchTaskResourceClient;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CE)
public class EntityChangeHandlerTest extends CategoryTest {
  public static final String TASK_ID = "taskId";
  public static final String NAME = "name";
  public static final String CONNECTOR_REF = "ConnectorRef";
  private String ACCOUNT_ID = "accountId";
  private static final String CE_K8S_CONNECTOR_ID = "ceK8sConnectorIdentifier";
  private static final String BASE_K8S_CONNECTOR_ID = "baseK8sConnectorIdentifier";

  private static final String CLUSTER_NAME = "clusterName";

  @Mock NGClusterRecordHandler clusterRecordHandler;
  @Mock K8sWatchTaskResourceClient k8sWatchTaskResourceClient;
  @Mock FeatureFlagService featureFlagService;
  @Mock Call<ResponseDTO<String>> responseDTOCall;
  @Mock Call<ResponseDTO<Boolean>> booleanResponseCall;

  @InjectMocks @Spy EntityChangeHandler entityChangeHandler;
  EntityChangeDTO entityChangeDTO;
  EntityChangeDTO baseK8sEntityChangeDTO;
  String ceK8sConnectorType;
  ClusterRecord clusterRecord;

  @Before
  public void setup() throws IOException {
    entityChangeDTO = EntityChangeDTO.newBuilder()
                          .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                          .setIdentifier(StringValue.of(CE_K8S_CONNECTOR_ID))
                          .setOrgIdentifier(StringValue.of(""))
                          .setProjectIdentifier(StringValue.of(""))
                          .build();
    baseK8sEntityChangeDTO = EntityChangeDTO.newBuilder()
                                 .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                 .setIdentifier(StringValue.of(BASE_K8S_CONNECTOR_ID))
                                 .setOrgIdentifier(StringValue.of(""))
                                 .setProjectIdentifier(StringValue.of(""))
                                 .build();
    ceK8sConnectorType = ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName();
    clusterRecord = ClusterRecord.builder()
                        .accountId(ACCOUNT_ID)
                        .clusterName(CLUSTER_NAME)
                        .ceK8sConnectorIdentifier(CE_K8S_CONNECTOR_ID)
                        .build();
    doReturn(ConnectorInfoDTO.builder()
                 .name(NAME)
                 .connectorConfig(CEKubernetesClusterConfigDTO.builder()
                                      .featuresEnabled(Collections.singletonList(CEFeatures.VISIBILITY))
                                      .connectorRef(CONNECTOR_REF)
                                      .build())
                 .build())
        .when(entityChangeHandler)
        .getConnectorConfigDTO(entityChangeDTO);
    doReturn(ConnectorInfoDTO.builder()
                 .name(NAME)
                 .connectorConfig(CEKubernetesClusterConfigDTO.builder()
                                      .featuresEnabled(Collections.singletonList(CEFeatures.VISIBILITY))
                                      .connectorRef(CONNECTOR_REF)
                                      .build())
                 .build())
        .when(entityChangeHandler)
        .getConnectorConfigDTO(baseK8sEntityChangeDTO);
    doReturn(true).when(featureFlagService).isEnabled(any(), any());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void handleCreateEventCEK8sConnector() throws Exception {
    when(clusterRecordHandler.handleNewCEK8sConnectorCreate(any())).thenReturn(clusterRecord);
    when(clusterRecordHandler.attachPerpetualTask(any(), any())).thenReturn(null);
    when(k8sWatchTaskResourceClient.create(any(), any())).thenReturn(responseDTOCall);
    ResponseDTO<String> responseDTO = ResponseDTO.newResponse(TASK_ID);
    Response<ResponseDTO<String>> response = Response.success(responseDTO);
    when(responseDTOCall.execute()).thenReturn(response);

    entityChangeHandler.handleCreateEvent(entityChangeDTO, ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName());
    ArgumentCaptor<K8sEventCollectionBundle> k8sEventCollectionBundleArgumentCaptor =
        ArgumentCaptor.forClass(K8sEventCollectionBundle.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sWatchTaskResourceClient)
        .create(accountIdCaptor.capture(), k8sEventCollectionBundleArgumentCaptor.capture());
    K8sEventCollectionBundle k8sEventCollectionBundle = k8sEventCollectionBundleArgumentCaptor.getValue();
    assertThat(accountIdCaptor.getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(k8sEventCollectionBundle.getClusterName()).isEqualTo(CLUSTER_NAME);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void handleDeleteEventCEK8sConnector() throws Exception {
    clusterRecord.setPerpetualTaskId(TASK_ID);
    when(clusterRecordHandler.getClusterRecord(ACCOUNT_ID, CE_K8S_CONNECTOR_ID)).thenReturn(clusterRecord);
    when(clusterRecordHandler.deleteClusterRecord(any(), any())).thenReturn(true);
    doReturn(true).when(entityChangeHandler).deletePerpetualTask(clusterRecord, TASK_ID);

    entityChangeHandler.handleDeleteEvent(entityChangeDTO, ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName());

    ArgumentCaptor<K8sEventCollectionBundle> k8sEventCollectionBundleArgumentCaptor =
        ArgumentCaptor.forClass(K8sEventCollectionBundle.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> ceK8sConnectorIdCaptor = ArgumentCaptor.forClass(String.class);

    verify(clusterRecordHandler).deleteClusterRecord(accountIdCaptor.capture(), ceK8sConnectorIdCaptor.capture());

    assertThat(accountIdCaptor.getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(ceK8sConnectorIdCaptor.getValue()).isEqualTo(CE_K8S_CONNECTOR_ID);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void handleUpdateEventCEK8sConnectorVisibilityEnabledAndNoUpdatesInCEFeatures() throws Exception {
    when(clusterRecordHandler.handleNewCEK8sConnectorCreate(any())).thenReturn(clusterRecord);
    when(k8sWatchTaskResourceClient.reset(any(), any(), any())).thenReturn(booleanResponseCall);
    ResponseDTO<Boolean> responseDTO = ResponseDTO.newResponse(true);
    Response<ResponseDTO<Boolean>> response = Response.success(responseDTO);
    when(booleanResponseCall.execute()).thenReturn(response);
    clusterRecord.setPerpetualTaskId(TASK_ID);
    when(clusterRecordHandler.getClusterRecord(ACCOUNT_ID, CE_K8S_CONNECTOR_ID)).thenReturn(clusterRecord);

    entityChangeHandler.handleUpdateEvent(entityChangeDTO, ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName());

    ArgumentCaptor<K8sEventCollectionBundle> k8sEventCollectionBundleArgumentCaptor =
        ArgumentCaptor.forClass(K8sEventCollectionBundle.class);
    ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sWatchTaskResourceClient)
        .reset(accountIdCaptor.capture(), taskIdCaptor.capture(), k8sEventCollectionBundleArgumentCaptor.capture());
    K8sEventCollectionBundle k8sEventCollectionBundle = k8sEventCollectionBundleArgumentCaptor.getValue();
    assertThat(accountIdCaptor.getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(taskIdCaptor.getValue()).isEqualTo(TASK_ID);
    assertThat(k8sEventCollectionBundle.getClusterName()).isEqualTo(NAME);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void handleUpdateEventCEK8sConnectorVisibilityEnabledAndDisabledOnUpdate() throws Exception {
    doReturn(ConnectorInfoDTO.builder()
                 .name(NAME)
                 .connectorConfig(CEKubernetesClusterConfigDTO.builder()
                                      .featuresEnabled(Collections.singletonList(CEFeatures.OPTIMIZATION))
                                      .connectorRef(CONNECTOR_REF)
                                      .build())
                 .build())
        .when(entityChangeHandler)
        .getConnectorConfigDTO(entityChangeDTO);

    clusterRecord.setPerpetualTaskId(TASK_ID);
    when(clusterRecordHandler.getClusterRecord(ACCOUNT_ID, CE_K8S_CONNECTOR_ID)).thenReturn(clusterRecord);
    when(clusterRecordHandler.deleteClusterRecord(any(), any())).thenReturn(true);
    doReturn(true).when(entityChangeHandler).deletePerpetualTask(clusterRecord, TASK_ID);

    entityChangeHandler.handleCEK8sUpdate(entityChangeDTO);

    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> ceK8sConnectorIdCaptor = ArgumentCaptor.forClass(String.class);

    verify(clusterRecordHandler).deleteClusterRecord(accountIdCaptor.capture(), ceK8sConnectorIdCaptor.capture());

    assertThat(accountIdCaptor.getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(ceK8sConnectorIdCaptor.getValue()).isEqualTo(CE_K8S_CONNECTOR_ID);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void handleUpdateEventCEK8sConnectorVisibilityDisabledAndEnabledOnUpdate() throws Exception {
    doReturn(ConnectorInfoDTO.builder()
                 .name(NAME)
                 .connectorConfig(CEKubernetesClusterConfigDTO.builder()
                                      .featuresEnabled(Collections.singletonList(CEFeatures.VISIBILITY))
                                      .connectorRef(CONNECTOR_REF)
                                      .build())
                 .build())
        .when(entityChangeHandler)
        .getConnectorConfigDTO(entityChangeDTO);
    when(clusterRecordHandler.getClusterRecord(ACCOUNT_ID, CE_K8S_CONNECTOR_ID)).thenReturn(null);
    when(clusterRecordHandler.handleNewCEK8sConnectorCreate(any())).thenReturn(clusterRecord);
    when(clusterRecordHandler.attachPerpetualTask(any(), any())).thenReturn(null);
    when(k8sWatchTaskResourceClient.create(any(), any())).thenReturn(responseDTOCall);
    ResponseDTO<String> responseDTO = ResponseDTO.newResponse(TASK_ID);
    Response<ResponseDTO<String>> response = Response.success(responseDTO);
    when(responseDTOCall.execute()).thenReturn(response);

    entityChangeHandler.handleCEK8sUpdate(entityChangeDTO);
    ArgumentCaptor<K8sEventCollectionBundle> k8sEventCollectionBundleArgumentCaptor =
        ArgumentCaptor.forClass(K8sEventCollectionBundle.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sWatchTaskResourceClient)
        .create(accountIdCaptor.capture(), k8sEventCollectionBundleArgumentCaptor.capture());
    K8sEventCollectionBundle k8sEventCollectionBundle = k8sEventCollectionBundleArgumentCaptor.getValue();
    assertThat(accountIdCaptor.getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(k8sEventCollectionBundle.getClusterName()).isEqualTo(CLUSTER_NAME);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void handleUpdateEventBaseK8sConnector() throws Exception {
    when(k8sWatchTaskResourceClient.reset(any(), any(), any())).thenReturn(booleanResponseCall);
    ResponseDTO<Boolean> responseDTO = ResponseDTO.newResponse(true);
    Response<ResponseDTO<Boolean>> response = Response.success(responseDTO);
    when(booleanResponseCall.execute()).thenReturn(response);
    clusterRecord.setPerpetualTaskId(TASK_ID);

    when(clusterRecordHandler.getClusterRecordFromK8sBaseConnector(ACCOUNT_ID, BASE_K8S_CONNECTOR_ID))
        .thenReturn(clusterRecord);

    entityChangeHandler.handleUpdateEvent(baseK8sEntityChangeDTO, ConnectorType.KUBERNETES_CLUSTER.getDisplayName());

    ArgumentCaptor<K8sEventCollectionBundle> k8sEventCollectionBundleArgumentCaptor =
        ArgumentCaptor.forClass(K8sEventCollectionBundle.class);
    ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sWatchTaskResourceClient)
        .reset(accountIdCaptor.capture(), taskIdCaptor.capture(), k8sEventCollectionBundleArgumentCaptor.capture());
    K8sEventCollectionBundle k8sEventCollectionBundle = k8sEventCollectionBundleArgumentCaptor.getValue();
    assertThat(accountIdCaptor.getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(taskIdCaptor.getValue()).isEqualTo(TASK_ID);
    assertThat(k8sEventCollectionBundle.getClusterName()).isEqualTo(CLUSTER_NAME);
  }
}
