/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.strategy.StrategyValidationUtils;

import com.cronutils.utils.StringUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(PIPELINE)
public class AmbianceUtils {
  public static Ambiance cloneForFinish(@NonNull Ambiance ambiance) {
    return clone(ambiance, ambiance.getLevelsList().size() - 1);
  }

  public static Ambiance cloneForFinish(@NonNull Ambiance ambiance, Level level) {
    Ambiance.Builder builder = cloneBuilder(ambiance, ambiance.getLevelsList().size() - 1);
    if (level.getStepType().getStepCategory() == StepCategory.STAGE) {
      builder.setStageExecutionId(level.getRuntimeId());
    }
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
    if (level.getStepType().getStepCategory() == StepCategory.STAGE) {
      builder.setStageExecutionId(level.getRuntimeId());
    }
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
    for (Level level : ambiance.getLevelsList()) {
      if (level.getStepType().getStepCategory() == StepCategory.STAGE) {
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

  public static String modifyIdentifier(Ambiance ambiance, String identifier) {
    Level level = obtainCurrentLevel(ambiance);
    return modifyIdentifier(level, identifier);
  }

  public static String modifyIdentifier(Level level, String identifier) {
    return identifier.replaceAll(
        StrategyValidationUtils.STRATEGY_IDENTIFIER_POSTFIX_ESCAPED, getStrategyPostfix(level));
  }

  public static String getStrategyPostfix(Level level) {
    if (level == null || !level.hasStrategyMetadata()) {
      return StringUtils.EMPTY;
    }
    if (!level.getStrategyMetadata().hasMatrixMetadata()) {
      if (level.getStrategyMetadata().getTotalIterations() <= 0) {
        return StringUtils.EMPTY;
      }
      return "_" + level.getStrategyMetadata().getCurrentIteration();
    }
    if (level.getStrategyMetadata().getMatrixMetadata().getMatrixCombinationList().isEmpty()) {
      if (level.getStrategyMetadata().getTotalIterations() <= 0) {
        return StringUtils.EMPTY;
      }
      return "_" + level.getStrategyMetadata().getCurrentIteration();
    }
    return "_"
        + level.getStrategyMetadata()
              .getMatrixMetadata()
              .getMatrixCombinationList()
              .stream()
              .map(String::valueOf)
              .collect(Collectors.joining("_"));
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
    return stageLevel.isPresent() && stageLevel.get().hasStrategyMetadata();
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
      return PipelineVersion.V0;
    }
    return metadata.getHarnessVersion();
  }

  public String getPipelineIdentifier(Ambiance ambiance) {
    if (ambiance.getMetadata() != null) {
      return ambiance.getMetadata().getPipelineIdentifier();
    }
    return null;
  }
}
