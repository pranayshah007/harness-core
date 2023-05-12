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

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentBuilder;
import io.kubernetes.client.openapi.models.V1OwnerReferenceBuilder;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChangeIntelDeploymentHandlerTest extends ChangeIntelHandlerTestBase {
  private ChangeIntelDeploymentHandler handler;

  @Before
  public void setup() throws Exception {
    super.setup();
    handler = ChangeIntelDeploymentHandler.builder()
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
    V1Deployment deployment = buildV1Deployment();
    handler.onAdd(deployment);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(KubernetesChangeEventMetadata.Action.Add.name());
    assertThat(changeEventMetadata.getResourceType().name())
        .isEqualTo(KubernetesChangeEventMetadata.KubernetesResourceType.Deployment.name());
    assertThat(changeEventMetadata.getNewYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(deployment.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(deployment.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnAdd_withOwnerReference() {
    V1Deployment deployment = buildV1Deployment();
    deployment.getMetadata().addOwnerReferencesItem(new V1OwnerReferenceBuilder().withController(true).build());
    handler.onAdd(deployment);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnUpdate() {
    V1Deployment oldDeployment = buildV1Deployment();
    V1Deployment newDeployment = buildV1Deployment();
    newDeployment.getSpec().setProgressDeadlineSeconds(300);
    handler.onUpdate(oldDeployment, newDeployment);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(KubernetesChangeEventMetadata.Action.Update.name());
    assertThat(changeEventMetadata.getResourceType().name())
        .isEqualTo(KubernetesChangeEventMetadata.KubernetesResourceType.Deployment.name());
    assertThat(changeEventMetadata.getOldYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNewYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(newDeployment.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(newDeployment.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnUpdate_noChanges() {
    V1Deployment deployment = buildV1Deployment();
    handler.onUpdate(deployment, deployment);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnDelete() {
    V1Deployment deployment = buildV1Deployment();
    handler.onDelete(deployment, false);
    ChangeEventDTO eventDTO = verifyAndValidate();
    assertThat(eventDTO.getMetadata()).isNotNull();
    KubernetesChangeEventMetadata changeEventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    assertThat(changeEventMetadata.getAction().name()).isEqualTo(KubernetesChangeEventMetadata.Action.Delete.name());
    assertThat(changeEventMetadata.getResourceType().name())
        .isEqualTo(KubernetesChangeEventMetadata.KubernetesResourceType.Deployment.name());
    assertThat(changeEventMetadata.getOldYaml()).isNotEmpty();
    assertThat(changeEventMetadata.getNamespace()).isEqualTo(deployment.getMetadata().getNamespace());
    assertThat(changeEventMetadata.getWorkload()).isEqualTo(deployment.getMetadata().getName());
  }

  @Test
  @Owner(developers = OwnerRule.PRAVEEN)
  @Category({UnitTests.class})
  public void testOnDelete_finalStateUnknown() {
    V1Deployment deployment = buildV1Deployment();
    handler.onDelete(deployment, true);
    verify(cvNextGenServiceClient, times(0)).saveChangeEvent(anyString(), any());
  }

  private V1Deployment buildV1Deployment() {
    return new V1DeploymentBuilder()
        .withNewMetadata()
        .withName("test-name")
        .withNamespace("test-namespace")
        .withUid("test-uid")
        .endMetadata()
        .withNewSpec()
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
        .withNewStatus()
        .addNewCondition()
        .withType("Available")
        .withStatus("True")
        .endCondition()
        .endStatus()
        .build();
  }
}
