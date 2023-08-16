/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;

import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_APP_PATH;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_COMMAND_OPTIONS;
import static io.harness.cdng.provision.awscdk.AwsCdkHelper.GIT_COMMIT_ID;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class AwsCdkHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock PipelineRbacHelper pipelineRbacHelper;
  @InjectMocks AwsCdkHelper awsCdkHelper = new AwsCdkHelper();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetCommonEnvVariables() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put("key1", "value1");
    ParameterField<Map<String, String>> envVariables =
        ParameterField.<Map<String, String>>builder().value(envVars).build();

    HashMap<String, String> commonEnvVariables =
        awsCdkHelper.getCommonEnvVariables("appPath", Arrays.asList("param1", "param2"), envVariables);

    assertThat(commonEnvVariables.get(PLUGIN_AWS_CDK_APP_PATH)).isEqualTo("appPath");
    assertThat(commonEnvVariables.get(PLUGIN_AWS_CDK_COMMAND_OPTIONS)).isEqualTo("param1 param2");
    assertThat(commonEnvVariables.get("key1")).isEqualTo("value1");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testProcessOutput() {
    StepMapOutput stepMapOutput = StepMapOutput.builder()
                                      .output(GIT_COMMIT_ID, "dGVzdHZhbHVlZQ--")
                                      .output("CDK_OUTPUT", "dGVzdHZhbHVlZQ--")
                                      .output("key", "notEncodedValue")
                                      .build();

    Map<String, String> processOutput = awsCdkHelper.processOutput(stepMapOutput);

    assertThat(processOutput.get(GIT_COMMIT_ID)).isEqualTo("testvaluee");
    assertThat(processOutput.get("CDK_OUTPUT")).isEqualTo("testvaluee");
    assertThat(processOutput.get("key")).isEqualTo("notEncodedValue");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testValidateRuntimePermissions() {
    Ambiance ambiance = getAmbiance();
    ArgumentCaptor<List<EntityDetail>> captor = ArgumentCaptor.forClass(List.class);

    awsCdkHelper.validateRuntimePermissions(ambiance,
        AwsCdkBootstrapStepParameters.infoBuilder()
            .connectorRef(ParameterField.<String>builder().value("connectorRef").build())
            .build());

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(getAmbiance()), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(1);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("connectorRef");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "test-account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }
}
