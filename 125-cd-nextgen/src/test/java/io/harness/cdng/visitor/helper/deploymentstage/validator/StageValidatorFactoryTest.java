/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helper.deploymentstage.validator;

import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.CustomDeploymentStageValidatorHelper;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.NoOpStageValidatorHelper;
import io.harness.cdng.creator.plan.stage.StageValidatorFactory;
import io.harness.cdng.creator.plan.stage.TasStageValidatorHelper;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.rule.Owner;

import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class StageValidatorFactoryTest extends CategoryTest {
  @Mock TasStageValidatorHelper tasStageValidatorHelper;
  @Mock CustomDeploymentStageValidatorHelper customDeploymentStageValidatorHelper;
  @Mock NoOpStageValidatorHelper noOpStageValidatorHelper;
  @InjectMocks StageValidatorFactory stageValidatorFactory = new StageValidatorFactory();

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateMultipleBasicAppSetup() {
    DeploymentStageConfig tasStageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.TAS)
            .execution(ExecutionElementConfig.builder().steps(new ArrayList<>()).build())
            .build();
    assertThat(stageValidatorFactory.getStageValidationHelper(tasStageConfig)).isEqualTo(tasStageValidatorHelper);
    DeploymentStageConfig customDeploymentStageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.CUSTOM_DEPLOYMENT)
            .execution(ExecutionElementConfig.builder().steps(new ArrayList<>()).build())
            .build();
    assertThat(stageValidatorFactory.getStageValidationHelper(customDeploymentStageConfig))
        .isEqualTo(customDeploymentStageValidatorHelper);
    DeploymentStageConfig k8sStageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(ServiceDefinitionType.KUBERNETES)
            .execution(ExecutionElementConfig.builder().steps(new ArrayList<>()).build())
            .build();
    assertThat(stageValidatorFactory.getStageValidationHelper(k8sStageConfig)).isEqualTo(noOpStageValidatorHelper);
    assertThat(stageValidatorFactory.getStageValidationHelper(ExecutionElementConfig.builder().build()))
        .isEqualTo(noOpStageValidatorHelper);
  }
}
