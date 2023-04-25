/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.setupusage.InfrastructureEntitySetupUsageHelper;

import com.google.protobuf.StringValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class InfraStructureDefinitionVisitorHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";

  @Mock InfrastructureEntityService infrastructureEntityService;

  @Mock InfrastructureEntitySetupUsageHelper infrastructureEntitySetupUsageHelper;

  @InjectMocks InfraStructureDefinitionVisitorHelper visitorHelper;

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

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testAddReference_WithInfraInputs0() {
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("identifier", "UAT_INFRA");
    inputs.put("type", "KubernetesDirect");
    inputs.put("__uuid", "UUID");

    Map<String, String> kubernetesSpec = new LinkedHashMap<>();
    kubernetesSpec.put("connectorRef", "testConn");
    kubernetesSpec.put("namespace", "<+input>");

    inputs.put("spec", kubernetesSpec);

    ParameterField<Map<String, Object>> inputsParam = ParameterField.createValueField(inputs);

    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(accountId)
                                                    .orgIdentifier(orgId)
                                                    .projectIdentifier(projectId)
                                                    .envIdentifier("envRef")
                                                    .identifier("infra1")
                                                    .yaml("infrastructureDefinition:\n"
                                                        + "  name: infra1\n"
                                                        + "  identifier: infra1\n"
                                                        + "  description: \"\"\n"
                                                        + "  tags: {}\n"
                                                        + "  orgIdentifier: org\n"
                                                        + "  projectIdentifier: proj\n"
                                                        + "  environmentRef: my_env\n"
                                                        + "  deploymentType: Kubernetes\n"
                                                        + "  type: KubernetesDirect\n"
                                                        + "  spec:\n"
                                                        + "    connectorRef: <+input>\n"
                                                        + "    namespace: <+input>\n"
                                                        + "    releaseName: release-<+INFRA_KEY>\n"
                                                        + "  allowSimultaneousDeployments: false\n")
                                                    .build();

    doReturn(Optional.of(infrastructureEntity))
        .when(infrastructureEntityService)
        .get(eq(accountId), eq(orgId), eq(projectId), eq("my_env"), eq("infra1"));

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();
    Map<String, String> metadataMap = new HashMap<>();
    metadataMap.put("fqn", "infrastructureDefinition.spec.connectorRef");
    metadataMap.put("expression", "<+input>");

    EntityDetailProtoDTO entityDetailProtoDTO = EntityDetailProtoDTO.newBuilder()
                                                    .setType(EntityTypeProtoEnum.CONNECTORS)
                                                    .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                                                          .setIdentifier(StringValue.of("<+input>"))
                                                                          .putAllMetadata(metadataMap))
                                                    .build();
    setEntityDetail.add(entityDetailProtoDTO);

    doReturn(setEntityDetail)
        .when(infrastructureEntitySetupUsageHelper)
        .getAllReferredEntities(eq(infrastructureEntity));

    Set<EntityDetailProtoDTO> result =
        visitorHelper.addReference(InfraStructureDefinitionYaml.builder()
                                       .identifier(ParameterField.createValueField("infra1"))
                                       .inputs(inputsParam)
                                       .build(),
            accountId, orgId, projectId, Map.of("envRef", "my_env"));

    // Infra and Connector
    assertThat(result).hasSize(2);

    List<EntityTypeProtoEnum> resultTypes =
        result.stream().map(EntityDetailProtoDTO::getType).collect(Collectors.toList());

    assertThat(resultTypes).contains(EntityTypeProtoEnum.CONNECTORS, EntityTypeProtoEnum.INFRASTRUCTURE);
  }

  private void verifyInfraDefRef(InfraDefinitionReferenceProtoDTO infraDefRef) {
    assertThat(infraDefRef.getAccountIdentifier().getValue()).isEqualTo(accountId);
    assertThat(infraDefRef.getOrgIdentifier().getValue()).isEqualTo(orgId);
    assertThat(infraDefRef.getProjectIdentifier().getValue()).isEqualTo(projectId);
  }
}
