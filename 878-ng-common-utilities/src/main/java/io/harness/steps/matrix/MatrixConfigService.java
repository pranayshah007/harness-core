/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import io.harness.plancreator.strategy.AxisConfig;
import io.harness.plancreator.strategy.ExcludeConfig;
import io.harness.plancreator.strategy.MatrixConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Singleton
public class MatrixConfigService implements StrategyConfigService {
  public List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfig strategyConfig, String childNodeId) {
    MatrixConfig matrixConfig = strategyConfig.getMatrixConfig();
    List<Map<String, String>> combinations = new ArrayList<>();
    List<List<Integer>> matrixMetadata = new ArrayList<>();
    List<String> keys = new LinkedList<>(matrixConfig.getAxes().keySet());

    fetchCombinations(new LinkedHashMap<>(), matrixConfig.getAxes(), combinations, matrixConfig.getExclude(),
        matrixMetadata, keys, 0, new LinkedList<>());
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    int currentIteration = 0;
    int totalCount = combinations.size();
    for (Map<String, String> combination : combinations) {
      children.add(ChildrenExecutableResponse.Child.newBuilder()
                       .setChildNodeId(childNodeId)
                       .setStrategyMetadata(
                           StrategyMetadata.newBuilder()
                               .setCurrentIteration(currentIteration)
                               .setTotalIterations(totalCount)
                               .setMatrixMetadata(MatrixMetadata.newBuilder()
                                                      .addAllMatrixCombination(matrixMetadata.get(currentIteration))
                                                      .putAllMatrixValues(combination)
                                                      .build())
                               .build())
                       .build());
      currentIteration++;
    }
    return children;
  }

  /**
   *
   * This function is used to recursively calculate the number of combinations that can be there for the
   * given matrix configuration in the yaml.
   * It takes care of excluding a given combination.
   *
   * @param currentCombinationRef - Reference variable to store the current combination
   * @param axes - The axes defined in the yaml
   * @param combinationsRef - The number of total combinations that are there till now
   * @param exclude - exclude as mentioned in the yaml
   * @param matrixMetadataRef - The metadata related to the combination number of values
   * @param keys - The list of keys as mentioned in the yaml under axes
   * @param index -  the current index of the key we are on
   * @param indexPath - the path till the current iteration for the indexes like [0,2,1] i.e the matrix combination
   *     index
   */
  private void fetchCombinations(Map<String, String> currentCombinationRef, Map<String, AxisConfig> axes,
      List<Map<String, String>> combinationsRef, List<ExcludeConfig> exclude, List<List<Integer>> matrixMetadataRef,
      List<String> keys, int index, List<Integer> indexPath) {
    if (axes.size() == index) {
      if (!exclude.contains(ExcludeConfig.builder().exclude(currentCombinationRef).build())) {
        combinationsRef.add(new HashMap<>(currentCombinationRef));
        matrixMetadataRef.add(new ArrayList<>(indexPath));
      }
      return;
    }
    String key = keys.get(index);
    AxisConfig axisValues = axes.get(key);
    int i = 0;
    for (String value : axisValues.getAxisValue().getValue()) {
      currentCombinationRef.put(key, value);
      indexPath.add(i);
      fetchCombinations(
          currentCombinationRef, axes, combinationsRef, exclude, matrixMetadataRef, keys, index + 1, indexPath);
      currentCombinationRef.remove(key);
      indexPath.remove(indexPath.size() - 1);
      i++;
    }
  }
}
