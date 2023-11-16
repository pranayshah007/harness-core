/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.steps.plugin.ContainerStepConstants.PLUGIN;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.pipeline.CommonStepInfo;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepData;
import io.harness.pms.pipeline.StepPalleteInfo;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ECS, HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineServiceStepHelper {
  @Inject private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private final CommonStepInfo commonStepInfo;
  @Inject private final PipelineEnforcementService pipelineEnforcementService;

  @VisibleForTesting public static String LIBRARY = "Library";
  @VisibleForTesting public static String COMMON = "common";

  /**
   * Filters the step to be shown in the UI based on a feature flag
   * @param stepInfoList
   * @param accountId
   * @return
   */
  public List<StepInfo> filterStepsBasedOnFeatureFlag(List<StepInfo> stepInfoList, String accountId) {
    try {
      List<StepInfo> ffEnabledStepInfoList = new ArrayList<>();
      if (!stepInfoList.isEmpty()) {
        ffEnabledStepInfoList = stepInfoList.stream()
                                    .filter(stepInfo
                                        -> EmptyPredicate.isEmpty(stepInfo.getFeatureFlag())
                                            || pmsFeatureFlagHelper.isEnabled(accountId, stepInfo.getFeatureFlag()))
                                    .collect(Collectors.toList());
      }
      return ffEnabledStepInfoList;
    } catch (Exception ex) {
      log.error(ex.getMessage());
      throw new InvalidRequestException(String.format("Could not fetch feature flags for accountID: %s", accountId));
    }
  }

  public StepCategory calculateStepsForCategory(String module, List<StepInfo> stepInfoList, String accountId) {
    StepCategory stepCategory = StepCategory.builder().name(module).build();
    return addStepsToStepCategory(stepCategory, stepInfoList, accountId);
  }

  public StepCategory addStepsToStepCategory(StepCategory stepCategory, List<StepInfo> stepInfoList, String accountId) {
    List<StepInfo> ffEnabledStepInfoList = filterStepsBasedOnFeatureFlag(stepInfoList, accountId);
    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap = new HashMap<>();

    try {
      featureRestrictionNameBooleanMap = pipelineEnforcementService.getFeatureRestrictionMap(accountId,
          ffEnabledStepInfoList.stream()
              .filter(stepInfo -> EmptyPredicate.isNotEmpty(stepInfo.getFeatureRestrictionName()))
              .map(StepInfo::getFeatureRestrictionName)
              .collect(Collectors.toSet()));
    } catch (Exception e) {
      log.error("Failed to get feature restriction map for the step category.", e);
    }
    for (StepInfo stepType : ffEnabledStepInfoList) {
      addToTopLevel(stepCategory, stepType, featureRestrictionNameBooleanMap);
    }
    return stepCategory;
  }

  public StepCategory calculateStepsForModuleBasedOnCategory(
      String category, List<StepInfo> stepInfoList, String accountId) {
    List<StepInfo> filteredStepTypes = new ArrayList<>();
    if (!stepInfoList.isEmpty()) {
      filteredStepTypes =
          stepInfoList.stream()
              .filter(stepInfo
                  -> EmptyPredicate.isEmpty(category) || stepInfo.getStepMetaData().getCategoryList().contains(category)
                      || EmptyPredicate.isEmpty(stepInfo.getStepMetaData().getCategoryList()))
              .collect(Collectors.toList());
    }
    filteredStepTypes.addAll(commonStepInfo.getCommonSteps(category));
    return calculateStepsForCategory(LIBRARY, filteredStepTypes, accountId);
  }

  public StepCategory calculateStepsForModuleBasedOnCategoryV2(
      String module, String category, List<StepInfo> stepInfoList, String accountId) {
    List<StepInfo> filteredStepTypes = new ArrayList<>();
    if (!stepInfoList.isEmpty()) {
      // Todo: This is a hack added for showing only few steps in Container Step V2 from CI. This is to support Aws Sam
      // functionality Come up with a proper fix for this
      if (EmptyPredicate.isNotEmpty(module) && EmptyPredicate.isNotEmpty(category)
          && module.equalsIgnoreCase(ModuleType.CI.getDisplayName()) && category.equalsIgnoreCase(PLUGIN)) {
        filteredStepTypes = stepInfoList.stream()
                                .filter(stepInfo
                                    -> EmptyPredicate.isNotEmpty(category)
                                        && stepInfo.getStepMetaData().getCategoryList().contains(category)
                                        && EmptyPredicate.isNotEmpty(stepInfo.getStepMetaData().getCategoryList()))
                                .collect(Collectors.toList());
      } else {
        filteredStepTypes = stepInfoList.stream()
                                .filter(stepInfo
                                    -> EmptyPredicate.isEmpty(category)
                                        || stepInfo.getStepMetaData().getCategoryList().contains(category)
                                        || EmptyPredicate.isEmpty(stepInfo.getStepMetaData().getCategoryList()))
                                .collect(Collectors.toList());
      }
    }
    return calculateStepsForCategory(module, filteredStepTypes, accountId);
  }

  public void addToTopLevel(StepCategory stepCategory, StepInfo stepInfo,
      Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap) {
    if (stepInfo != null) {
      List<String> folderPaths = stepInfo.getStepMetaData().getFolderPathsList();
      if (EmptyPredicate.isEmpty(folderPaths)) {
        folderPaths = new ArrayList<>();
        folderPaths.add(stepInfo.getStepMetaData().getFolderPath());
      }
      for (String folderPath : folderPaths) {
        StepCategory currentStepCategory = stepCategory;
        String[] categoryArrayName = folderPath.split("/");
        for (String categoryName : categoryArrayName) {
          currentStepCategory = currentStepCategory.getOrCreateChildStepCategory(categoryName);
        }
        boolean disabled = !stepInfo.getFeatureRestrictionName().isEmpty()
            && !featureRestrictionNameBooleanMap.get(
                FeatureRestrictionName.valueOf(stepInfo.getFeatureRestrictionName()));
        if (!stepInfo.getFeatureRestrictionName().isEmpty()) {
          currentStepCategory.addStepData(
              StepData.builder()
                  .name(stepInfo.getName())
                  .type(stepInfo.getType())
                  .disabled(disabled)
                  .featureRestrictionName(FeatureRestrictionName.valueOf(stepInfo.getFeatureRestrictionName()))
                  .build());
        } else {
          currentStepCategory.addStepData(
              StepData.builder().name(stepInfo.getName()).type(stepInfo.getType()).disabled(disabled).build());
        }
      }
    }
  }

  public StepCategory getAllSteps(String accountId, Map<String, StepPalleteInfo> serviceInstanceNameToSupportedSteps) {
    StepCategory stepCategory = StepCategory.builder().name(LIBRARY).build();
    for (Map.Entry<String, StepPalleteInfo> entry : serviceInstanceNameToSupportedSteps.entrySet()) {
      StepCategory moduleCategory =
          calculateStepsForCategory(entry.getValue().getModuleName(), entry.getValue().getStepTypes(), accountId);
      stepCategory.addStepCategory(moduleCategory);
      addStepsToStepCategory(moduleCategory, commonStepInfo.getCommonSteps(""), accountId);
    }

    return stepCategory;
  }
}
