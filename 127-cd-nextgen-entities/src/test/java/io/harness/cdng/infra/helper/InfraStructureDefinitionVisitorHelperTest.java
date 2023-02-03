/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.helper;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InfraStructureDefinitionVisitorHelperTest extends CategoryTest {
  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";

  InfraStructureDefinitionVisitorHelper visitorHelper = new InfraStructureDefinitionVisitorHelper();
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testAddReference_0() {
    Set<EntityDetailProtoDTO> result = visitorHelper.addReference(
        InfraStructureDefinitionYaml.builder().identifier(ParameterField.createValueField("infra1")).build(), accountId,
        orgId, projectId, Map.of("envRef", "my_env"));

    assertThat(result).hasSize(1);
    assertThat(result.iterator().next().getType()).isEqualTo(EntityTypeProtoEnum.INFRASTRUCTURE);
    InfraDefinitionReferenceProtoDTO infraDefRef = result.iterator().next().getInfraDefRef();
    verifyInfraDefRef(infraDefRef);
    assertThat(infraDefRef.getEnvIdentifier().getValue()).isEqualTo("my_env");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testAddReference_1() {
    Set<EntityDetailProtoDTO> result =
        visitorHelper.addReference(InfraStructureDefinitionYaml.builder()
                                       .identifier(ParameterField.createExpressionField(true, "<+input>", null, true))
                                       .build(),
            accountId, orgId, projectId, Map.of("envRef", "my_env"));
    assertThat(result).hasSize(1);
    assertThat(result.iterator().next().getType()).isEqualTo(EntityTypeProtoEnum.INFRASTRUCTURE);
    InfraDefinitionReferenceProtoDTO infraDefRef = result.iterator().next().getInfraDefRef();
    verifyInfraDefRef(infraDefRef);
    assertThat(infraDefRef.getIdentifier().getValue()).isEqualTo("<+input>");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testAddReference_2() {
    Set<EntityDetailProtoDTO> result =
        visitorHelper.addReference(InfraStructureDefinitionYaml.builder()
                                       .identifier(ParameterField.createExpressionField(true, "<+input>", null, true))
                                       .build(),
            accountId, orgId, projectId, Map.of("envRef", "<+input>"));
    assertThat(result).hasSize(1);
    assertThat(result.iterator().next().getType()).isEqualTo(EntityTypeProtoEnum.INFRASTRUCTURE);
    InfraDefinitionReferenceProtoDTO infraDefRef = result.iterator().next().getInfraDefRef();
    verifyInfraDefRef(infraDefRef);
    assertThat(infraDefRef.getEnvIdentifier().getValue()).isEqualTo("<+input>");
    assertThat(infraDefRef.getIdentifier().getValue()).isEqualTo("<+input>");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testAddReference_3() {
    Set<EntityDetailProtoDTO> result =
        visitorHelper.addReference(InfraStructureDefinitionYaml.builder()
                                       .identifier(ParameterField.createExpressionField(true, "<+input>", null, true))
                                       .build(),
            accountId, orgId, projectId, Map.of("envRef", "<+a.b>"));
    assertThat(result).hasSize(1);
    assertThat(result.iterator().next().getType()).isEqualTo(EntityTypeProtoEnum.INFRASTRUCTURE);
    InfraDefinitionReferenceProtoDTO infraDefRef = result.iterator().next().getInfraDefRef();
    verifyInfraDefRef(infraDefRef);
    assertThat(infraDefRef.getEnvIdentifier().getValue()).isEqualTo("<+a.b>");
    assertThat(infraDefRef.getIdentifier().getValue()).isEqualTo("<+input>");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testAddReference_4() {
    Set<EntityDetailProtoDTO> result =
        visitorHelper.addReference(InfraStructureDefinitionYaml.builder()
                                       .identifier(ParameterField.createExpressionField(true, "<+input>", null, true))
                                       .build(),
            accountId, orgId, projectId, Map.of());
    assertThat(result).hasSize(0);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testAddReference_5() {
    Set<EntityDetailProtoDTO> result = visitorHelper.addReference(
        InfraStructureDefinitionYaml.builder().identifier(ParameterField.createValueField("infra1")).build(), accountId,
        orgId, projectId, Map.of("envRef", "account.my_env"));

    assertThat(result).hasSize(1);
    assertThat(result.iterator().next().getType()).isEqualTo(EntityTypeProtoEnum.INFRASTRUCTURE);
    InfraDefinitionReferenceProtoDTO infraDefRef = result.iterator().next().getInfraDefRef();
    assertThat(infraDefRef.getAccountIdentifier().getValue()).isEqualTo(accountId);
    assertThat(infraDefRef.getOrgIdentifier().getValue()).isEmpty();
    assertThat(infraDefRef.getProjectIdentifier().getValue()).isEmpty();
    assertThat(infraDefRef.getEnvIdentifier().getValue()).isEqualTo("my_env");
  }

  private void verifyInfraDefRef(InfraDefinitionReferenceProtoDTO infraDefRef) {
    assertThat(infraDefRef.getAccountIdentifier().getValue()).isEqualTo(accountId);
    assertThat(infraDefRef.getOrgIdentifier().getValue()).isEqualTo(orgId);
    assertThat(infraDefRef.getProjectIdentifier().getValue()).isEqualTo(projectId);
  }
}
