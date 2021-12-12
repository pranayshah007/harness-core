package io.harness.pms.pipeline.service;

import io.harness.ModuleType;
import io.harness.PipelineUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.exceptions.FeatureNotSupportedException;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.pipeline.CommonStepInfo;
import io.harness.pms.plan.creation.PlanCreatorServiceInfo;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.PmsSdkHelper;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.YamlField;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PipelineEnforcementServiceImpl implements PipelineEnforcementService {
  private static final String DEPLOYMENT_EXCEEDED_KEY = "DeploymentExceeded";
  private static final String BUILD_EXCEEDED_KEY = "BuildExceeded";

  private static final String EXECUTION_ERROR = "Your current plan does not support the use of following steps: %s.";
  private static final String UPGRADE_YOUR_PLAN_ERROR_MESSAGE = "Please upgrade your plan.";
  private static final Map<String, String> stageTypeToModule = new ConcurrentHashMap<>();

  @Inject PmsSdkInstanceService pmsSdkInstanceService;
  @Inject EnforcementClientService enforcementClientService;
  @Inject CommonStepInfo commonStepInfo;
  @Inject PmsSdkHelper pmsSdkHelper;

  @Override
  public boolean isFeatureRestricted(String accountId, String featureRestrictionName) {
    return enforcementClientService.isAvailable(FeatureRestrictionName.valueOf(featureRestrictionName), accountId);
  }

  @Override
  public Map<FeatureRestrictionName, Boolean> getFeatureRestrictionMap(
      String accountId, Set<String> featureRestrictionNameList) {
    Set<FeatureRestrictionName> featureRestrictionNames =
        featureRestrictionNameList.stream().map(FeatureRestrictionName::valueOf).collect(Collectors.toSet());
    return enforcementClientService.getAvailabilityForRemoteFeatures(
        new ArrayList<>(featureRestrictionNames), accountId);
  }

  @Override
  public Set<FeatureRestrictionName> getDisabledFeatureRestrictionNames(
      String accountId, Set<String> featureRestrictionNameList) {
    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap =
        getFeatureRestrictionMap(accountId, featureRestrictionNameList);
    Set<FeatureRestrictionName> disabledFeatures = new HashSet<>();
    for (Map.Entry<FeatureRestrictionName, Boolean> entry : featureRestrictionNameBooleanMap.entrySet()) {
      if (entry.getValue() == Boolean.FALSE) {
        disabledFeatures.add(entry.getKey());
      }
    }
    return disabledFeatures;
  }

  @Override
  public void validateExecutionEnforcementsBasedOnStage(String accountId, YamlField pipelineField) {
    Set<YamlField> stageFields = PipelineUtils.getStagesFieldFromPipeline(pipelineField);
    Set<String> modules = new HashSet<>();
    Set<YamlField> nonCachedStageYamlFields = new HashSet<>();
    for (YamlField stageField : stageFields) {
      if (stageTypeToModule.containsKey(stageField.getNode().getType())) {
        modules.add(stageTypeToModule.get(stageField.getNode().getType()));
      } else {
        nonCachedStageYamlFields.add(stageField);
      }
    }

    if (!nonCachedStageYamlFields.isEmpty()) {
      Map<String, PlanCreatorServiceInfo> services = pmsSdkHelper.getServices();
      for (Map.Entry<String, PlanCreatorServiceInfo> planCreatorServiceInfoEntry : services.entrySet()) {
        Map<String, Set<String>> supportedTypes = planCreatorServiceInfoEntry.getValue().getSupportedTypes();
        for (YamlField stageField : stageFields) {
          if (stageTypeToModule.containsKey(stageField.getNode().getType())) {
            modules.add(stageTypeToModule.get(stageField.getNode().getType()));
          } else {
            if (PlanCreatorUtils.supportsField(supportedTypes, stageField)) {
              modules.add(planCreatorServiceInfoEntry.getKey());
              stageTypeToModule.put(stageField.getNode().getType(), planCreatorServiceInfoEntry.getKey());
            }
          }
        }
      }
    }
    validateExecutionFeatureRestrictions(accountId, modules);
  }

  private void validateExecutionFeatureRestrictions(String accountId, Set<String> modules) {
    Multimap<String, String> featureRestrictionToStepNameMap = HashMultimap.create();
    // Add featureRestriction based on executions (Builds or deployments)
    for (String module : modules) {
      // Todo: Take via PmsSdkInstance
      if (module.equalsIgnoreCase(ModuleType.CD.name())) {
        featureRestrictionToStepNameMap.put(
            FeatureRestrictionName.DEPLOYMENTS_PER_MONTH.name(), DEPLOYMENT_EXCEEDED_KEY);
      } else if (module.equalsIgnoreCase(ModuleType.CI.name())) {
        featureRestrictionToStepNameMap.put(FeatureRestrictionName.BUILDS.name(), BUILD_EXCEEDED_KEY);
      }
    }

    Set<FeatureRestrictionName> disabledFeatures =
        getDisabledFeatureRestrictionNames(accountId, featureRestrictionToStepNameMap.keySet());
    if (disabledFeatures.isEmpty()) {
      return;
    }
    throw new FeatureNotSupportedException(constructErrorMessage(featureRestrictionToStepNameMap, disabledFeatures));
  }

  /**
   * NOTE: Use this function during execution only.
   */
  @Override
  public void validatePipelineExecutionRestriction(String accountId, Set<StepType> stepTypes) {
    Map<String, Set<SdkStep>> sdkSteps = pmsSdkInstanceService.getSdkSteps();
    Multimap<String, String> featureRestrictionToStepNamesMap =
        getFeatureRestrictionMapFromUsedSteps(sdkSteps, stepTypes);
    Set<FeatureRestrictionName> disabledFeatures =
        getDisabledFeatureRestrictionNames(accountId, featureRestrictionToStepNamesMap.keySet());
    if (disabledFeatures.isEmpty()) {
      return;
    }
    throw new FeatureNotSupportedException(constructErrorMessage(featureRestrictionToStepNamesMap, disabledFeatures));
  }

  /**
   * returns a map of feature restriction to the stepNames on which the feature is present.
   * @param sdkSteps
   * @param stepTypes
   * @return
   */
  private Multimap<String, String> getFeatureRestrictionMapFromUsedSteps(
      Map<String, Set<SdkStep>> sdkSteps, Set<StepType> stepTypes) {
    Multimap<String, String> featureRestrictionToStepNameMap = HashMultimap.create();
    Set<String> modules = new HashSet<>();

    // Add featureRestriction based on steps from all modules
    for (Map.Entry<String, Set<SdkStep>> entry : sdkSteps.entrySet()) {
      for (SdkStep sdkStep : entry.getValue()) {
        if (stepTypes.contains(sdkStep.getStepType())) {
          if (sdkStep.hasStepInfo() && EmptyPredicate.isNotEmpty(sdkStep.getStepInfo().getFeatureRestrictionName())) {
            featureRestrictionToStepNameMap.put(
                sdkStep.getStepInfo().getFeatureRestrictionName(), sdkStep.getStepInfo().getName());
          }
          if (sdkStep.getStepType().getStepCategory() == StepCategory.STAGE) {
            modules.add(entry.getKey());
          }
        }
      }
    }
    // Add featureRestriction based on common steps
    List<String> stepTypeString = stepTypes.stream().map(StepType::getType).collect(Collectors.toList());
    for (StepInfo stepInfo : commonStepInfo.getCommonSteps("")) {
      if (stepTypeString.contains(stepInfo.getType())
          && EmptyPredicate.isNotEmpty(stepInfo.getFeatureRestrictionName())) {
        featureRestrictionToStepNameMap.put(stepInfo.getFeatureRestrictionName(), stepInfo.getName());
      }
    }
    return featureRestrictionToStepNameMap;
  }

  private String constructErrorMessage(
      Multimap<String, String> featureRestrictionToStepNamesMap, Set<FeatureRestrictionName> disabledFeatures) {
    Set<String> disabledSteps = new HashSet<>();
    boolean deploymentsExceeded = false;
    boolean buildsExceeded = false;
    for (FeatureRestrictionName featureRestrictionName : disabledFeatures) {
      if (isExecutionFeatureRestriction(featureRestrictionName)) {
        continue;
      }
      // Todo: Take via pmsSdkInstance
      if (FeatureRestrictionName.DEPLOYMENTS_PER_MONTH.equals(featureRestrictionName)) {
        deploymentsExceeded = true;
        continue;
      }
      if (FeatureRestrictionName.BUILDS.equals(featureRestrictionName)) {
        buildsExceeded = true;
        continue;
      }
      disabledSteps.addAll(featureRestrictionToStepNamesMap.get(featureRestrictionName.name()));
    }
    StringBuilder stringBuilder = new StringBuilder(40);
    if (!disabledSteps.isEmpty()) {
      stringBuilder.append(String.format(EXECUTION_ERROR, disabledSteps));
    }
    if (deploymentsExceeded && buildsExceeded) {
      stringBuilder.append("You have exceeded max number of deployments and builds.");
    } else if (deploymentsExceeded) {
      stringBuilder.append("You have exceeded max number of deployments.");
    } else if (buildsExceeded) {
      stringBuilder.append("You have exceeded max number of builds.");
    }
    stringBuilder.append(UPGRADE_YOUR_PLAN_ERROR_MESSAGE);
    return stringBuilder.toString();
  }

  private boolean isExecutionFeatureRestriction(FeatureRestrictionName featureRestrictionName) {
    return ImmutableSet.of(FeatureRestrictionName.INITIAL_DEPLOYMENTS, FeatureRestrictionName.DEPLOYMENTS)
        .contains(featureRestrictionName);
  }
}
