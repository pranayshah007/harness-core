/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection.k8s;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.CVNGRequestExecutor;
import io.harness.cvng.beans.K8ActivityDataCollectionInfo;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata.Action;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata.KubernetesResourceType;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.verificationclient.CVNextGenServiceClient;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1OwnerReferenceBuilder;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetBuilder;
import java.time.OffsetDateTime;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

public class ChangeIntelReplicaSetHandlerTest extends DelegateTestBase {
  @Mock private CVNGRequestExecutor cvngRequestExecutor;
  @Mock private CVNextGenServiceClient cvNextGenServiceClient;

  private String accountId;
  private K8ActivityDataCollectionInfo dataCollectionInfo;
  private ChangeIntelReplicaSetHandler handler;
  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    accountId = generateUuid();
    dataCollectionInfo = K8ActivityDataCollectionInfo.builder()
                             .changeSourceIdentifier("changesourceId")
                             .envIdentifier("envId")
                             .serviceIdentifier("serviceId")
                             .projectIdentifier("projectId")
                             .orgIdentifier("orgId")
                             .build();
    handler = ChangeIntelReplicaSetHandler.builder()
                  .accountId(accountId)
                  .dataCollectionInfo(dataCollectionInfo)
                  .k8sHandlerUtils(new K8sHandlerUtils())
                  .build();

    Call<RestResponse<Boolean>> call = Mockito.mock(Call.class);
    when(cvNextGenServiceClient.saveChangeEvent(anyString(), any(ChangeEventDTO.class))).thenReturn(call);
    when(cvngRequestExecutor.executeWithRetry(any(Call.class))).thenReturn(new RestResponse<>(true));

    FieldUtils.writeField(handler, "cvNextGenServiceClient", cvNextGenServiceClient, true);
    FieldUtils.writeField(handler, "cvngRequestExecutor", cvngRequestExecutor, true);
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnAdd() {
    V1ReplicaSet replicaSet = buildReplicaSet();
    handler.onAdd(replicaSet);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(Action.Add.name());
    assertThat(changeEventMetadata.getResourceType().name()).isEqualTo(KubernetesResourceType.ReplicaSet.name());
    assertThat(changeEventMetadata.getNewYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(replicaSet.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(replicaSet.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnAdd_withOwnerReference() {
    V1ReplicaSet replicaSet = buildReplicaSet();
    replicaSet.getMetadata().addOwnerReferencesItem(new V1OwnerReferenceBuilder().withController(true).build());
    handler.onAdd(replicaSet);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnUpdate() {
    V1ReplicaSet oldReplicaSet = buildReplicaSet();
    V1ReplicaSet newReplicaSet = buildReplicaSet();
    newReplicaSet.getSpec().setReplicas(10);
    handler.onUpdate(oldReplicaSet, newReplicaSet);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(Action.Update.name());
    assertThat(changeEventMetadata.getResourceType().name()).isEqualTo(KubernetesResourceType.ReplicaSet.name());
    assertThat(changeEventMetadata.getOldYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNewYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(newReplicaSet.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(newReplicaSet.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnUpdate_noChanges() {
    V1ReplicaSet oldReplicaSet = buildReplicaSet();
    handler.onUpdate(oldReplicaSet, oldReplicaSet);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnDelete() {
    V1ReplicaSet replicaSet = buildReplicaSet();
    handler.onDelete(replicaSet, false);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(Action.Delete.name());
    assertThat(changeEventMetadata.getResourceType().name()).isEqualTo(KubernetesResourceType.ReplicaSet.name());
    assertThat(changeEventMetadata.getOldYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(replicaSet.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(replicaSet.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnDelete_finalStateUnknown() {
    V1ReplicaSet replicaSet = buildReplicaSet();
    handler.onDelete(replicaSet, true);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  private ChangeEventDTO verifyAndValidate() {
    ArgumentCaptor<ChangeEventDTO> argumentCaptor = ArgumentCaptor.forClass(ChangeEventDTO.class);
    verify(cvNextGenServiceClient).saveChangeEvent(anyString(), argumentCaptor.capture());
    ChangeEventDTO eventDTO = argumentCaptor.getValue();
    assertThat(eventDTO).isNotNull();
    assertThat(eventDTO.getAccountId()).isEqualTo(accountId);
    assertThat(eventDTO.getChangeSourceIdentifier()).isEqualTo(dataCollectionInfo.getChangeSourceIdentifier());
    assertThat(eventDTO.getMonitoredServiceIdentifier()).isEqualTo(dataCollectionInfo.getMonitoredServiceIdentifier());
    assertThat(eventDTO.getEnvIdentifier()).isEqualTo(dataCollectionInfo.getEnvIdentifier());
    assertThat(eventDTO.getServiceIdentifier()).isEqualTo(dataCollectionInfo.getServiceIdentifier());
    assertThat(eventDTO.getOrgIdentifier()).isEqualTo(dataCollectionInfo.getOrgIdentifier());
    assertThat(eventDTO.getType().name()).isEqualTo(ChangeSourceType.KUBERNETES.name());
    return eventDTO;
  }

  private V1ReplicaSet buildReplicaSet() {
    return new V1ReplicaSetBuilder()
        .withNewMetadata()
        .withName("test-name")
        .withNamespace("test-namespace")
        .withUid("test-uid")
        .withCreationTimestamp(OffsetDateTime.now())
        .endMetadata()
        .withNewSpec()
        .withReplicas(5)
        .withNewTemplate()
        .withNewSpec()
        .addNewInitContainer()
        .withName("init-container-1")
        .endInitContainer()
        .addNewContainer()
        .withName("container-1")
        .withNewResources()
        .addToRequests("cpu", Quantity.fromString("100m"))
        .addToRequests("memory", Quantity.fromString("100Mi"))
        .addToLimits("cpu", Quantity.fromString("200m"))
        .addToLimits("memory", Quantity.fromString("200Mi"))
        .endResources()
        .endContainer()
        .addNewContainer()
        .withName("container-2")
        .withNewResources()
        .addToRequests("cpu", Quantity.fromString("750m"))
        .addToRequests("memory", Quantity.fromString("1Gi"))
        .addToLimits("cpu", Quantity.fromString("1500m"))
        .addToLimits("memory", Quantity.fromString("2Gi"))
        .endResources()
        .endContainer()
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }
}
