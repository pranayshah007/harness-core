/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.aws.beans.AsgCapacityConfig;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Arrays;
import java.util.Collections;
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

    boolean ret = asgStepCommonHelper.isV2Feature(asgStoreManifestsContent, null, null, null, null);
    assertThat(ret).isEqualTo(true);

    ret = asgStepCommonHelper.isV2Feature(null, AsgInstances.builder().build(), null, null, null);
    assertThat(ret).isEqualTo(true);

    ret = asgStepCommonHelper.isV2Feature(
        null, null, List.of(AwsAsgLoadBalancerConfigYaml.builder().build()), null, null);
    assertThat(ret).isEqualTo(true);

    ret = asgStepCommonHelper.isV2Feature(null, null, null, AsgInfraConfig.builder().baseAsgName("test").build(), null);
    assertThat(ret).isEqualTo(true);

    ret = asgStepCommonHelper.isV2Feature(null, null, null, null,
        AsgRollingDeployStepParameters.infoBuilder().asgName(ParameterField.createValueField("test")).build());
    assertThat(ret).isEqualTo(true);
  }

  @Test
  @Owner(developers = OwnerRule.VITALIE)
  @Category(UnitTests.class)
  public void isBaseAsgDeploymentTest() {
    Ambiance ambiance = mock(Ambiance.class);
    InfrastructureOutcome infrastructureOutcome = mock(InfrastructureOutcome.class);

    AsgRollingDeployStepParameters asgRollingDeployStepParameters =
        AsgRollingDeployStepParameters.infoBuilder().asgName(ParameterField.createValueField("test")).build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(asgRollingDeployStepParameters).build();

    doReturn(AsgInfraConfig.builder().baseAsgName("test").build())
        .when(asgEntityHelper)
        .getAsgInfraConfig(any(), any());

    boolean ret = asgStepCommonHelper.isBaseAsgDeployment(ambiance, infrastructureOutcome, stepElementParameters);
    assertThat(ret).isEqualTo(true);

    AsgRollingDeployStepParameters asgRollingDeployStepParameters2 =
        AsgRollingDeployStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters2 =
        StepElementParameters.builder().spec(asgRollingDeployStepParameters2).build();
    assertThatThrownBy(
        () -> asgStepCommonHelper.isBaseAsgDeployment(ambiance, infrastructureOutcome, stepElementParameters2))
        .isInstanceOf(InvalidRequestException.class);

    doReturn(AsgInfraConfig.builder().build()).when(asgEntityHelper).getAsgInfraConfig(any(), any());

    ret = asgStepCommonHelper.isBaseAsgDeployment(ambiance, infrastructureOutcome, stepElementParameters);
    assertThat(ret).isEqualTo(false);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getLoadBalancersTest() {
    String loadBalancer = "loadBalancer";
    String prodListenerArn = "prodListenerArn";
    String prodListenerRuleArn = "prodListenerRuleArn";
    String stageListenerArn = "stageListenerArn";
    String stageListenerRuleArn = "stageListenerRuleArn";
    List<String> prodTargetGroupArnsList = List.of("p_gr1", "p_gr2");
    List<String> stageTargetGroupArnsList = List.of("s_gr1", "s_gr2");

    AwsAsgLoadBalancerConfigYaml awsAsgLoadBalancerConfigYaml =
        AwsAsgLoadBalancerConfigYaml.builder()
            .loadBalancer(ParameterField.createValueField(loadBalancer))
            .prodListener(ParameterField.createValueField(prodListenerArn))
            .prodListenerRuleArn(ParameterField.createValueField(prodListenerRuleArn))
            .stageListener(ParameterField.createValueField(stageListenerArn))
            .stageListenerRuleArn(ParameterField.createValueField(stageListenerRuleArn))
            .build();

    AsgBlueGreenPrepareRollbackDataOutcome asgBlueGreenPrepareRollbackDataOutcome =
        AsgBlueGreenPrepareRollbackDataOutcome.builder()
            .loadBalancerConfigs(List.of(awsAsgLoadBalancerConfigYaml))
            .prodTargetGroupArnListForLoadBalancer(Map.of(loadBalancer, prodTargetGroupArnsList))
            .stageTargetGroupArnListForLoadBalancer(Map.of(loadBalancer, stageTargetGroupArnsList))
            .build();

    List<AsgLoadBalancerConfig> ret =
        AsgStepCommonHelper.getLoadBalancers(asgBlueGreenPrepareRollbackDataOutcome, null);
    assertThat(ret.size()).isEqualTo(1);
    AsgLoadBalancerConfig asgLoadBalancerConfig = ret.get(0);
    assertThat(asgLoadBalancerConfig.getLoadBalancer()).isEqualTo(loadBalancer);
    assertThat(asgLoadBalancerConfig.getProdListenerArn()).isEqualTo(prodListenerArn);
    assertThat(asgLoadBalancerConfig.getProdListenerRuleArn()).isEqualTo(prodListenerRuleArn);
    assertThat(asgLoadBalancerConfig.getProdTargetGroupArnsList()).isEqualTo(prodTargetGroupArnsList);
    assertThat(asgLoadBalancerConfig.getStageListenerArn()).isEqualTo(stageListenerArn);
    assertThat(asgLoadBalancerConfig.getStageListenerRuleArn()).isEqualTo(stageListenerRuleArn);
    assertThat(asgLoadBalancerConfig.getStageTargetGroupArnsList()).isEqualTo(stageTargetGroupArnsList);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getLoadBalancersTestShiftTraffic() {
    String prodListenerArn = "prodListenerArn";
    String prodListenerRuleArn = "prodListenerRuleArn";
    String stageListenerArn = "stageListenerArn";
    String stageListenerRuleArn = "stageListenerRuleArn";
    List<String> prodTargetGroupArnsList = List.of("p_gr1", "p_gr2");
    List<String> stageTargetGroupArnsList = List.of("s_gr1", "s_gr2");

    AwsAsgLoadBalancerConfigYaml awsAsgLoadBalancerConfigYaml1 =
        AwsAsgLoadBalancerConfigYaml.builder()
            .loadBalancer(ParameterField.createValueField("lb1"))
            .prodListener(ParameterField.createValueField(prodListenerArn))
            .prodListenerRuleArn(ParameterField.createValueField(prodListenerRuleArn))
            .stageListener(ParameterField.createValueField(stageListenerArn))
            .stageListenerRuleArn(ParameterField.createValueField(stageListenerRuleArn))
            .build();
    AwsAsgLoadBalancerConfigYaml awsAsgLoadBalancerConfigYaml2 =
        AwsAsgLoadBalancerConfigYaml.builder()
            .loadBalancer(ParameterField.createValueField("lb2"))
            .prodListener(ParameterField.createValueField(prodListenerArn))
            .prodListenerRuleArn(ParameterField.createValueField(prodListenerRuleArn))
            .build();

    AsgBlueGreenPrepareRollbackDataOutcome asgBlueGreenPrepareRollbackDataOutcome =
        AsgBlueGreenPrepareRollbackDataOutcome.builder()
            .loadBalancerConfigs(List.of(awsAsgLoadBalancerConfigYaml1, awsAsgLoadBalancerConfigYaml2))
            .prodTargetGroupArnListForLoadBalancer(
                Map.of("lb1", prodTargetGroupArnsList, "lb2", prodTargetGroupArnsList))
            .stageTargetGroupArnListForLoadBalancer(Map.of("lb1", stageTargetGroupArnsList))
            .build();

    List<AsgLoadBalancerConfig> ret =
        AsgStepCommonHelper.getLoadBalancers(asgBlueGreenPrepareRollbackDataOutcome, null);
    assertThat(ret.size()).isEqualTo(2);

    ret = AsgStepCommonHelper.getLoadBalancers(asgBlueGreenPrepareRollbackDataOutcome, false);
    assertThat(ret.size()).isEqualTo(1);
    AsgLoadBalancerConfig asgLoadBalancerConfig = ret.get(0);
    assertThat(asgLoadBalancerConfig.getLoadBalancer()).isEqualTo("lb1");
    assertThat(asgLoadBalancerConfig.getStageListenerArn()).isEqualTo(stageListenerArn);

    ret = AsgStepCommonHelper.getLoadBalancers(asgBlueGreenPrepareRollbackDataOutcome, true);
    assertThat(ret.size()).isEqualTo(1);
    asgLoadBalancerConfig = ret.get(0);
    assertThat(asgLoadBalancerConfig.getLoadBalancer()).isEqualTo("lb2");
    assertThat(asgLoadBalancerConfig.getStageListenerArn()).isNull();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void isShiftTrafficFeature() {
    boolean ret = asgStepCommonHelper.isShiftTrafficFeature(Collections.EMPTY_LIST);
    assertThat(ret).isEqualTo(false);

    List<AwsAsgLoadBalancerConfigYaml> loadBalancers = Arrays.asList(AwsAsgLoadBalancerConfigYaml.builder().build());
    ret = asgStepCommonHelper.isShiftTrafficFeature(loadBalancers);
    assertThat(ret).isEqualTo(true);

    loadBalancers = Arrays.asList(AwsAsgLoadBalancerConfigYaml.builder()
                                      .stageListenerRuleArn(ParameterField.createValueField("stageListenerRuleArn"))
                                      .stageListener(ParameterField.createValueField("stageListener"))
                                      .build());
    ret = asgStepCommonHelper.isShiftTrafficFeature(loadBalancers);
    assertThat(ret).isEqualTo(false);

    List<AwsAsgLoadBalancerConfigYaml> loadBalancers2 = Arrays.asList(
        AwsAsgLoadBalancerConfigYaml.builder().stageListener(ParameterField.createValueField("stageListener")).build());
    assertThatThrownBy(() -> asgStepCommonHelper.isShiftTrafficFeature(loadBalancers2))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getInfrastructureOutcomeWithUpdatedExpressions() {
    AsgInfrastructureOutcome asgInfrastructureOutcome =
        AsgInfrastructureOutcome.builder().infrastructureKey("infraKey").region("<+expression>").build();

    doReturn(asgInfrastructureOutcome).when(outcomeService).resolve(any(), any());

    Ambiance ambiance = Ambiance.newBuilder().build();

    AsgInfrastructureOutcome result =
        (AsgInfrastructureOutcome) asgStepCommonHelper.getInfrastructureOutcomeWithUpdatedExpressions(ambiance);
    assertThat(result.getRegion()).isNotNull();
  }
}
