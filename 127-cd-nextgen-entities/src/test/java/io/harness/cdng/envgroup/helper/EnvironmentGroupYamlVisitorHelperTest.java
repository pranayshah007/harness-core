/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.envgroup.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class EnvironmentGroupYamlVisitorHelperTest extends CategoryTest {
  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddReference() {
    EnvironmentGroupYamlVisitorHelper envGroupVisitor = new EnvironmentGroupYamlVisitorHelper();

    // case1: without expression
    EnvironmentGroupYaml environmentGroupYaml =
        EnvironmentGroupYaml.builder().envGroupRef(ParameterField.createValueField("ref")).build();
    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS =
        envGroupVisitor.addReference(environmentGroupYaml, accountId, orgId, projectId, contextMap);

    assertThat(entityDetailProtoDTOS).hasSize(1);
    EntityDetailProtoDTO entityDetailProto = entityDetailProtoDTOS.iterator().next();
    assertThat(entityDetailProto.getType()).isEqualTo(EntityTypeProtoEnum.ENVIRONMENT_GROUP);
    assertThat(entityDetailProto.getIdentifierRef().getIdentifier().getValue()).isEqualTo("ref");

    // case2: with expression
    environmentGroupYaml = EnvironmentGroupYaml.builder()
                               .envGroupRef(ParameterField.createExpressionField(true, "<+b>", null, false))
                               .build();
    entityDetailProtoDTOS = envGroupVisitor.addReference(environmentGroupYaml, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).hasSize(1);
    entityDetailProto = entityDetailProtoDTOS.iterator().next();
    assertThat(entityDetailProto.getType()).isEqualTo(EntityTypeProtoEnum.ENVIRONMENT_GROUP);
  }
}