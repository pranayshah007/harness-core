/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1OwnerReferenceBuilder;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChangeIntelConfigMapHandlerTest extends ChangeIntelHandlerTestBase {
  private ChangeIntelConfigMapHandler handler;
  @Before
  public void setup() throws Exception {
    super.setup();
    handler = ChangeIntelConfigMapHandler.builder()
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
    V1ConfigMap configMap = buildConfigMap();
    handler.onAdd(configMap);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(KubernetesChangeEventMetadata.Action.Add.name());
    assertThat(changeEventMetadata.getResourceType().name())
        .isEqualTo(KubernetesChangeEventMetadata.KubernetesResourceType.ConfigMap.name());
    assertThat(changeEventMetadata.getNewYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(configMap.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(configMap.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnAdd_withOwnerReference() {
    V1ConfigMap configMap = buildConfigMap();
    configMap.getMetadata().addOwnerReferencesItem(new V1OwnerReferenceBuilder().withController(true).build());
    handler.onAdd(configMap);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnUpdate() {
    V1ConfigMap oldConfigMap = buildConfigMap();
    V1ConfigMap newConfigMap = buildConfigMap();
    newConfigMap.getData().put("key2", "value2");
    handler.onUpdate(oldConfigMap, newConfigMap);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(KubernetesChangeEventMetadata.Action.Update.name());
    assertThat(changeEventMetadata.getResourceType().name())
        .isEqualTo(KubernetesChangeEventMetadata.KubernetesResourceType.ConfigMap.name());
    assertThat(changeEventMetadata.getOldYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNewYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(newConfigMap.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(newConfigMap.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnUpdate_noChanges() {
    V1ConfigMap configMap = buildConfigMap();
    handler.onUpdate(configMap, configMap);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnUpdate_updateRenewTime() {
    V1ConfigMap configMap = buildConfigMap();
    V1ConfigMap configMapNew = buildConfigMap();
    configMapNew.getMetadata().getAnnotations().put("control-plane.alpha.kubernetes.io/leader",
        "{\"holderIdentity\":\"nginx-ingress-controller-fd6c8f756-6p46w\",\"leaseDurationSeconds\":30,\"acquireTime\":\"2021-10-09T16:38:45Z\",\"renewTime\":\"2021-10-19T06:04:36Z\",\"leaderTransitions\":272}");
    handler.onUpdate(configMap, configMapNew);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnDelete() {
    V1ConfigMap configMap = buildConfigMap();
    handler.onDelete(configMap, false);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(KubernetesChangeEventMetadata.Action.Delete.name());
    assertThat(changeEventMetadata.getResourceType().name())
        .isEqualTo(KubernetesChangeEventMetadata.KubernetesResourceType.ConfigMap.name());
    assertThat(changeEventMetadata.getOldYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(configMap.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(configMap.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnDelete_finalStateUnknown() {
    V1ConfigMap configMap = buildConfigMap();
    handler.onDelete(configMap, true);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  private V1ConfigMap buildConfigMap() {
    Map<String, String> annotationMap = new HashMap<>();
    annotationMap.put("control-plane.alpha.kubernetes.io/leader",
        "{\"holderIdentity\":\"nginx-ingress-controller-fd6c8f756-6p46w\",\"leaseDurationSeconds\":30,\"acquireTime\":\"2021-10-09T16:38:45Z\",\"renewTime\":\"2021-10-19T06:04:26Z\",\"leaderTransitions\":272}");
    return new V1ConfigMapBuilder()
        .withNewMetadata()
        .withAnnotations(annotationMap)
        .withName("test-name")
        .withNamespace("test-namespace")
        .withCreationTimestamp(OffsetDateTime.now())
        .withUid("test-uid")
        .endMetadata()
        .withData(Maps.newHashMap(ImmutableMap.of("key1", "value1")))
        .build();
  }
}
