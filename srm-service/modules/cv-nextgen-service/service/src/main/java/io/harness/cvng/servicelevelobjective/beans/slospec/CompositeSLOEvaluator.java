/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slospec;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.util.Pair;

public abstract class CompositeSLOEvaluator {
  public abstract Pair<Double, Double> evaluate(
      List<Double> weightage, List<Integer> goodSliValues, List<Integer> badSliValues);

  public abstract Double getSloPercentage(List<Double> weightage, List<Double> sloPercentageList);

  public abstract Double getSloErrorBudgetBurnDown(List<Double> weightage, List<Double> errorBudgetBurned);

  List<Double> getSLOValuesOfIndividualSLIs(List<Double> weightage, List<Integer> sliValues) {
    List<Double> sliWithWeightage = new ArrayList<>();
    if (weightage.size() == sliValues.size()) {
      for (int i = 0; i < weightage.size(); i++) {
        sliWithWeightage.add((weightage.get(i) * sliValues.get(i)) / 100.0);
      }
    }
    return sliWithWeightage;
  }
}
