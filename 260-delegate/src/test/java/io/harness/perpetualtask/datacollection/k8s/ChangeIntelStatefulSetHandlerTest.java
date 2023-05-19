/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1OwnerReferenceBuilder;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1StatefulSetBuilder;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChangeIntelStatefulSetHandlerTest extends ChangeIntelHandlerTestBase {
  private ChangeIntelStatefulSetHandler handler;
  @Before
  public void setup() throws Exception {
    super.setup();
    handler = ChangeIntelStatefulSetHandler.builder()
                  .accountId(accountId)
                  .dataCollectionInfo(dataCollectionInfo)
                  .k8sHandlerUtils(new K8sHandlerUtils())
                  .build();
    FieldUtils.writeField(handler, "cvNextGenServiceClient", cvNextGenServiceClient, true);
    FieldUtils.writeField(handler, "cvngRequestExecutor", cvngRequestExecutor, true);
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnAdd() {
    V1StatefulSet v1StatefulSet = builsStatefulSet();
    handler.onAdd(v1StatefulSet);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(KubernetesChangeEventMetadata.Action.Add.name());
    assertThat(changeEventMetadata.getResourceType().name())
        .isEqualTo(KubernetesChangeEventMetadata.KubernetesResourceType.StatefulSet.name());
    assertThat(changeEventMetadata.getNewYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(v1StatefulSet.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(v1StatefulSet.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnAdd_withOwnerReference() {
    V1StatefulSet v1StatefulSet = builsStatefulSet();
    v1StatefulSet.getMetadata().addOwnerReferencesItem(new V1OwnerReferenceBuilder().withController(true).build());
    handler.onAdd(v1StatefulSet);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnUpdate() {
    V1StatefulSet oldStatefulSet = builsStatefulSet();
    V1StatefulSet newStatefulSet = builsStatefulSet();
    newStatefulSet.getSpec().setReplicas(10);
    handler.onUpdate(oldStatefulSet, newStatefulSet);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(KubernetesChangeEventMetadata.Action.Update.name());
    assertThat(changeEventMetadata.getResourceType().name())
        .isEqualTo(KubernetesChangeEventMetadata.KubernetesResourceType.StatefulSet.name());
    assertThat(changeEventMetadata.getOldYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNewYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(newStatefulSet.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(newStatefulSet.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnUpdate_noChanges() {
    V1StatefulSet v1StatefulSet = builsStatefulSet();
    handler.onUpdate(v1StatefulSet, v1StatefulSet);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnDelete() {
    V1StatefulSet v1StatefulSet = builsStatefulSet();
    handler.onDelete(v1StatefulSet, false);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(KubernetesChangeEventMetadata.Action.Delete.name());
    assertThat(changeEventMetadata.getResourceType().name())
        .isEqualTo(KubernetesChangeEventMetadata.KubernetesResourceType.StatefulSet.name());
    assertThat(changeEventMetadata.getOldYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(v1StatefulSet.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(v1StatefulSet.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnDelete_finalStateUnknown() {
    V1StatefulSet v1StatefulSet = builsStatefulSet();
    handler.onDelete(v1StatefulSet, true);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  private V1StatefulSet builsStatefulSet() {
    return new V1StatefulSetBuilder()
        .withNewMetadata()
        .withName("test-name")
        .withNamespace("test-namespace")
        .withUid("test-uid")
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
