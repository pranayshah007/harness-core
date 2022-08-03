/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stages;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class StageExecutionSelectorHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageExecutionResponse() {
    String pipelineYaml = getPipelineYamlForStagesRequired();
    List<StageExecutionResponse> stageExecutionResponse =
        StageExecutionSelectorHelper.getStageExecutionResponse(pipelineYaml);
    assertThat(stageExecutionResponse).hasSize(4);

    StageExecutionResponse s1 = stageExecutionResponse.get(0);
    assertThat(s1.getStageIdentifier()).isEqualTo("s1");
    assertThat(s1.getStageName()).isEqualTo("s1");
    assertThat(s1.getStagesRequired()).containsExactlyInAnyOrder("s3", "s2");
    assertThat(s1.getMessage()).isNotEmpty();
    assertThat(s1.isToBeBlocked()).isTrue();

    StageExecutionResponse s2 = stageExecutionResponse.get(1);
    assertThat(s2.getStageIdentifier()).isEqualTo("s2");
    assertThat(s2.getStageName()).isEqualTo("s2");
    assertThat(s2.getStagesRequired()).containsExactlyInAnyOrder("s3", "s1");
    assertThat(s2.getMessage()).isNotEmpty();
    assertThat(s2.isToBeBlocked()).isTrue();

    StageExecutionResponse s3 = stageExecutionResponse.get(2);
    assertThat(s3.getStageIdentifier()).isEqualTo("s3");
    assertThat(s3.getStageName()).isEqualTo("s3");
    assertThat(s3.getStagesRequired()).containsExactlyInAnyOrder("s2", "s1");
    assertThat(s3.getMessage()).isNotEmpty();
    assertThat(s3.isToBeBlocked()).isTrue();

    StageExecutionResponse s4 = stageExecutionResponse.get(3);
    assertThat(s4.getStageIdentifier()).isEqualTo("s4");
    assertThat(s4.getStageName()).isEqualTo("s4");
    assertThat(s4.getStagesRequired()).isEmpty();
    assertThat(s4.getMessage()).isNull();
    assertThat(s4.isToBeBlocked()).isFalse();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetStageExecutionResponseWithCIUseFromStage() {
    String pipelineYaml = getPipelineYamlForStagesRequiredWithCIUseFromStage();
    List<StageExecutionResponse> stageExecutionResponse =
        StageExecutionSelectorHelper.getStageExecutionResponse(pipelineYaml);
    assertThat(stageExecutionResponse).hasSize(4);

    StageExecutionResponse s1 = stageExecutionResponse.get(0);
    assertThat(s1.getStageIdentifier()).isEqualTo("s1");
    assertThat(s1.getStageName()).isEqualTo("s1");
    assertThat(s1.getStagesRequired()).containsExactlyInAnyOrder("s3", "s2");
    assertThat(s1.getMessage()).isNotEmpty();
    assertThat(s1.isToBeBlocked()).isTrue();

    StageExecutionResponse s2 = stageExecutionResponse.get(1);
    assertThat(s2.getStageIdentifier()).isEqualTo("s2");
    assertThat(s2.getStageName()).isEqualTo("s2");
    assertThat(s2.getStagesRequired()).containsExactlyInAnyOrder("s3", "s1");
    assertThat(s2.getMessage()).isNotEmpty();
    assertThat(s2.isToBeBlocked()).isTrue();

    StageExecutionResponse s3 = stageExecutionResponse.get(2);
    assertThat(s3.getStageIdentifier()).isEqualTo("s3");
    assertThat(s3.getStageName()).isEqualTo("s3");
    assertThat(s3.getStagesRequired()).containsExactlyInAnyOrder("s2", "s1");
    assertThat(s3.getMessage()).isNotEmpty();
    assertThat(s3.isToBeBlocked()).isTrue();

    StageExecutionResponse s4 = stageExecutionResponse.get(3);
    assertThat(s4.getStageIdentifier()).isEqualTo("s4");
    assertThat(s4.getStageName()).isEqualTo("s4");
    assertThat(s4.getStagesRequired()).isEmpty();
    assertThat(s4.getMessage()).isNull();
    assertThat(s4.isToBeBlocked()).isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageExecutionResponseForStageTemplates() {
    String pipelineYaml = getPipelineWithStageTemplates();
    List<StageExecutionResponse> stageExecutionResponse =
        StageExecutionSelectorHelper.getStageExecutionResponse(pipelineYaml);
    assertThat(stageExecutionResponse).hasSize(2);
    StageExecutionResponse s0 = stageExecutionResponse.get(0);
    assertThat(s0.getStageIdentifier()).isEqualTo("d1");
    assertThat(s0.getStageName()).isEqualTo("d1");
    StageExecutionResponse s1 = stageExecutionResponse.get(1);
    assertThat(s1.getStageIdentifier()).isEqualTo("d2");
    assertThat(s1.getStageName()).isEqualTo("d2");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageInfoListWithStagesRequired() {
    String pipelineYaml = getPipelineYamlForStagesRequired();
    List<BasicStageInfo> stageInfoList = StageExecutionSelectorHelper.getStageInfoListWithStagesRequired(pipelineYaml);

    assertThat(stageInfoList.get(0).getStagesRequired()).containsExactlyInAnyOrder("s3", "s2");
    assertThat(stageInfoList.get(1).getStagesRequired()).containsExactlyInAnyOrder("s3", "s1");
    assertThat(stageInfoList.get(2).getStagesRequired()).containsExactlyInAnyOrder("s1", "s2");
    assertThat(stageInfoList.get(3).getStagesRequired()).hasSize(0);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageInfoList() {
    String pipelineYaml = getPipelineYaml();
    List<BasicStageInfo> stageYamlList = StageExecutionSelectorHelper.getStageInfoList(pipelineYaml);
    assertBasicStageInfo(stageYamlList);
  }

  private void assertBasicStageInfo(List<BasicStageInfo> stageYamlList) {
    assertThat(stageYamlList).hasSize(6);
    assertThat(stageYamlList.get(0).getIdentifier()).isEqualTo("a1");
    assertThat(stageYamlList.get(1).getIdentifier()).isEqualTo("a2");
    assertThat(stageYamlList.get(2).getIdentifier()).isEqualTo("d1");
    assertThat(stageYamlList.get(3).getIdentifier()).isEqualTo("p_d1");
    assertThat(stageYamlList.get(4).getIdentifier()).isEqualTo("p_d2");
    assertThat(stageYamlList.get(5).getIdentifier()).isEqualTo("d1_again");

    assertThat(stageYamlList.get(0).getName()).isEqualTo("a1");
    assertThat(stageYamlList.get(1).getName()).isEqualTo("a2");
    assertThat(stageYamlList.get(2).getName()).isEqualTo("d1");
    assertThat(stageYamlList.get(3).getName()).isEqualTo("p d1");
    assertThat(stageYamlList.get(4).getName()).isEqualTo("p d2");
    assertThat(stageYamlList.get(5).getName()).isEqualTo("d1 again");

    assertThat(stageYamlList.get(0).getType()).isEqualTo("Approval");
    assertThat(stageYamlList.get(1).getType()).isEqualTo("Approval");
    assertThat(stageYamlList.get(2).getType()).isEqualTo("Deployment");
    assertThat(stageYamlList.get(3).getType()).isEqualTo("Deployment");
    assertThat(stageYamlList.get(4).getType()).isEqualTo("Deployment");
    assertThat(stageYamlList.get(5).getType()).isEqualTo("Deployment");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAddStagesRequired() {
    String pipelineYaml = getPipelineYamlForStagesRequired();
    List<BasicStageInfo> stageYamlList = StageExecutionSelectorHelper.getStageInfoList(pipelineYaml);
    List<BasicStageInfo> res = StageExecutionSelectorHelper.addStagesRequired(stageYamlList);
    assertThat(res).hasSize(4);
    assertThat(res.get(0).getStagesRequired()).containsExactlyInAnyOrder("s2", "s3");
    assertThat(res.get(1).getStagesRequired()).containsExactlyInAnyOrder("s3", "s1");
    assertThat(res.get(2).getStagesRequired()).containsExactlyInAnyOrder("s1", "s2");
    assertThat(res.get(3).getStagesRequired()).hasSize(0);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAddStagesRequiredStartingWithApproval() {
    BasicStageInfo stageInfo0 = BasicStageInfo.builder().name("a0").identifier("a0").type("Approval").build();
    BasicStageInfo stageInfo1 = BasicStageInfo.builder().name("a1").identifier("a1").type("Approval").build();
    BasicStageInfo stageInfo2 = BasicStageInfo.builder().name("d0").identifier("d0").type("Deployment").build();
    BasicStageInfo stageInfo3 = BasicStageInfo.builder().name("d1").identifier("d1").type("Deployment").build();
    BasicStageInfo stageInfo4 = BasicStageInfo.builder().name("a2").identifier("a2").type("Approval").build();
    BasicStageInfo stageInfo5 = BasicStageInfo.builder().name("d2").identifier("d2").type("Deployment").build();
    List<BasicStageInfo> stageInfos =
        Arrays.asList(stageInfo0, stageInfo1, stageInfo2, stageInfo3, stageInfo4, stageInfo5);
    List<BasicStageInfo> stageInfosWithRequiredStages =
        StageExecutionSelectorHelper.addApprovalStagesRequired(stageInfos);
    assertThat(stageInfosWithRequiredStages).hasSize(6);
    assertThat(stageInfosWithRequiredStages.get(0).getStagesRequired().toString()).isEqualTo("[]");
    assertThat(stageInfosWithRequiredStages.get(1).getStagesRequired().toString()).isEqualTo("[a0]");
    assertThat(stageInfosWithRequiredStages.get(2).getStagesRequired().toString()).isEqualTo("[a0, a1]");
    assertThat(stageInfosWithRequiredStages.get(3).getStagesRequired().toString()).isEqualTo("[a0, a1]");
    assertThat(stageInfosWithRequiredStages.get(4).getStagesRequired().toString()).isEqualTo("[]");
    assertThat(stageInfosWithRequiredStages.get(5).getStagesRequired().toString()).isEqualTo("[a2]");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAddStagesRequiredStartingWithNonApproval() {
    BasicStageInfo stageInfo0 = BasicStageInfo.builder().name("b0").identifier("b0").type("Build").build();
    BasicStageInfo stageInfo1 = BasicStageInfo.builder().name("d1").identifier("d1").type("Deployment").build();
    BasicStageInfo stageInfo2 = BasicStageInfo.builder().name("a2").identifier("a2").type("Approval").build();
    BasicStageInfo stageInfo3 = BasicStageInfo.builder().name("d2").identifier("d2").type("Deployment").build();
    List<BasicStageInfo> stageInfos = Arrays.asList(stageInfo0, stageInfo1, stageInfo2, stageInfo3);
    List<BasicStageInfo> stageInfosWithRequiredStages =
        StageExecutionSelectorHelper.addApprovalStagesRequired(stageInfos);
    assertThat(stageInfosWithRequiredStages).hasSize(4);
    assertThat(stageInfosWithRequiredStages.get(0).getStagesRequired().toString()).isEqualTo("[]");
    assertThat(stageInfosWithRequiredStages.get(1).getStagesRequired().toString()).isEqualTo("[]");
    assertThat(stageInfosWithRequiredStages.get(2).getStagesRequired().toString()).isEqualTo("[]");
    assertThat(stageInfosWithRequiredStages.get(3).getStagesRequired().toString()).isEqualTo("[a2]");
  }

  private String getPipelineYaml() {
    return "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "     identifier: a1\n"
        + "     name: a1\n"
        + "     type: Approval\n"
        + "  - stage:\n"
        + "     identifier: a2\n"
        + "     name: a2\n"
        + "     type: Approval\n"
        + "  - stage:\n"
        + "      identifier: d1\n"
        + "      name: d1\n"
        + "      type: Deployment\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: p_d1\n"
        + "        name: p d1\n"
        + "        type: Deployment\n"
        + "    - stage:\n"
        + "        identifier: p_d2\n"
        + "        name: p d2\n"
        + "        type: Deployment\n"
        + "  - stage:\n"
        + "      identifier: d1_again\n"
        + "      name: d1 again\n"
        + "      type: Deployment";
  }

  private String getPipelineWithStageTemplates() {
    return "pipeline:\n"
        + "    name: nv-templates\n"
        + "    identifier: nvtemplates\n"
        + "    allowStageExecutions: false\n"
        + "    projectIdentifier: naidusanity\n"
        + "    orgIdentifier: default\n"
        + "    tags: {}\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: d1\n"
        + "              identifier: d1\n"
        + "              template:\n"
        + "                  templateRef: run_10_tests\n"
        + "                  versionLabel: \"1.0\"\n"
        + "        - stage:\n"
        + "              name: d2\n"
        + "              identifier: d2\n"
        + "              template:\n"
        + "                  templateRef: D1\n"
        + "                  versionLabel: Version1\n"
        + "                  templateInputs:\n"
        + "                      type: Deployment\n"
        + "                      spec:\n"
        + "                          infrastructure:\n"
        + "                              infrastructureDefinition:\n"
        + "                                  type: KubernetesDirect\n"
        + "                                  spec:\n"
        + "                                      namespace: <+input>\n";
  }

  private String getPipelineYamlForStagesRequired() {
    return "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        name: s1\n"
        + "        type: Deployment\n"
        + "        spec:\n"
        + "          serviceConfig:\n"
        + "            useFromStage:\n"
        + "              stage: s3\n"
        + "          infrastructure:\n"
        + "            useFromStage:\n"
        + "              stage: s2\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        name: s2\n"
        + "        type: Deployment\n"
        + "        spec:\n"
        + "          something:\n"
        + "            useFromStage:\n"
        + "              stage: s3\n"
        + "          somethingElse:\n"
        + "            useFromStage:\n"
        + "              stage: s1\n"
        + "    - stage:\n"
        + "        identifier: s3\n"
        + "        name: s3\n"
        + "        type: Deployment\n"
        + "        manifests:\n"
        + "          - manifest:\n"
        + "              identifier: m1\n"
        + "              useFromStage:\n"
        + "                stage: s2\n"
        + "          - manifest:\n"
        + "              identifier: m2\n"
        + "              useFromStage:\n"
        + "                stage: s3\n"
        + "          - manifest:\n"
        + "              identifier: m3\n"
        + "              useFromStage:\n"
        + "                stage: s1\n"
        + "    - stage:\n"
        + "        identifier: s4\n"
        + "        name: s4\n"
        + "        type: Deployment\n"
        + "        manifests:\n"
        + "          - manifest:\n"
        + "              identifier: m1\n";
  }

  private String getPipelineYamlForStagesRequiredWithCIUseFromStage() {
    return "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        name: s1\n"
        + "        type: Deployment\n"
        + "        spec:\n"
        + "          serviceConfig:\n"
        + "            useFromStage: s3\n"
        + "          infrastructure:\n"
        + "            useFromStage: s2\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        name: s2\n"
        + "        type: Deployment\n"
        + "        spec:\n"
        + "          something:\n"
        + "            useFromStage: s3\n"
        + "          somethingElse:\n"
        + "            useFromStage: s1\n"
        + "    - stage:\n"
        + "        identifier: s3\n"
        + "        name: s3\n"
        + "        type: Deployment\n"
        + "        manifests:\n"
        + "          - manifest:\n"
        + "              identifier: m1\n"
        + "              useFromStage: s2\n"
        + "          - manifest:\n"
        + "              identifier: m2\n"
        + "              useFromStage: s3\n"
        + "          - manifest:\n"
        + "              identifier: m3\n"
        + "              useFromStage: s1\n"
        + "    - stage:\n"
        + "        identifier: s4\n"
        + "        name: s4\n"
        + "        type: Deployment\n"
        + "        manifests:\n"
        + "          - manifest:\n"
        + "              identifier: m1\n";
  }
}
