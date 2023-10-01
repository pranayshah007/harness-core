/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PARENT_PATH_KEY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class EnvironmentYamlV2VisitorHelperTest extends CategoryTest {
  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";
  EnvironmentYamlV2VisitorHelper envVisitor = new EnvironmentYamlV2VisitorHelper();

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddReference() {
    // case1: without expression
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("ref")).build();
    Map<String, Object> contextMap = new HashMap<>();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS =
        envVisitor.addReference(environmentYamlV2, accountId, orgId, projectId, contextMap);

    assertThat(entityDetailProtoDTOS).hasSize(1);
    EntityDetailProtoDTO entityDetailProto = entityDetailProtoDTOS.iterator().next();
    assertThat(entityDetailProto.getType()).isEqualTo(EntityTypeProtoEnum.ENVIRONMENT);
    assertThat(entityDetailProto.getIdentifierRef().getIdentifier().getValue()).isEqualTo("ref");

    // Case1: without expression with empty environmentRefString
    environmentYamlV2 = EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("")).build();
    entityDetailProtoDTOS = envVisitor.addReference(environmentYamlV2, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).isEmpty();

    // case2: with expression
    environmentYamlV2 = EnvironmentYamlV2.builder()
                            .environmentRef(ParameterField.createExpressionField(true, "<+b>", null, false))
                            .build();
    entityDetailProtoDTOS = envVisitor.addReference(environmentYamlV2, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).hasSize(1);
    entityDetailProto = entityDetailProtoDTOS.iterator().next();
    assertThat(entityDetailProto.getType()).isEqualTo(EntityTypeProtoEnum.ENVIRONMENT);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testaddReferenceMultiEnvCase() throws IOException {
    Map<String, Object> contextMap = new HashMap<>();
    LinkedList<String> linkedList = new LinkedList<>();
    linkedList.add("pipeline");
    linkedList.add("stages");
    linkedList.add("s1");
    linkedList.add("spec");
    linkedList.add("environments");
    linkedList.add("values");
    contextMap.put(PARENT_PATH_KEY, linkedList);
    Map<String, Object> serviceInputs = new LinkedHashMap<>();
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder()
            .environmentRef(ParameterField.createExpressionField(true, "<+input>", null, true))
            .environmentInputs(ParameterField.<Map<String, Object>>builder().value(serviceInputs).build())
            .build();

    EntityDetailProtoDTO entityDetailProtoDTO =
        EntityDetailProtoDTO.newBuilder()
            .setType(EntityTypeProtoEnum.ENVIRONMENT)
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .setScope(ScopeProtoEnum.UNKNOWN)
                                  .setAccountIdentifier(StringValue.of(accountId))
                                  .setOrgIdentifier(StringValue.of(orgId))
                                  .setProjectIdentifier(StringValue.of(projectId))
                                  .setIdentifier(StringValue.of("<+input>"))
                                  .putMetadata("fqn", "pipeline.stages.s1.spec.environments.values")
                                  .putMetadata("yamlTypeRefName", "environmentRef")
                                  .putMetadata("expression", "<+input>")
                                  .build())
            .build();

    Set<EntityDetailProtoDTO> entityDetailProtoDTOS =
        envVisitor.addReference(environmentYamlV2, accountId, orgId, projectId, contextMap);

    assertThat(entityDetailProtoDTOS).contains(entityDetailProtoDTO);
  }
}