/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slospec;

import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;

import com.google.inject.Inject;
import java.util.List;
import org.apache.commons.math3.util.Pair;

public class WeightedAverageCompositeSLOEvaluator extends CompositeSLOEvaluator {
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Override
  public Pair<Double, Double> evaluate(
      List<Double> weightage, List<Integer> goodSliValues, List<Integer> badSliValues) {
    if (badSliValues.contains(-1)) {
      return Pair.create(0.0, 0.0);
    }
    List<Double> goodSliWithWeightage = getSLOValuesOfIndividualSLIs(weightage, goodSliValues);
    Double runningGoodCount = goodSliWithWeightage.stream().mapToDouble(Double::doubleValue).sum();
    List<Double> badSliWithWeightage = getSLOValuesOfIndividualSLIs(weightage, badSliValues);
    Double runningBadCount = badSliWithWeightage.stream().mapToDouble(Double::doubleValue).sum();
    return Pair.create(runningGoodCount, runningBadCount);
  }

  @Override
  public Double getSloPercentage(List<Double> weightage, List<Double> sloPercentageList) {
    double sloPercentage = 0.0;
    for (int i = 0; i < weightage.size(); i++) {
      sloPercentage += weightage.get(i) * sloPercentageList.get(i);
    }
    return sloPercentage;
  }

  @Override
  public Double getSloErrorBudgetBurnDown(
      List<Double> weightage, List<Double> errorBudgetBurned, List<Double> sloPercentageList) {
    double sloErrorBudgetBurnDown = 0.0;
    for (int i = 0; i < weightage.size(); i++) {
      sloErrorBudgetBurnDown += weightage.get(i) * errorBudgetBurned.get(i);
    }
    return sloErrorBudgetBurnDown;
  }
}
