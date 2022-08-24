/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.infra;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.cdng.infra.steps.InfraSectionStepParameters;
import io.harness.cdng.infra.steps.InfrastructureSectionStep;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class InfrastructurePmsPlanCreatorTest extends CDNGTestBase {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetInfraSectionStepParamsFromConfig() {
    InfrastructureDefinitionConfig infrastructureDefinitionConfig =
        InfrastructureDefinitionConfig.builder().environmentRef("ref").identifier("infraId").build();
    InfraSectionStepParameters infraSectionStepParamsFromConfig =
        InfrastructurePmsPlanCreator.getInfraSectionStepParamsFromConfig(infrastructureDefinitionConfig, "childNode");

    assertThat(infraSectionStepParamsFromConfig.getChildNodeID()).isEqualTo("childNode");
    assertThat(infraSectionStepParamsFromConfig.getEnvironmentRef().getValue()).isEqualTo("ref");
    assertThat(infraSectionStepParamsFromConfig.getRef().getValue()).isEqualTo("infraId");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testPlanBuilderForInfraSection() {
    String yaml = "---\n"
        + "name: \"Dummy\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n";
    YamlField yamlField;
    try {
      String yamlFieldWithUuid = YamlUtils.injectUuid(yaml);
      yamlField = YamlUtils.readTree(yamlFieldWithUuid);
    } catch (IOException e) {
      throw new InvalidRequestException("Exception while parsing yaml");
    }

    PlanNode planNode =
        InfrastructurePmsPlanCreator.planBuilderForInfraSection("infraSectionUuid").build();
    assertThat(planNode.getUuid()).isEqualTo("infraSectionUuid");
    assertThat(planNode.getName()).isEqualTo(PlanCreatorConstants.INFRA_SECTION_NODE_NAME);
    assertThat(planNode.getIdentifier()).isEqualTo(PlanCreatorConstants.INFRA_SECTION_NODE_IDENTIFIER);
    assertThat(planNode.getGroup()).isEqualTo(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP);
    assertThat(planNode.getStepType()).isEqualTo(InfrastructureSectionStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddRCDependency() {
    String yaml = "---\n"
        + "spec: \n"
        + " environment: \n"
        + "   infraDefinitions: \n"
        + " execution: \n";
    YamlField yamlField;
    try {
      String yamlFieldWithUuid = YamlUtils.injectUuid(yaml);
      yamlField = YamlUtils.readTree(yamlFieldWithUuid);
    } catch (IOException e) {
      throw new InvalidRequestException("Exception while parsing yaml");
    }

    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    YamlField rcField = InfrastructurePmsPlanCreator.addResourceConstraintDependency(
        yamlField.getNode().getField("spec").getNode().getField("environment").getNode(), planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    Map<String, String> dependencyMap =
        planCreationResponseMap.get(rcField.getNode().getUuid()).getDependencies().getDependenciesMap();
    assertThat(dependencyMap.size()).isEqualTo(1);
    assertThat(dependencyMap.containsKey(rcField.getNode().getUuid())).isTrue();
  }
}