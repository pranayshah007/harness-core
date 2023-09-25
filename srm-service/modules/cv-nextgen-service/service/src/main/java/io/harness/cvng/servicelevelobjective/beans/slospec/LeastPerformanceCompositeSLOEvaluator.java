/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slospec;

import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.apache.commons.math3.util.Pair;

public class LeastPerformanceCompositeSLOEvaluator extends CompositeSLOEvaluator {
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Override
  public Pair<Double, Double> evaluate(
      List<Double> weightage, List<Integer> goodSliValues, List<Integer> badSliValues) {
    if (badSliValues.contains(-1)) {
      return Pair.create(0.0, 0.0);
    }
    List<Double> badSliWithWeightage = getSLOValuesOfIndividualSLIs(weightage, badSliValues);
    double runningBadCount = Collections.max(badSliWithWeightage);
    return Pair.create(1.0 - runningBadCount, runningBadCount);
  }

  public Double getSloPercentage(List<Double> weightage, List<Double> sloPercentageList) {
    double sloPercentage = 100.0;
    double sloPercent = 0.0;
    for (int i = 0; i < weightage.size(); i++) {
      double temp = sloPercentageList.get(i) * (1 - weightage.get(i));
      if (temp < sloPercentage) {
        sloPercentage = temp;
        sloPercent = sloPercentageList.get(i);
      }
    }
    return sloPercent;
  }

  public Double getSloErrorBudgetBurnDown(List<Double> weightage, List<Double> errorBudgetBurned) {
    double sloErrorBudgetBurnDown = 100.0;
    double sloErrorBudget = 0.0;
    for (int i = 0; i < weightage.size(); i++) {
      sloErrorBudgetBurnDown = Math.min(weightage.get(i) * errorBudgetBurned.get(i), sloErrorBudgetBurnDown);
      double temp = errorBudgetBurned.get(i) * (1 - weightage.get(i));
      if (temp < sloErrorBudgetBurnDown) {
        sloErrorBudgetBurnDown = temp;
        sloErrorBudget = errorBudgetBurned.get(i);
      }
    }
    return sloErrorBudget;
  }
}
