/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.integrationstage;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.environment.BuildJobEnvInfo;
import io.harness.beans.environment.K8BuildJobEnvInfo;
import io.harness.beans.stages.IntegrationStageConfigImpl;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.category.element.UnitTests;
import io.harness.ci.integrationstage.K8InitializeStepInfoBuilder;
import io.harness.common.CIExecutionConstants;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.execution.CIExecutionConfigService;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.ff.CIFeatureFlagService;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.stateutils.buildstate.ConnectorUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class K8InitializeStepInfoBuilderTest extends CIExecutionTestBase {
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                            "projectIdentifier", "orgIdentifier", "orgIdentifier"))
                                        .build();

  @Mock private ConnectorUtils connectorUtils;
  @Mock private ConnectorDetails connectorDetails;
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private CIFeatureFlagService featureFlagService;
  @InjectMocks private K8InitializeStepInfoBuilder k8InitializeStepInfoBuilder;

  @Before
  public void setUp() {
    on(k8InitializeStepInfoBuilder).set("ciExecutionConfigService", ciExecutionConfigService);
    on(k8InitializeStepInfoBuilder).set("featureFlagService", featureFlagService);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testECRBuildPushWithBaseImageConnectorSecretEnv() throws IOException {
    prepareConnector(ConnectorType.DOCKER);
    StageElementConfig stageElementConfig = prepareStageConfig("ecrBuildAndPushWithBaseImageConnector.json");

    Map<EnvVariableEnum, String> awsConnectorMap = new HashMap<>();
    awsConnectorMap.put(EnvVariableEnum.AWS_ACCESS_KEY, CIExecutionConstants.PLUGIN_ACCESS_KEY);
    awsConnectorMap.put(EnvVariableEnum.AWS_SECRET_KEY, CIExecutionConstants.PLUGIN_SECRET_KEY);
    awsConnectorMap.put(EnvVariableEnum.AWS_CROSS_ACCOUNT_ROLE_ARN, CIExecutionConstants.AWS_ROLE_ARN);

    Map<EnvVariableEnum, String> dockerConnectorMap = new HashMap<>();
    dockerConnectorMap.put(EnvVariableEnum.DOCKER_PASSWORD, CIExecutionConstants.PLUGIN_PASSW);
    dockerConnectorMap.put(EnvVariableEnum.DOCKER_USERNAME, CIExecutionConstants.PLUGIN_USERNAME);
    dockerConnectorMap.put(EnvVariableEnum.DOCKER_REGISTRY, CIExecutionConstants.PLUGIN_REGISTRY);

    List<K8BuildJobEnvInfo.ConnectorConversionInfo> expectedConversionInfo =
        Arrays.asList(K8BuildJobEnvInfo.ConnectorConversionInfo.builder()
                          .connectorRef("AWSConnector")
                          .envToSecretsMap(awsConnectorMap)
                          .build(),
            K8BuildJobEnvInfo.ConnectorConversionInfo.builder()
                .connectorRef("docker")
                .envToSecretsMap(dockerConnectorMap)
                .build());
    Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> expected = new HashMap<>();
    expected.put("Build_and_Push_to_ECR", expectedConversionInfo);

    BuildJobEnvInfo buildJobEnvInfo = k8InitializeStepInfoBuilder.getInitializeStepInfoBuilder(stageElementConfig,
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder().volumes(new ParameterField<>()).build())
            .build(),
        null, stageElementConfig.getStageType().getExecution().getSteps(), ambiance);
    K8BuildJobEnvInfo k8BuildJobEnvInfo = (K8BuildJobEnvInfo) buildJobEnvInfo;
    Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> actual = k8BuildJobEnvInfo.getStepConnectorRefs();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testECRBuildPushWithoutBaseImageConnectorSecretEnv() throws IOException {
    prepareConnector(ConnectorType.DOCKER);
    StageElementConfig stageElementConfig = prepareStageConfig("ecrBuildAndPushWithoutBaseImageConnector.json");

    Map<EnvVariableEnum, String> awsConnectorMap = new HashMap<>();
    awsConnectorMap.put(EnvVariableEnum.AWS_ACCESS_KEY, CIExecutionConstants.PLUGIN_ACCESS_KEY);
    awsConnectorMap.put(EnvVariableEnum.AWS_SECRET_KEY, CIExecutionConstants.PLUGIN_SECRET_KEY);
    awsConnectorMap.put(EnvVariableEnum.AWS_CROSS_ACCOUNT_ROLE_ARN, CIExecutionConstants.AWS_ROLE_ARN);

    List<K8BuildJobEnvInfo.ConnectorConversionInfo> expectedConversionInfo =
        Arrays.asList(K8BuildJobEnvInfo.ConnectorConversionInfo.builder()
                          .connectorRef("AWSConnector")
                          .envToSecretsMap(awsConnectorMap)
                          .build());
    Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> expected = new HashMap<>();
    expected.put("Build_and_Push_to_ECR", expectedConversionInfo);

    BuildJobEnvInfo buildJobEnvInfo = k8InitializeStepInfoBuilder.getInitializeStepInfoBuilder(stageElementConfig,
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder().volumes(new ParameterField<>()).build())
            .build(),
        null, stageElementConfig.getStageType().getExecution().getSteps(), ambiance);
    K8BuildJobEnvInfo k8BuildJobEnvInfo = (K8BuildJobEnvInfo) buildJobEnvInfo;
    Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> actual = k8BuildJobEnvInfo.getStepConnectorRefs();
    assertThat(actual).isEqualTo(expected);
  }

  private void prepareConnector(ConnectorType connectorType) {
    when(connectorDetails.getConnectorType()).thenReturn(connectorType);
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
  }

  private StageElementConfig prepareStageConfig(String filename) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    JsonNode stepDetails = objectMapper.readTree(
        IOUtils.resourceToString(filename, StandardCharsets.UTF_8, this.getClass().getClassLoader()));
    steps.add(ExecutionWrapperConfig.builder().step(stepDetails).build());
    return StageElementConfig.builder()
        .identifier("test")
        .type("CI")
        .stageType(IntegrationStageConfigImpl.builder()
                       .execution(ExecutionElementConfig.builder().steps(steps).build())
                       .serviceDependencies(new ParameterField<>())
                       .sharedPaths(new ParameterField<>())
                       .build())
        .build();
  }
}
