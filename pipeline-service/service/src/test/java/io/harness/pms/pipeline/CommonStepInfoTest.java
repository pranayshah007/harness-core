/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class CommonStepInfoTest extends CategoryTest {
  @InjectMocks CommonStepInfo commonStepInfo;
  StepInfo shellScriptStepInfo =
      StepInfo.newBuilder()
          .setName("Shell Script")
          .setType("ShellScript")
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Scripted").build())
          .build();
  StepInfo httpStepInfo =
      StepInfo.newBuilder()
          .setName("HTTP")
          .setType("Http")
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Non-Scripted").build())
          .build();
  StepInfo harnessApprovalStepInfo =
      StepInfo.newBuilder()
          .setName("Harness Approval")
          .setType("HarnessApproval")
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory("Provisioner")
                               .addCategory("Approval")
                               .addFolderPaths("Approval")
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_HARNESS_UI.name())

          .build();
  StepInfo jiraApprovalStepInfo =
      StepInfo.newBuilder()
          .setName("Jira Approval")
          .setType("JiraApproval")
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory("Provisioner")
                               .addCategory("Approval")
                               .addFolderPaths("Approval")
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_JIRA.name())
          .build();
  StepInfo jiraCreateStepInfo =
      StepInfo.newBuilder()
          .setName("Jira Create")
          .setType(StepSpecTypeConstants.JIRA_CREATE)
          .setStepMetaData(StepMetaData.newBuilder().addCategory("Jira").addFolderPaths("Jira").build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_JIRA.name())
          .build();
  StepInfo jiraUpdateStepInfo =
      StepInfo.newBuilder()
          .setName("Jira Update")
          .setType(StepSpecTypeConstants.JIRA_UPDATE)
          .setStepMetaData(StepMetaData.newBuilder().addCategory("Jira").addFolderPaths("Jira").build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_JIRA.name())
          .build();
  StepInfo barrierStepInfo =
      StepInfo.newBuilder()
          .setName("Barrier")
          .setType("Barrier")
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("FlowControl/Barrier").build())
          .build();
  StepInfo serviceNowApprovalStepInfo =
      StepInfo.newBuilder()
          .setName("ServiceNow Approval")
          .setType("ServiceNowApproval")
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory("Provisioner")
                               .addCategory("Approval")
                               .addFolderPaths("Approval")
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_SERVICE_NOW.name())
          .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetCommonSteps() {
    List<StepInfo> stepInfos = commonStepInfo.getCommonSteps("Approval");
    assertTrue(stepInfos.contains(httpStepInfo));
    assertTrue(stepInfos.contains(harnessApprovalStepInfo));
    assertTrue(stepInfos.contains(jiraApprovalStepInfo));
    assertTrue(stepInfos.contains(jiraCreateStepInfo));
    assertTrue(stepInfos.contains(jiraUpdateStepInfo));
    assertTrue(stepInfos.contains(barrierStepInfo));
    stepInfos = commonStepInfo.getCommonSteps("NotApproval");
    assertTrue(stepInfos.contains(httpStepInfo));
    assertTrue(stepInfos.contains(harnessApprovalStepInfo));
    assertTrue(stepInfos.contains(jiraApprovalStepInfo));
    assertTrue(stepInfos.contains(jiraCreateStepInfo));
    assertTrue(stepInfos.contains(jiraUpdateStepInfo));
    assertTrue(stepInfos.contains(barrierStepInfo));
    assertTrue(stepInfos.contains(shellScriptStepInfo));
    assertTrue(stepInfos.contains(serviceNowApprovalStepInfo));
  }
}
