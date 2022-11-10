/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.serviceconfig;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.CustomDeploymentServiceSpec;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class CustomDeploymentServiceSpecVisitorHelperTest {
  private static final String accountId = "ACCOUNT_ID";
  private static final String orgId = "ORG_ID";
  private static final String projectId = "PROJECT_ID";

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddReferenceStableTemplate() {
    CustomDeploymentServiceSpecVisitorHelper customDeploymentServiceSpecVisitorHelper =
        new CustomDeploymentServiceSpecVisitorHelper();
    CustomDeploymentServiceSpec customDeploymentServiceSpec =
        CustomDeploymentServiceSpec.builder()
            .customDeploymentRef(StepTemplateRef.builder().templateRef("account.OpenStack").build())
            .build();

    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS = customDeploymentServiceSpecVisitorHelper.addReference(
        customDeploymentServiceSpec, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).hasSize(1);
    EntityDetailProtoDTO entityDetailProtoDTO = new ArrayList<>(entityDetailProtoDTOS).get(0);
    assertThat(entityDetailProtoDTO.getType()).isEqualTo(TEMPLATE);
    assertThat(entityDetailProtoDTO.getTemplateRef().getIdentifier().getValue()).isEqualTo("OpenStack");
    assertThat(entityDetailProtoDTO.getTemplateRef().getAccountIdentifier().getValue()).isEqualTo(accountId);
    assertThat(entityDetailProtoDTO.getTemplateRef().getOrgIdentifier().getValue()).isEqualTo("");
    assertThat(entityDetailProtoDTO.getTemplateRef().getProjectIdentifier().getValue()).isEqualTo("");
    assertThat(entityDetailProtoDTO.getTemplateRef().getVersionLabel().getValue()).isEqualTo("__STABLE__");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddReferenceAccountTemplate() {
    CustomDeploymentServiceSpecVisitorHelper customDeploymentServiceSpecVisitorHelper =
        new CustomDeploymentServiceSpecVisitorHelper();
    CustomDeploymentServiceSpec customDeploymentServiceSpec =
        CustomDeploymentServiceSpec.builder()
            .customDeploymentRef(StepTemplateRef.builder().templateRef("account.OpenStack").versionLabel("V1").build())
            .build();

    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS = customDeploymentServiceSpecVisitorHelper.addReference(
        customDeploymentServiceSpec, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).hasSize(1);
    EntityDetailProtoDTO entityDetailProtoDTO = new ArrayList<>(entityDetailProtoDTOS).get(0);
    assertThat(entityDetailProtoDTO.getType()).isEqualTo(TEMPLATE);
    assertThat(entityDetailProtoDTO.getTemplateRef().getIdentifier().getValue()).isEqualTo("OpenStack");
    assertThat(entityDetailProtoDTO.getTemplateRef().getAccountIdentifier().getValue()).isEqualTo(accountId);
    assertThat(entityDetailProtoDTO.getTemplateRef().getOrgIdentifier().getValue()).isEqualTo("");
    assertThat(entityDetailProtoDTO.getTemplateRef().getProjectIdentifier().getValue()).isEqualTo("");
    assertThat(entityDetailProtoDTO.getTemplateRef().getVersionLabel().getValue()).isEqualTo("V1");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddReferenceOrgTemplate() {
    CustomDeploymentServiceSpecVisitorHelper customDeploymentServiceSpecVisitorHelper =
        new CustomDeploymentServiceSpecVisitorHelper();
    CustomDeploymentServiceSpec customDeploymentServiceSpec =
        CustomDeploymentServiceSpec.builder()
            .customDeploymentRef(StepTemplateRef.builder().templateRef("org.OpenStack").versionLabel("V1").build())
            .build();

    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS = customDeploymentServiceSpecVisitorHelper.addReference(
        customDeploymentServiceSpec, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).hasSize(1);
    EntityDetailProtoDTO entityDetailProtoDTO = new ArrayList<>(entityDetailProtoDTOS).get(0);
    assertThat(entityDetailProtoDTO.getType()).isEqualTo(TEMPLATE);
    assertThat(entityDetailProtoDTO.getTemplateRef().getIdentifier().getValue()).isEqualTo("OpenStack");
    assertThat(entityDetailProtoDTO.getTemplateRef().getAccountIdentifier().getValue()).isEqualTo(accountId);
    assertThat(entityDetailProtoDTO.getTemplateRef().getOrgIdentifier().getValue()).isEqualTo(orgId);
    assertThat(entityDetailProtoDTO.getTemplateRef().getProjectIdentifier().getValue()).isEqualTo("");
    assertThat(entityDetailProtoDTO.getTemplateRef().getVersionLabel().getValue()).isEqualTo("V1");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddReferenceProjectTemplate() {
    CustomDeploymentServiceSpecVisitorHelper customDeploymentServiceSpecVisitorHelper =
        new CustomDeploymentServiceSpecVisitorHelper();
    CustomDeploymentServiceSpec customDeploymentServiceSpec =
        CustomDeploymentServiceSpec.builder()
            .customDeploymentRef(StepTemplateRef.builder().templateRef("OpenStack").versionLabel("V1").build())
            .build();

    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS = customDeploymentServiceSpecVisitorHelper.addReference(
        customDeploymentServiceSpec, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).hasSize(1);
    EntityDetailProtoDTO entityDetailProtoDTO = new ArrayList<>(entityDetailProtoDTOS).get(0);
    assertThat(entityDetailProtoDTO.getType()).isEqualTo(TEMPLATE);
    assertThat(entityDetailProtoDTO.getTemplateRef().getIdentifier().getValue()).isEqualTo("OpenStack");
    assertThat(entityDetailProtoDTO.getTemplateRef().getAccountIdentifier().getValue()).isEqualTo(accountId);
    assertThat(entityDetailProtoDTO.getTemplateRef().getOrgIdentifier().getValue()).isEqualTo(orgId);
    assertThat(entityDetailProtoDTO.getTemplateRef().getProjectIdentifier().getValue()).isEqualTo(projectId);
    assertThat(entityDetailProtoDTO.getTemplateRef().getVersionLabel().getValue()).isEqualTo("V1");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddReferenceInvalidSpec() {
    CustomDeploymentServiceSpecVisitorHelper customDeploymentServiceSpecVisitorHelper =
        new CustomDeploymentServiceSpecVisitorHelper();
    KubernetesServiceSpec customDeploymentServiceSpec =
        KubernetesServiceSpec.builder().variables(new ArrayList<>()).build();
    Map<String, Object> contextMap = Collections.emptyMap();
    assertThatThrownBy(()
                           -> customDeploymentServiceSpecVisitorHelper.addReference(
                               customDeploymentServiceSpec, accountId, orgId, projectId, contextMap))
        .hasMessage(
            "Object of class class io.harness.cdng.service.beans.KubernetesServiceSpec does not implement CustomDeploymentServiceSpec, and hence can't have CustomDeploymentServiceSpecVisitorHelper as its visitor helper");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCreateDummyElement() {
    CustomDeploymentServiceSpecVisitorHelper customDeploymentServiceSpecVisitorHelper =
        new CustomDeploymentServiceSpecVisitorHelper();
    Object dummyVisitableElement = customDeploymentServiceSpecVisitorHelper.createDummyVisitableElement(new Object());
    customDeploymentServiceSpecVisitorHelper.validate(null, null);
    assertThat(dummyVisitableElement).isInstanceOf(CustomDeploymentServiceSpec.class);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddReferenceWithoutTemplateRef() {
    CustomDeploymentServiceSpecVisitorHelper customDeploymentServiceSpecVisitorHelper =
        new CustomDeploymentServiceSpecVisitorHelper();
    CustomDeploymentServiceSpec customDeploymentServiceSpec = CustomDeploymentServiceSpec.builder().build();

    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS = customDeploymentServiceSpecVisitorHelper.addReference(
        customDeploymentServiceSpec, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).isNotNull();
    assertThat(entityDetailProtoDTOS).hasSize(0);
  }
}
