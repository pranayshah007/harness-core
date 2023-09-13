/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.aws.beans.AsgCapacityConfig;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgStepCommonHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock CDExpressionResolver cdExpressionResolver;
  @Mock AsgEntityHelper asgEntityHelper;
  @Mock AsgStepHelper asgStepHelper;
  @Mock CDStepHelper cdStepHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  @Mock OutcomeService outcomeService;

  @InjectMocks private AsgStepCommonHelper asgStepCommonHelper;

  @Test
  @Owner(developers = OwnerRule.VITALIE)
  @Category(UnitTests.class)
  public void resolveAsgUserDataOutcomeTest() {
    Ambiance ambiance = mock(Ambiance.class);
    StoreConfig storeConfig = mock(StoreConfig.class);
    UserDataOutcome userDataOutcome = UserDataOutcome.builder().store(storeConfig).build();
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(userDataOutcome).build();

    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    UserDataOutcome ret = asgStepCommonHelper.resolveAsgUserDataOutcome(ambiance);
    assertThat(ret.getStore()).isEqualTo(userDataOutcome.getStore());
  }

  @Test
  @Owner(developers = OwnerRule.VITALIE)
  @Category(UnitTests.class)
  public void convertUserDataOutcomeToManifestOutcomeTest() {
    StoreConfig storeConfig = mock(StoreConfig.class);
    UserDataOutcome userDataOutcome = UserDataOutcome.builder().store(storeConfig).build();
    ManifestOutcome ret = asgStepCommonHelper.convertUserDataOutcomeToManifestOutcome(userDataOutcome);
    assertThat(ret.getStore()).isEqualTo(userDataOutcome.getStore());
  }

  @Test
  @Owner(developers = OwnerRule.VITALIE)
  @Category(UnitTests.class)
  public void getAsgCapacityConfigTest() {
    AsgCapacityConfig ret = asgStepCommonHelper.getAsgCapacityConfig(null);
    assertThat(ret).isNull();

    AsgInstances instances = AsgInstances.builder().type(AsgInstancesType.CURRENT_RUNNING).build();

    ret = asgStepCommonHelper.getAsgCapacityConfig(instances);
    assertThat(ret).isNull();

    AsgFixedInstances asgFixedInstances = AsgFixedInstances.builder()
                                              .min(ParameterField.createValueField(1))
                                              .max(ParameterField.createValueField(3))
                                              .desired(ParameterField.createValueField(2))
                                              .build();
    instances = AsgInstances.builder().type(AsgInstancesType.FIXED).spec(asgFixedInstances).build();

    ret = asgStepCommonHelper.getAsgCapacityConfig(instances);
    assertThat(ret.getMin()).isEqualTo(1);
    assertThat(ret.getMax()).isEqualTo(3);
    assertThat(ret.getDesired()).isEqualTo(2);
  }
  @Test
  @Owner(developers = OwnerRule.VITALIE)
  @Category(UnitTests.class)
  public void isUseAlreadyRunningInstancesTest() {
    boolean ret = asgStepCommonHelper.isUseAlreadyRunningInstances(null, true);
    assertThat(ret).isEqualTo(true);

    ret = asgStepCommonHelper.isUseAlreadyRunningInstances(null, false);
    assertThat(ret).isEqualTo(false);

    AsgInstances instances = AsgInstances.builder().type(AsgInstancesType.CURRENT_RUNNING).build();
    ret = asgStepCommonHelper.isUseAlreadyRunningInstances(instances, false);
    assertThat(ret).isEqualTo(true);

    instances = AsgInstances.builder().type(AsgInstancesType.FIXED).build();
    ret = asgStepCommonHelper.isUseAlreadyRunningInstances(instances, true);
    assertThat(ret).isEqualTo(false);
  }

  @Test
  @Owner(developers = OwnerRule.VITALIE)
  @Category(UnitTests.class)
  public void isV2Feature() {
    Map<String, List<String>> asgStoreManifestsContent =
        Map.of(OutcomeExpressionConstants.USER_DATA, List.of("something"));

    boolean ret = asgStepCommonHelper.isV2Feature(asgStoreManifestsContent, null, null);
    assertThat(ret).isEqualTo(true);

    ret = asgStepCommonHelper.isV2Feature(null, AsgInstances.builder().build(), null);
    assertThat(ret).isEqualTo(true);

    ret = asgStepCommonHelper.isV2Feature(null, null, List.of(AwsAsgLoadBalancerConfigYaml.builder().build()));
    assertThat(ret).isEqualTo(true);
  }
}
