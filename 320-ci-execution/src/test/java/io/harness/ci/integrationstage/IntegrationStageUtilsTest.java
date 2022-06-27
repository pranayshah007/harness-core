/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.category.element.UnitTests;
import io.harness.ci.pipeline.executions.beans.CIImageDetails;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.ci.pipeline.executions.beans.CIScmDetails;
import io.harness.ci.pipeline.executions.beans.TIBuildDetails;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.executionplan.CIExecutionPlanTestHelper;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.yaml.YamlUtils;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.List;

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
    InitializeStepInfo initializeStepInfo = InitializeStepInfo
            .builder()
            .executionElementConfig(executionElementConfig)
            .build();

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
    InitializeStepInfo initializeStepInfo = InitializeStepInfo
            .builder()
            .executionElementConfig(executionElementConfig)
            .build();

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

    CIScmDetails expectedCiScmDetails = CIScmDetails.builder()
            .scmProvider("Git")
            .scmAuthType("Http")
            .scmHostType("SaaS")
            .build();

    assertThat(ciScmDetails).isEqualTo(expectedCiScmDetails);
  }
}
