/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.yaml.core.MatrixConstants.MATRIX_IDENTIFIER_POSTFIX_FOR_DUPLICATES;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.strategy.StrategyValidationUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@Slf4j
@OwnedBy(PIPELINE)
public class AmbianceUtils {
  public static final String STAGE = "STAGE";
  public static final String SPECIAL_CHARACTER_REGEX = "[^a-zA-Z0-9]";
  public static final String PIE_SIMPLIFY_LOG_BASE_KEY = "PIE_SIMPLIFY_LOG_BASE_KEY";
  public static final String PIE_SECRETS_OBSERVER = "PIE_SECRETS_OBSERVER";

  public static Ambiance cloneForFinish(@NonNull Ambiance ambiance) {
    return clone(ambiance, ambiance.getLevelsList().size() - 1);
  }

  public static Ambiance cloneForFinish(@NonNull Ambiance ambiance, Level level) {
    Ambiance.Builder builder = cloneBuilder(ambiance, ambiance.getLevelsList().size() - 1);
    return builder.addLevels(level).build();
  }

  public static String getRuntimeIdForGivenCategory(@NonNull Ambiance ambiance, StepCategory category) {
    Optional<Level> stageLevel = Optional.empty();
    for (Level level : ambiance.getLevelsList()) {
      if (level.getStepType().getStepCategory() == category) {
        stageLevel = Optional.of(level);
      }
    }
    return stageLevel.get().getRuntimeId();
  }

  public String getStageSetupIdAmbiance(Ambiance ambiance) {
    Optional<Level> stageLevel = getStageLevelFromAmbiance(ambiance);
    if (stageLevel.isPresent()) {
      return stageLevel.get().getSetupId();
    }
    throw new InvalidRequestException("Stage not present");
  }

  public static Ambiance cloneForChild(@NonNull Ambiance ambiance, @NonNull Level level) {
    Ambiance.Builder builder = cloneBuilder(ambiance, ambiance.getLevelsList().size());
    return builder.addLevels(level).build();
  }

  public static Ambiance.Builder cloneBuilder(Ambiance ambiance, int levelsToKeep) {
    return clone(ambiance, levelsToKeep).toBuilder();
  }

  public static Ambiance clone(Ambiance ambiance, int levelsToKeep) {
    Ambiance.Builder clonedBuilder = ambiance.toBuilder().clone();
    if (levelsToKeep >= 0 && levelsToKeep < ambiance.getLevelsList().size()) {
      List<Level> clonedLevels = clonedBuilder.getLevelsList().subList(0, levelsToKeep);
      clonedBuilder.clearLevels();
      clonedBuilder.addAllLevels(clonedLevels);
    }
    return clonedBuilder.build();
  }

  @VisibleForTesting
  static Ambiance deepCopy(Ambiance ambiance) throws InvalidProtocolBufferException {
    return Ambiance.parseFrom(ambiance.toByteString());
  }

  public static String obtainCurrentRuntimeId(Ambiance ambiance) {
    Level level = obtainCurrentLevel(ambiance);
    return level == null || isEmpty(level.getRuntimeId()) ? null : level.getRuntimeId();
  }

  public static String obtainCurrentSetupId(Ambiance ambiance) {
    Level level = obtainCurrentLevel(ambiance);
    return level == null || isEmpty(level.getSetupId()) ? null : level.getSetupId();
  }

  public static String obtainNodeType(Ambiance ambiance) {
    Level level = obtainCurrentLevel(ambiance);
    return level == null || isEmpty(level.getNodeType()) ? null : level.getNodeType();
  }

  public static Level obtainCurrentLevel(Ambiance ambiance) {
    if (ambiance == null || isEmpty(ambiance.getLevelsList())) {
      return null;
    }
    return ambiance.getLevelsList().get(ambiance.getLevelsList().size() - 1);
  }

  public static Level obtainParentLevel(Ambiance ambiance) {
    if (isEmpty(ambiance.getLevelsList()) || ambiance.getLevelsCount() == 1) {
      return null;
    }
    return ambiance.getLevelsList().get(ambiance.getLevelsList().size() - 2);
  }

  public static String obtainStepIdentifier(Ambiance ambiance) {
    Level level = obtainCurrentLevel(ambiance);
    return level == null || isEmpty(level.getIdentifier()) ? null : level.getIdentifier();
  }

  public static String obtainStepGroupIdentifier(Ambiance ambiance) {
    Level level = null;
    Optional<Level> levelOptional = getStepGroupLevelFromAmbiance(ambiance);
    if (levelOptional.isPresent()) {
      level = levelOptional.get();
    }
    return level == null || isEmpty(level.getIdentifier()) ? null : level.getIdentifier();
  }

  public static AutoLogContext autoLogContext(Ambiance ambiance) {
    return new AutoLogContext(logContextMap(ambiance), OVERRIDE_NESTS);
  }

  public static Map<String, String> logContextMap(Ambiance ambiance) {
    Map<String, String> logContext = ambiance.getSetupAbstractionsMap() == null
        ? new HashMap<>()
        : new HashMap<>(ambiance.getSetupAbstractionsMap());
    logContext.put("planExecutionId", ambiance.getPlanExecutionId());
    Level level = obtainCurrentLevel(ambiance);
    if (level != null) {
      logContext.put("identifier", level.getIdentifier());
      logContext.put("runtimeId", level.getRuntimeId());
      logContext.put("setupId", level.getSetupId());
      logContext.put("stepType", level.getStepType().getType());
    }
    if (ambiance.getMetadata() != null && ambiance.getMetadata().getPipelineIdentifier() != null) {
      logContext.put("pipelineIdentifier", ambiance.getMetadata().getPipelineIdentifier());
    }
    return logContext;
  }

  public static String getAccountId(Ambiance ambiance) {
    return ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId);
  }

  public static String getProjectIdentifier(Ambiance ambiance) {
    return ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier);
  }

  public static String getOrgIdentifier(Ambiance ambiance) {
    return ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier);
  }

  public static StepType getCurrentStepType(Ambiance ambiance) {
    Level level = obtainCurrentLevel(ambiance);
    return level == null ? null : level.getStepType();
  }

  public static StepType getParentStepType(Ambiance ambiance) {
    Level level = obtainParentLevel(ambiance);
    return level == null ? null : level.getStepType();
  }

  public static String getCurrentGroup(Ambiance ambiance) {
    Level level = obtainCurrentLevel(ambiance);
    return level == null ? null : level.getGroup();
  }

  public static long getCurrentLevelStartTs(Ambiance ambiance) {
    Level currLevel = obtainCurrentLevel(ambiance);
    if (currLevel == null) {
      throw new InvalidRequestException("Ambiance.levels is empty");
    }
    return currLevel.getStartTs();
  }

  public NGAccess getNgAccess(Ambiance ambiance) {
    return BaseNGAccess.builder()
        .accountIdentifier(getAccountId(ambiance))
        .orgIdentifier(getOrgIdentifier(ambiance))
        .projectIdentifier(getProjectIdentifier(ambiance))
        .build();
  }

  public Optional<Level> getStageLevelFromAmbiance(Ambiance ambiance) {
    Optional<Level> stageLevel = Optional.empty();

    // @Todo(SahilHindwani): Correct StepCategory for IdentityNodes. Currently they always have STEP as StepCategory.
    for (Level level : ambiance.getLevelsList()) {
      if (level.getStepType().getStepCategory() == StepCategory.STAGE || Objects.equals(level.getGroup(), STAGE)) {
        stageLevel = Optional.of(level);
      }
    }
    return stageLevel;
  }

  public String getStageRuntimeIdAmbiance(Ambiance ambiance) {
    Optional<Level> stageLevel = getStageLevelFromAmbiance(ambiance);
    if (stageLevel.isPresent()) {
      return stageLevel.get().getRuntimeId();
    }
    throw new InvalidRequestException("Stage not present");
  }

  public Optional<Level> getStrategyLevelFromAmbiance(Ambiance ambiance) {
    Optional<Level> stageLevel = Optional.empty();
    for (Level level : ambiance.getLevelsList()) {
      if (level.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        stageLevel = Optional.of(level);
      }
    }
    return stageLevel;
  }

  public Optional<Level> getStepGroupLevelFromAmbiance(Ambiance ambiance) {
    Optional<Level> stageLevel = Optional.empty();
    for (Level level : ambiance.getLevelsList()) {
      if (level.getStepType().getType().equals("STEP_GROUP")) {
        stageLevel = Optional.of(level);
      }
    }
    return stageLevel;
  }

  public Optional<Level> getNearestStepGroupLevelWithStrategyFromAmbiance(Ambiance ambiance) {
    for (int index = ambiance.getLevelsCount() - 1; index > 0; index--) {
      Level level = ambiance.getLevelsList().get(index);
      Level nextLevel = ambiance.getLevelsList().get(index - 1);
      if (level.getStepType().getType().equals("STEP_GROUP")
          && nextLevel.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        return Optional.of(level);
      }
    }
    return Optional.empty();
  }

  public static boolean isRetry(Ambiance ambiance) {
    Level level = Objects.requireNonNull(obtainCurrentLevel(ambiance));
    return level.getRetryIndex() != 0;
  }

  public static String obtainParentRuntimeId(Ambiance ambiance) {
    if (ambiance.getLevelsCount() < 2) {
      return null;
    }
    return ambiance.getLevels(ambiance.getLevelsCount() - 2).getRuntimeId();
  }

  public static String modifyIdentifier(StrategyMetadata metadata, String identifier, Ambiance ambiance) {
    return modifyIdentifier(metadata, identifier, shouldUseMatrixFieldName(ambiance));
  }

  public static String modifyIdentifier(
      StrategyMetadata strategyMetadata, String identifier, boolean useMatrixFieldName) {
    return identifier.replaceAll(StrategyValidationUtils.STRATEGY_IDENTIFIER_POSTFIX_ESCAPED,
        getStrategyPostFixUsingMetadata(strategyMetadata, useMatrixFieldName));
  }

  // Todo: Use metadata.getIdentifierPostfix going forward.
  public static String getStrategyPostFixUsingMetadata(StrategyMetadata metadata, boolean useMatrixFieldName) {
    if (!metadata.hasMatrixMetadata()) {
      if (metadata.getTotalIterations() <= 0) {
        return StringUtils.EMPTY;
      }
      return "_" + metadata.getCurrentIteration();
    }
    if (metadata.getMatrixMetadata().getMatrixCombinationList().isEmpty()) {
      if (metadata.getTotalIterations() <= 0) {
        return StringUtils.EMPTY;
      }
      return "_" + metadata.getCurrentIteration();
    }

    // User given nodeName while defining the Matrix
    String nodeName = metadata.getMatrixMetadata().getNodeName();

    return getLevelIdentifierUsingMetadata(metadata, nodeName, useMatrixFieldName);
  }

  private String getLevelIdentifierUsingMetadata(
      StrategyMetadata strategyMetadata, String nodeName, boolean useMatrixFieldName) {
    String levelIdentifier;

    if (EmptyPredicate.isNotEmpty(nodeName)) {
      levelIdentifier = nodeName;
    } else if (useMatrixFieldName) {
      List<String> matrixKeysToSkipInName = strategyMetadata.getMatrixMetadata().getMatrixKeysToSkipInNameList();

      levelIdentifier = strategyMetadata.getMatrixMetadata()
                            .getMatrixValuesMap()
                            .entrySet()
                            .stream()
                            .filter(entry
                                -> !matrixKeysToSkipInName.contains(entry.getKey())
                                    && !MATRIX_IDENTIFIER_POSTFIX_FOR_DUPLICATES.equals(entry.getKey()))
                            .sorted(Map.Entry.comparingByKey())
                            .map(t -> t.getValue().replace(".", ""))
                            .collect(Collectors.joining("_"));
    } else {
      levelIdentifier = strategyMetadata.getMatrixMetadata()
                            .getMatrixCombinationList()
                            .stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining("_"));
    }

    // Making sure that identifier postfix is added at the last while forming the identifier for the matrix stage
    if (strategyMetadata.getMatrixMetadata().getMatrixValuesMap().containsKey(
            MATRIX_IDENTIFIER_POSTFIX_FOR_DUPLICATES)) {
      levelIdentifier = levelIdentifier + "_"
          + strategyMetadata.getMatrixMetadata().getMatrixValuesMap().get(MATRIX_IDENTIFIER_POSTFIX_FOR_DUPLICATES);
    }

    String modifiedString =
        "_" + (levelIdentifier.length() <= 126 ? levelIdentifier : levelIdentifier.substring(0, 126));

    return modifiedString.replaceAll(SPECIAL_CHARACTER_REGEX, "_");
  }

  public boolean isCurrentStrategyLevelAtStage(Ambiance ambiance) {
    int levelsCount = ambiance.getLevelsCount();
    // Parent of current level is stages.
    if (levelsCount >= 2 && ambiance.getLevels(levelsCount - 2).getGroup().equals("STAGES")) {
      return true;
    }
    // Parent is Parallel and Its parent of parent is STAGES.
    return levelsCount >= 3 && ambiance.getLevels(levelsCount - 2).getStepType().getStepCategory() == StepCategory.FORK
        && ambiance.getLevels(levelsCount - 3).getGroup().equals("STAGES");
  }

  public boolean isCurrentNodeUnderStageStrategy(Ambiance ambiance) {
    Optional<Level> stageLevel = getStageLevelFromAmbiance(ambiance);
    return stageLevel.isPresent() && hasStrategyMetadata(stageLevel.get());
  }

  public boolean isCurrentLevelAtStep(Ambiance ambiance) {
    StepType currentStepType = getCurrentStepType(ambiance);
    if (currentStepType == null) {
      return false;
    }
    return currentStepType.getStepCategory() == StepCategory.STEP;
  }

  public boolean isCurrentLevelInsideStage(Ambiance ambiance) {
    Optional<Level> stageLevel = getStageLevelFromAmbiance(ambiance);
    return stageLevel.isPresent();
  }

  public String getEmail(Ambiance ambiance) {
    TriggeredBy triggeredBy = ambiance.getMetadata().getTriggerInfo().getTriggeredBy();
    return triggeredBy.getExtraInfoOrDefault("email", null);
  }

  public static String getPipelineVersion(Ambiance ambiance) {
    ExecutionMetadata metadata = ambiance.getMetadata();
    if (EmptyPredicate.isEmpty(metadata.getHarnessVersion())) {
      return HarnessYamlVersion.V0;
    }
    return metadata.getHarnessVersion();
  }

  public String getPipelineIdentifier(Ambiance ambiance) {
    if (ambiance.getMetadata() != null) {
      return ambiance.getMetadata().getPipelineIdentifier();
    }
    return null;
  }

  public String getTriggerIdentifier(Ambiance ambiance) {
    return ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier();
  }

  public TriggerType getTriggerType(Ambiance ambiance) {
    return ambiance.getMetadata().getTriggerInfo().getTriggerType();
  }

  public TriggeredBy getTriggerBy(Ambiance ambiance) {
    return ambiance.getMetadata().getTriggerInfo().getTriggeredBy();
  }

  public String getPipelineExecutionIdentifier(Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(ambiance.getPlanExecutionId())) {
      return ambiance.getPlanExecutionId();
    } else if (ambiance.getMetadata() != null) {
      return ambiance.getMetadata().getExecutionUuid();
    }
    return null;
  }

  public static boolean isCurrentLevelChildOfStep(Ambiance ambiance, String stepName) {
    if (isEmpty(ambiance.getLevelsList()) || ambiance.getLevelsCount() == 1) {
      return false;
    }
    List<Level> levels = ambiance.getLevelsList();

    int currentLevelIdx = levels.size() - 1;
    for (int i = 0; i < currentLevelIdx; i++) {
      Level level = levels.get(i);
      if (level.hasStepType() && Objects.equals(stepName, level.getStepType().getType())) {
        return true;
      }
    }

    return false;
  }

  public static AutoLogContext autoLogContext(Ambiance ambiance, SdkResponseEventType sdkResponseEventType) {
    Map<String, String> logContextMap = logContextMap(ambiance);
    logContextMap.put("sdkEventType", sdkResponseEventType.toString());
    return new AutoLogContext(logContextMap, OVERRIDE_NESTS);
  }

  public String getFQNUsingLevels(@NotNull List<Level> levels) {
    List<String> fqnList = new ArrayList<>();
    for (Level level : levels) {
      // Strategy level also handled. Strategy identifier will not come is skipExpressionChain will be true.
      if (YamlUtils.shouldIncludeInQualifiedName(
              level.getIdentifier(), level.getSetupId(), level.getSkipExpressionChain())) {
        fqnList.add(level.getIdentifier());
      }
    }
    return String.join(".", fqnList);
  }

  /**
   * This method is used to find the combined index of the given node.
   * For example: if a strategy is defined at stage, stepGroup and then step level.
   * This would return a string which would be a concat of current iteration of stage, step group and step level.
   * @param levels
   * @return
   */
  public String getCombinedIndexes(@NotNull List<Level> levels) {
    List<String> fqnList = new ArrayList<>();
    List<Level> levelsWithStrategy = levels.stream().filter(Level::hasStrategyMetadata).collect(Collectors.toList());
    return levelsWithStrategy.stream()
        .map(level -> String.valueOf(AmbianceUtils.getCurrentIteration(level)))
        .collect(Collectors.joining("."));
  }

  public boolean isRollbackModeExecution(Ambiance ambiance) {
    ExecutionMode executionMode = ambiance.getMetadata().getExecutionMode();
    return executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK || executionMode == ExecutionMode.PIPELINE_ROLLBACK;
  }

  public boolean isUnderRollbackSteps(Ambiance ambiance) {
    List<Level> levels = ambiance.getLevelsList();
    for (Level level : levels) {
      String identifier = level.getIdentifier();
      if (identifier.equals(YAMLFieldNameConstants.ROLLBACK_STEPS)) {
        return true;
      }
    }
    return false;
  }

  public String getStageExecutionIdForExecutionMode(Ambiance ambiance) {
    if (isRollbackModeExecution(ambiance)) {
      return ambiance.getOriginalStageExecutionIdForRollbackMode();
    }
    return ambiance.getStageExecutionId();
  }

  public String getPlanExecutionIdForExecutionMode(Ambiance ambiance) {
    if (isRollbackModeExecution(ambiance)) {
      return ambiance.getMetadata().getOriginalPlanExecutionIdForRollbackMode();
    }
    return ambiance.getPlanExecutionId();
  }

  public boolean shouldUseMatrixFieldName(Ambiance ambiance) {
    return checkIfSettingEnabled(ambiance, NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName());
  }

  public boolean isNodeExecutionAuditsEnabled(Ambiance ambiance) {
    return checkIfSettingEnabled(ambiance, NGPipelineSettingsConstant.ENABLE_NODE_EXECUTION_AUDIT_EVENTS.getName());
  }

  public boolean shouldUseExpressionEngineV2(Ambiance ambiance) {
    return checkIfSettingEnabled(ambiance, NGPipelineSettingsConstant.ENABLE_EXPRESSION_ENGINE_V2.getName());
  }

  // This method should be used when the setting value is of type boolean.
  public boolean checkIfSettingEnabled(Ambiance ambiance, String settingId) {
    Map<String, String> settingToValueMap = ambiance.getMetadata().getSettingToValueMapMap();
    return settingToValueMap.containsKey(settingId) && "true".equals(settingToValueMap.get(settingId));
  }

  // This method should be used when the setting value is of type String.
  public String getSettingValue(Ambiance ambiance, String settingId) {
    Map<String, String> settingToValueMap = ambiance.getMetadata().getSettingToValueMapMap();
    return settingToValueMap.get(settingId);
  }

  public List<String> getEnabledFeatureFlags(Ambiance ambiance) {
    List<String> enabledFeatureFlags = new ArrayList<>();
    if (ambiance.getMetadata() != null && ambiance.getMetadata().getFeatureFlagToValueMapMap() != null) {
      Map<String, Boolean> stringMap = ambiance.getMetadata().getFeatureFlagToValueMapMap();
      for (Map.Entry<String, Boolean> entry : stringMap.entrySet()) {
        if (entry.getValue()) {
          enabledFeatureFlags.add(entry.getKey());
        }
      }
    }
    return enabledFeatureFlags;
  }

  public boolean shouldSimplifyLogBaseKey(Ambiance ambiance) {
    return ambiance.getMetadata() != null && ambiance.getMetadata().getFeatureFlagToValueMapMap() != null
        && ambiance.getMetadata().getFeatureFlagToValueMapMap().getOrDefault(PIE_SIMPLIFY_LOG_BASE_KEY, false);
  }

  public boolean shouldEnableSecretsObserver(Ambiance ambiance) {
    return ambiance.getMetadata() != null && ambiance.getMetadata().getFeatureFlagToValueMapMap() != null
        && ambiance.getMetadata().getFeatureFlagToValueMapMap().getOrDefault(PIE_SECRETS_OBSERVER, false);
  }

  public boolean hasStrategyMetadata(Level level) {
    return level.hasStrategyMetadata();
  }

  public int getCurrentIteration(Level level) {
    return level.getStrategyMetadata().getCurrentIteration();
  }

  public int getTotalIteration(Level level) {
    return level.getStrategyMetadata().getTotalIterations();
  }

  public void enabledJsonSupportFeatureFlag(Ambiance ambiance, Map<String, String> contextMap) {
    List<String> enabledJsonSupportFeatureFlag = new ArrayList<>();
    if (AmbianceUtils.shouldUseExpressionEngineV2(ambiance)) {
      enabledJsonSupportFeatureFlag.add(EngineExpressionEvaluator.PIE_EXECUTION_JSON_SUPPORT);
    }
    if (isNotEmpty(enabledJsonSupportFeatureFlag)) {
      contextMap.put(
          EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY, String.join(",", enabledJsonSupportFeatureFlag));
    }
  }

  public boolean checkIfFeatureFlagEnabled(Ambiance ambiance, String featureFlagName) {
    if (ambiance.getMetadata() != null && ambiance.getMetadata().getFeatureFlagToValueMapMap() != null) {
      Map<String, Boolean> stringMap = ambiance.getMetadata().getFeatureFlagToValueMapMap();
      return stringMap.containsKey(featureFlagName) && Boolean.TRUE.equals(stringMap.get(featureFlagName));
    }
    return false;
  }
}
