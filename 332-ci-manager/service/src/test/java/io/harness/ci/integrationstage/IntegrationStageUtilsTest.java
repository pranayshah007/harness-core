/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.Repository;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sHostedInfraYaml;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.pipeline.executions.beans.CIImageDetails;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.ci.pipeline.executions.beans.CIScmDetails;
import io.harness.ci.pipeline.executions.beans.TIBuildDetails;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.CiIntegrationStageUtils;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class IntegrationStageUtilsTest {
  private CIExecutionPlanTestHelper ciExecutionPlanTestHelper = new CIExecutionPlanTestHelper();

  @Test
  @Category(UnitTests.class)
  public void getGitURLTestWithoutGitSuffix() throws Exception {
    String yamlNode =
        "{\"connectorRef\":\"git_3464\",\"repoName\":\"harness-core\",\"build\":{\"type\":\"branch\",\"spec\":{\"branch\":\"develop\",\"__uuid\":\"YtRST1sGTMyuLgNvJYsInw\"},\"__uuid\":\"Sh-Z7OKrQkeeg35DDI8tHQ\"},\"__uuid\":\"Yl_HajezQ4yOIRqE6xWZYQ\"}";
    CodeBase ciCodebase = YamlUtils.read(yamlNode, CodeBase.class);
    GitConnectionType connectionType = GitConnectionType.ACCOUNT;
    String url = "git@github.com:devkimittal";
    String actual = IntegrationStageUtils.getGitURL(ciCodebase, connectionType, url);
    String expected = "git@github.com:devkimittal/harness-core.git";
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Category(UnitTests.class)
  public void getGitURLTestWithGitSuffix() throws Exception {
    String yamlNode =
        "{\"connectorRef\":\"git_3464\",\"repoName\":\"harness-core.git\",\"build\":{\"type\":\"branch\",\"spec\":{\"branch\":\"develop\",\"__uuid\":\"YtRST1sGTMyuLgNvJYsInw\"},\"__uuid\":\"Sh-Z7OKrQkeeg35DDI8tHQ\"},\"__uuid\":\"Yl_HajezQ4yOIRqE6xWZYQ\"}";
    CodeBase ciCodebase = YamlUtils.read(yamlNode, CodeBase.class);
    GitConnectionType connectionType = GitConnectionType.ACCOUNT;
    String url = "git@github.com:devkimittal";
    String actual = IntegrationStageUtils.getGitURL(ciCodebase, connectionType, url);
    String expected = "git@github.com:devkimittal/harness-core.git";
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Category(UnitTests.class)
  public void getTiBuildDetailsTest() throws Exception {
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder().executionElementConfig(executionElementConfig).build();

    List<TIBuildDetails> tiBuildDetailsList = IntegrationStageUtils.getTiBuildDetails(initializeStepInfo);

    TIBuildDetails tiBuildDetails = TIBuildDetails.builder().buildTool("Maven").language("Java").build();
    List<TIBuildDetails> expectedTiBuildDetails = new ArrayList<>();
    expectedTiBuildDetails.add(tiBuildDetails);

    assertThat(tiBuildDetailsList).isEqualTo(expectedTiBuildDetails);
  }

  @Test
  @Category(UnitTests.class)
  public void getCiImageDetailsTest() throws Exception {
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder().executionElementConfig(executionElementConfig).build();

    List<CIImageDetails> ciImageDetailsList = IntegrationStageUtils.getCiImageDetails(initializeStepInfo);

    CIImageDetails image1 = CIImageDetails.builder().imageName("drone/git").imageTag("").build();
    CIImageDetails image2 = CIImageDetails.builder().imageName("maven").imageTag("3.6.3-jdk-8").build();
    CIImageDetails image3 = CIImageDetails.builder().imageName("plugins/git").imageTag("").build();
    CIImageDetails image4 = CIImageDetails.builder().imageName("maven").imageTag("3.6.3-jdk-8").build();

    List<CIImageDetails> expectedCiBuildDetails = new ArrayList<>();
    expectedCiBuildDetails.add(image1);
    expectedCiBuildDetails.add(image2);
    expectedCiBuildDetails.add(image3);
    expectedCiBuildDetails.add(image4);

    assertThat(ciImageDetailsList).isEqualTo(expectedCiBuildDetails);
  }

  @Test
  @Category(UnitTests.class)
  public void getCiInfraDetailsTest() throws Exception {
    Infrastructure infrastructure = ciExecutionPlanTestHelper.getInfrastructureWithVolume();

    CIInfraDetails ciInfraDetails = IntegrationStageUtils.getCiInfraDetails(infrastructure);

    CIInfraDetails expectedCiInfraDetails = CIInfraDetails.builder()
                                                .infraType("KubernetesDirect")
                                                .infraOSType("Linux")
                                                .infraHostType("Self Hosted")
                                                .infraArchType("Amd64")
                                                .build();

    assertThat(ciInfraDetails).isEqualTo(expectedCiInfraDetails);
  }

  @Test
  @Category(UnitTests.class)
  public void getCiScmDetailsTest() throws Exception {
    Infrastructure infrastructure = ciExecutionPlanTestHelper.getInfrastructureWithVolume();

    ConnectorUtils connectorUtils = new ConnectorUtils(null, null, null, null);
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getGitConnector();

    CIScmDetails ciScmDetails = IntegrationStageUtils.getCiScmDetails(connectorUtils, connectorDetails);

    CIScmDetails expectedCiScmDetails =
        CIScmDetails.builder().scmProvider("Git").scmAuthType("Http").scmHostType("SaaS").build();

    assertThat(ciScmDetails).isEqualTo(expectedCiScmDetails);
  }

  @Test
  @Category(UnitTests.class)
  public void testGetAllSteps() throws Exception {
    List<ExecutionWrapperConfig> wrapperConfigs =
        K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup1();
    List<CIAbstractStepNode> steps = IntegrationStageUtils.getAllSteps(wrapperConfigs);
    assertThat(steps.size()).isEqualTo(9);
    List<String> ids = new ArrayList<>();
    for (CIAbstractStepNode step : steps) {
      ids.add(step.getIdentifier());
    }
    assertThat(ids.contains("run2")).isTrue();
    assertThat(ids.contains("run1")).isTrue();
    assertThat(ids.contains("run31")).isTrue();
    assertThat(ids.contains("run32")).isTrue();
    assertThat(ids.contains("step-2")).isTrue();
    assertThat(ids.contains("step-3")).isTrue();
    assertThat(ids.contains("step-4")).isTrue();
    assertThat(ids.contains("run21")).isTrue();
    assertThat(ids.contains("run22")).isTrue();
    assertThat(ids.contains("run3")).isFalse();
  }

  @Test
  @Category(UnitTests.class)
  public void testGetStageConnectorRefs() throws Exception {
    List<ExecutionWrapperConfig> wrapperConfigs =
        K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup1();
    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(wrapperConfigs).build();
    IntegrationStageConfig integrationStageConfig =
        IntegrationStageConfigImpl.builder().execution(executionElementConfig).build();
    List<String> refs = IntegrationStageUtils.getStageConnectorRefs(integrationStageConfig);
    assertThat(refs.size()).isEqualTo(8);
    assertThat(refs.contains("account.harnessImage")).isTrue();
    assertThat(refs.contains("run")).isTrue();
  }

  @Test
  @Category(UnitTests.class)
  public void testInjectLoopEnvVariables() throws Exception {
    List<ExecutionWrapperConfig> wrapperConfigs =
        K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup1();
    for (ExecutionWrapperConfig config : wrapperConfigs) {
      IntegrationStageUtils.injectLoopEnvVariables(config);
    }
    List<CIAbstractStepNode> steps = IntegrationStageUtils.getAllSteps(wrapperConfigs);
    for (CIAbstractStepNode step : steps) {
      StepSpecType spec = step.getStepSpecType();
      StepParameters params = spec.getStepParameters();
      String stepJson = params.toString();
      assertThat(stepJson.contains("\"HARNESS_STAGE_INDEX\": \"<+stage.iteration>\""));
      assertThat(stepJson.contains("\"HARNESS_STAGE_TOTAL\": \"<+stage.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_STEP_INDEX\": \"<+step.iteration>\""));
      assertThat(stepJson.contains("\"HARNESS_STEP_TOTAL\": \"<+step.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_NODE_INDEX\": \"<+strategy.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_NODE_TOTAL\": \"<+strategy.iterations>\""));
    }

    wrapperConfigs = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup2();
    for (ExecutionWrapperConfig config : wrapperConfigs) {
      IntegrationStageUtils.injectLoopEnvVariables(config);
    }
    steps = IntegrationStageUtils.getAllSteps(wrapperConfigs);
    for (CIAbstractStepNode step : steps) {
      StepSpecType spec = step.getStepSpecType();
      StepParameters params = spec.getStepParameters();
      String stepJson = params.toString();
      assertThat(stepJson.contains("\"HARNESS_STAGE_INDEX\": \"<+stage.iteration>\""));
      assertThat(stepJson.contains("\"HARNESS_STAGE_TOTAL\": \"<+stage.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_STEP_INDEX\": \"<+step.iteration>\""));
      assertThat(stepJson.contains("\"HARNESS_STEP_TOTAL\": \"<+step.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_NODE_INDEX\": \"<+strategy.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_NODE_TOTAL\": \"<+strategy.iterations>\""));
    }
  }

  @Test
  @Category(UnitTests.class)
  public void getBuildTimeMultiplier() {
    K8sHostedInfraYaml k8sHostedInfraYaml = K8sHostedInfraYaml.builder().build();
    Double buildTimeMultiplier = IntegrationStageUtils.getBuildTimeMultiplierForHostedInfra(k8sHostedInfraYaml);
    assertThat(buildTimeMultiplier).isEqualTo(1.0);
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().arch(ParameterField.createValueField(ArchType.Amd64)).build()))
                      .build())
            .build();
    buildTimeMultiplier = IntegrationStageUtils.getBuildTimeMultiplierForHostedInfra(hostedVmInfraYaml);
    assertThat(buildTimeMultiplier).isEqualTo(1.0);
  }

  @Test
  @Category(UnitTests.class)
  public void shouldNotFailForAzureOnPremUrl() {
    String accountUrl = "https://tfs.azureonprem.com/Org/Project/";
    String actualUrl =
        CiIntegrationStageUtils.retrieveGenericGitConnectorURL("repo", GitConnectionType.PROJECT, accountUrl);
    assertThat(actualUrl).isEqualTo(accountUrl + "_git/repo");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testIsURLSame() {
    assertThat(
        IntegrationStageUtils.isURLSame(
            WebhookExecutionSource.builder()
                .webhookEvent(
                    BranchWebhookEvent.builder()
                        .repository(Repository.builder().httpURL("https://github.com/devkimittal/harness-core").build())
                        .build())
                .build(),
            "https://github.com/devkimittal/harness-core"))
        .isTrue();
    assertThat(
        IntegrationStageUtils.isURLSame(
            WebhookExecutionSource.builder()
                .webhookEvent(
                    BranchWebhookEvent.builder()
                        .repository(Repository.builder().httpURL("https://github.com/devkimittal/harness-core").build())
                        .build())
                .build(),
            "https://github.com/Devkimittal/Harness-core"))
        .isTrue();
    assertThat(
        IntegrationStageUtils.isURLSame(
            WebhookExecutionSource.builder()
                .webhookEvent(
                    PRWebhookEvent.builder()
                        .repository(Repository.builder().httpURL("https://github.com/devkimittal/harness-core").build())
                        .build())
                .build(),
            "https://github.com/Devkimittal/Harness-core"))
        .isTrue();
  }
}
