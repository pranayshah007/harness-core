/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGInstanceUnitType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.steps.matrix.StrategyParameters;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class StrategyValidator {
  public static void validateStrategyParametersForCanary(StrategyParameters strategyParameters) {
    if (null == strategyParameters.getPhases() || isEmpty(strategyParameters.getPhases())) {
      throw new InvalidArgumentsException("phases need to be defined, e.g. phases : [10, 50, 100]");
    }
    List<Integer> sortedPhases = Arrays.stream(strategyParameters.getPhases()).sorted().collect(Collectors.toList());
    if (sortedPhases.get(0) <= 0) {
      throw new InvalidArgumentsException("phases need to be positive");
    }
    if (!sortedPhases.equals(Arrays.asList(strategyParameters.getPhases()))) {
      throw new InvalidArgumentsException("phases need to be in asc order");
    }
    if (sortedPhases.stream().filter(i -> i > 100).findAny().isPresent()
        && NGInstanceUnitType.PERCENTAGE.equals(strategyParameters.getUnitType())) {
      throw new InvalidArgumentsException("phase can not be greater than 100");
    }
    if (sortedPhases.stream().distinct().count() != sortedPhases.size()) {
      throw new InvalidArgumentsException("phase values should be unique");
    }
    if (null == strategyParameters.getUnitType()) {
      throw new InvalidArgumentsException("unitType needs to be defined, one of <COUNT | PERCENTAGE>");
    }
    if (null == strategyParameters.getArtifactType()) {
      throw new InvalidArgumentsException("artifactType needs to be defined, e.g. WAR");
    }
  }

  public static void validateStrategyParametersForBasic(StrategyParameters strategyParameters) {
    if (null == strategyParameters.getArtifactType()) {
      throw new InvalidArgumentsException("artifactType needs to be defined, e.g. WAR");
    }
  }

  public static void validateStrategyParametersForRolling(StrategyParameters strategyParameters) {
    if (null == strategyParameters.getInstances()) {
      throw new InvalidArgumentsException("Number of instances needs to be defined, e.g. 10");
    }
    if (strategyParameters.getInstances() <= 0) {
      throw new InvalidArgumentsException("Number of instances need to be positive");
    }
    if (strategyParameters.getInstances() > 100
        && NGInstanceUnitType.PERCENTAGE.equals(strategyParameters.getUnitType())) {
      throw new InvalidArgumentsException("Number of instances need to be between 0 and 100");
    }
    if (null == strategyParameters.getArtifactType()) {
      throw new InvalidArgumentsException("artifactType needs to be defined, e.g. WAR");
    }
  }
}
