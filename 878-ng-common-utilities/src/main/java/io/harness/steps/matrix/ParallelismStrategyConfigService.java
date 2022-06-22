package io.harness.steps.matrix;

import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.StrategyMetadata;

import java.util.ArrayList;
import java.util.List;

public class ParallelismStrategyConfigService implements StrategyConfigService {
  @Override
  public List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfig strategyConfig, String childNodeId) {
    Integer parallelism = strategyConfig.getParallelism();
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    for (int i = 0; i < parallelism; i++) {
      children.add(ChildrenExecutableResponse.Child.newBuilder()
                       .setChildNodeId(childNodeId)
                       .setStrategyMetadata(
                           StrategyMetadata.newBuilder().setCurrentIteration(i).setTotalIterations(parallelism).build())
                       .build());
    }
    return children;
  }
}
