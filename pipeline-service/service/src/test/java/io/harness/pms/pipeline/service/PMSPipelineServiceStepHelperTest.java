/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SAMARTH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.pipeline.CommonStepInfo;
import io.harness.pms.pipeline.StepCategory;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PMSPipelineServiceStepHelperTest extends CategoryTest {
  @InjectMocks private PMSPipelineServiceStepHelper pmsPipelineServiceStepHelper;
  @Mock private CommonStepInfo commonStepInfo;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private PipelineEnforcementService pipelineEnforcementService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    pmsPipelineServiceStepHelper =
        new PMSPipelineServiceStepHelper(pmsFeatureFlagHelper, commonStepInfo, pipelineEnforcementService);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testFilterStepsOnFeatureFlag() {
    List<StepInfo> expectedStepInfoList = new ArrayList<>();
    expectedStepInfoList.add(StepInfo.newBuilder()
                                 .setName("testStepCV")
                                 .setType("testStepCV")
                                 .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Double/Single").build())
                                 .build());
    expectedStepInfoList.add(StepInfo.newBuilder()
                                 .setName("testStepCV1")
                                 .setType("testStepCV1")
                                 .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Double/Single").build())
                                 .build());

    List<StepInfo> actualStepInfoList =
        pmsPipelineServiceStepHelper.filterStepsBasedOnFeatureFlag(expectedStepInfoList, "accountId");

    assertThat(actualStepInfoList).isEqualTo(expectedStepInfoList);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCalculateStepsForCategory() {
    List<StepInfo> stepInfoList = new ArrayList<>();
    stepInfoList.add(StepInfo.newBuilder()
                         .setName("testStepCV")
                         .setType("testStepCV")
                         .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Double/Single").build())
                         .build());
    stepInfoList.add(StepInfo.newBuilder()
                         .setName("testStepCV1")
                         .setType("testStepCV1")
                         .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Double/Single").build())
                         .build());
    String module = "cv";
    StepCategory stepCategory =
        pmsPipelineServiceStepHelper.calculateStepsForCategory(module, stepInfoList, "accountId");
    assertThat(stepCategory).isNotNull();
    assertThat(stepCategory.toString())
        .isEqualTo(
            "StepCategory(name=cv, stepsData=[], stepCategories=[StepCategory(name=Double, stepsData=[], stepCategories=[StepCategory(name=Single, stepsData=[StepData(name=testStepCV, type=testStepCV, disabled=false, featureRestrictionName=null), StepData(name=testStepCV1, type=testStepCV1, disabled=false, featureRestrictionName=null)], stepCategories=[])])])");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCalculateStepsForModuleBasedOnCategory() {
    Mockito.when(commonStepInfo.getCommonSteps(any())).thenReturn(new ArrayList<>());
    List<StepInfo> stepInfoList = new ArrayList<>();
    stepInfoList.add(
        StepInfo.newBuilder()
            .setName("testStepCD")
            .setType("testStepCD")
            .setStepMetaData(StepMetaData.newBuilder().addCategory("K8S").addFolderPaths("Double/Single").build())
            .build());
    stepInfoList.add(StepInfo.newBuilder()
                         .setName("testStepCV")
                         .setType("testStepCV")
                         .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Double/Single").build())
                         .build());
    StepCategory stepCategory =
        pmsPipelineServiceStepHelper.calculateStepsForModuleBasedOnCategory("Terraform", stepInfoList, "account");
    assertThat(stepCategory).isNotNull();
    assertThat(stepCategory.toString())
        .isEqualTo(
            "StepCategory(name=Library, stepsData=[], stepCategories=[StepCategory(name=Double, stepsData=[], stepCategories=[StepCategory(name=Single, stepsData=[StepData(name=testStepCV, type=testStepCV, disabled=false, featureRestrictionName=null)], stepCategories=[])])])");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAddToTopLevel() {
    StepInfo stepInfo = StepInfo.newBuilder()
                            .setName("testStepCV")
                            .setType("testStepCV")
                            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Double/Single").build())
                            .build();
    StepCategory stepCategory = StepCategory.builder().name("cv").build();
    pmsPipelineServiceStepHelper.addToTopLevel(stepCategory, stepInfo, new HashMap<>());
    assertThat(stepCategory.toString())
        .isEqualTo(
            "StepCategory(name=cv, stepsData=[], stepCategories=[StepCategory(name=Double, stepsData=[], stepCategories=[StepCategory(name=Single, stepsData=[StepData(name=testStepCV, type=testStepCV, disabled=false, featureRestrictionName=null)], stepCategories=[])])])");
  }
}
