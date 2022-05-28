package io.harness.steps.matrix;

import io.harness.plancreator.strategy.HarnessForConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.StrategyMetadata;

import java.util.ArrayList;
import java.util.List;

public class ForLoopStrategyConfigService implements StrategyConfigService {
  @Override
  public List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfig strategyConfig, String childNodeId) {
    HarnessForConfig harnessForConfig = strategyConfig.getForConfig();
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    for (int i = 0; i < harnessForConfig.getIteration(); i++) {
      children.add(ChildrenExecutableResponse.Child.newBuilder()
                       .setChildNodeId(childNodeId)
                       .setStrategyMetadata(StrategyMetadata.newBuilder()
                                                .setCurrentIteration(i)
                                                .setTotalIterations(harnessForConfig.getIteration())
                                                .getDefaultInstanceForType())
                       .build());
    }
    return children;
  }
}
