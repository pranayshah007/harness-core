/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.cdng.visitor.YamlTypes.K8S_ROLLING_DEPLOY;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
@OwnedBy(HarnessTeam.CDC)
public class CDPMSStepPlanCreatorV2Test extends CDNGTestBase {
  @Inject @InjectMocks K8sRollingRollbackStepPlanCreator stepsPlanCreator;

  private YamlField getYamlFieldFromGivenFileName(String file) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(file);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField yamlField = YamlUtils.readTree(yaml);
    return yamlField;
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetExecutionStepFqn() throws IOException {
    YamlField stepsYamlField = getYamlFieldFromGivenFileName("cdng/plan/steps/nestedStepGroups.yml");
    YamlField currentNode = stepsYamlField.getNode().getField("execution").getNode().getField("rollbackSteps");

    PlanCreationContext ctx = PlanCreationContext.builder().currentField(currentNode).build();
    String rollingFqn = stepsPlanCreator.getExecutionStepFqn(ctx.getCurrentField(), K8S_ROLLING_DEPLOY);
    assertThat(rollingFqn).isEqualTo("stage.steps.rolloutDeployment");
  }
}
