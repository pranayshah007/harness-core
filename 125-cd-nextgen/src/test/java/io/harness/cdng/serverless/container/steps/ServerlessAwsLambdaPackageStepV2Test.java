/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.container.steps;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.aws.sam.AwsSamStepHelper;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.ContainerPortHelper;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;

import java.util.HashMap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ServerlessAwsLambdaPackageStepV2Test extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ServerlessStepCommonHelper serverlessStepCommonHelper;

  @Mock private ContainerPortHelper containerPortHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private InstanceInfoService instanceInfoService;

  @Mock AwsSamStepHelper awsSamStepHelper;

  @InjectMocks @Spy private ServerlessAwsLambdaPackageV2Step serverlessAwsLambdaPackageV2Step;

  @Before
  public void setup() {}

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetSerialisedStep() {
    String accountId = "accountId";
    int port = 1;
    String callbackToken = "token";
    String displayName = "name";
    String id = "id";
    String logKey = "logKey";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    doReturn(1).when(serverlessAwsLambdaPackageV2Step).getPort(any(), any());
    doReturn(122L).when(serverlessAwsLambdaPackageV2Step).getTimeout(any(), any());
    UnitStep unitStep = mock(UnitStep.class);
    doReturn(accountId).when(unitStep).getAccountId();
    doReturn(port).when(unitStep).getContainerPort();
    doReturn(callbackToken).when(unitStep).getCallbackToken();
    doReturn(displayName).when(unitStep).getDisplayName();
    doReturn(id).when(unitStep).getId();
    doReturn(logKey).when(unitStep).getLogKey();
    doReturn(unitStep)
        .when(serverlessAwsLambdaPackageV2Step)
        .getUnitStep(any(), any(), any(), any(), any(), any(), any());

    ServerlessAwsLambdaPackageV2StepParameters stepParameters =
        ServerlessAwsLambdaPackageV2StepParameters.infoBuilder()
            .image(ParameterField.<String>builder().value("sdaf").build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    long timeout = 1000;
    String parkedTaskId = "parkedTaskId";
    UnitStep unit = serverlessAwsLambdaPackageV2Step.getSerialisedStep(
        ambiance, stepElementParameters, accountId, logKey, timeout, parkedTaskId);
    assertThat(unit.getContainerPort()).isEqualTo(port);
    assertThat(unit.getAccountId()).isEqualTo(accountId);
    assertThat(unit.getCallbackToken()).isEqualTo(callbackToken);
    assertThat(unit.getDisplayName()).isEqualTo(displayName);
    assertThat(unit.getId()).isEqualTo(id);
    assertThat(unit.getLogKey()).isEqualTo(logKey);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetUnitStep() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    ServerlessAwsLambdaPackageV2StepParameters stepParameters =
        ServerlessAwsLambdaPackageV2StepParameters.infoBuilder()
            .image(ParameterField.<String>builder().value("sdaf").build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .identifier("identifier")
                                                      .name("name")
                                                      .spec(stepParameters)
                                                      .timeout(ParameterField.createValueField("1h"))
                                                      .build();

    UnitStep unitStep = mock(UnitStep.class);
    Mockito.mockStatic(ContainerUnitStepUtils.class);
    when(ContainerUnitStepUtils.serializeStepWithStepParameters(anyInt(), anyString(), anyString(), anyString(),
             anyLong(), anyString(), anyString(), any(), any(), any(), anyString(), any()))
        .thenReturn(unitStep);
    doReturn(1).when(containerPortHelper).getPort(any(), anyString(), anyBoolean());
    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.obtainStepGroupIdentifier(any())).thenReturn("group");
    assertThat(serverlessAwsLambdaPackageV2Step.getUnitStep(
                   ambiance, stepElementParameters, accountId, "logaKey", "100", stepParameters, new HashMap<>()))
        .isEqualTo(unitStep);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetAnyOutComeForStep() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    ServerlessAwsLambdaPackageV2StepParameters stepParameters =
        ServerlessAwsLambdaPackageV2StepParameters.infoBuilder()
            .image(ParameterField.<String>builder().value("sdaf").build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    assertThat(serverlessAwsLambdaPackageV2Step.getAnyOutComeForStep(ambiance, stepElementParameters, new HashMap<>()))
        .isNull();
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(serverlessAwsLambdaPackageV2Step.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetTimeout() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    ServerlessAwsLambdaPackageV2StepParameters stepParameters =
        ServerlessAwsLambdaPackageV2StepParameters.infoBuilder()
            .image(ParameterField.<String>builder().value("sdaf").build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .identifier("identifier")
                                                      .name("name")
                                                      .spec(stepParameters)
                                                      .timeout(ParameterField.createValueField("1s"))
                                                      .build();

    assertThat(serverlessAwsLambdaPackageV2Step.getTimeout(ambiance, stepElementParameters)).isEqualTo(1000);
  }
}