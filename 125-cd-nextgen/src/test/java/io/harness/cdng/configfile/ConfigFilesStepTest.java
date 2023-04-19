/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.FAILED_CHILDREN_OUTPUT;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.configfile.steps.ConfigFilesStep;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.steps.fork.ForkStepParameters;
import io.harness.tasks.ResponseData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class ConfigFilesStepTest extends CDNGTestBase {
  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks private ConfigFilesStep configFilesStep;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse() {
    String identifier = "identifier";
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, ResponseData> responseData = new HashMap<>();
    when(serviceStepsHelper.getChildrenOutcomes(responseData))
        .thenReturn(Collections.singletonList(ConfigFileOutcome.builder().identifier(identifier).build()));
    when(executionSweepingOutputService.listOutputsWithGivenNameAndSetupIds(
             any(), eq(FAILED_CHILDREN_OUTPUT), anyList()))
        .thenReturn(Collections.emptyList());

    StepResponse response =
        configFilesStep.handleChildrenResponse(ambiance, ForkStepParameters.builder().build(), responseData);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);

    StepResponse.StepOutcome[] stepOutcomes = response.getStepOutcomes().toArray(new StepResponse.StepOutcome[1]);
    ConfigFilesOutcome configFilesOutcome = (ConfigFilesOutcome) stepOutcomes[0].getOutcome();
    ConfigFileOutcome configFileOutcome = configFilesOutcome.get(identifier);
    assertThat(configFileOutcome.getIdentifier()).isEqualTo(identifier);
  }
}
