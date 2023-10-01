/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class CIStepGroupUtilsTest extends CIExecutionTestBase {
  @Inject CIStepGroupUtils ciStepGroupUtils;
  @Inject CIExecutionPlanTestHelper ciExecutionPlanTestHelper;
  @Mock CIFeatureFlagService featureFlagService;

  private final String accountID = "accountID";

  @Before
  public void setup() {
    on(ciStepGroupUtils).set("featureFlagService", featureFlagService);
    when(featureFlagService.isEnabled(FeatureName.CI_DLITE_DISTRIBUTED, accountID)).thenReturn(true);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void createExecutionWrapperWithLiteEngineSteps() {
    IntegrationStageNode integrationStageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    integrationStageNode.getIntegrationStageConfig().setInfrastructure(
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .build()))
                    .build())
            .build());
    List<ExecutionWrapperConfig> executionWrapperConfigs = ciStepGroupUtils.createExecutionWrapperWithInitializeStep(
        integrationStageNode, ciExecutionPlanTestHelper.getCIExecutionArgs(), ciExecutionPlanTestHelper.getCICodebase(),
        integrationStageNode.getIntegrationStageConfig().getInfrastructure(), accountID);
    assertThat(executionWrapperConfigs).isNotEmpty();
    ExecutionWrapperConfig leWrapperConfig = executionWrapperConfigs.get(0);
    leWrapperConfig.getStep().has("failureStrategies");
  }
}
