/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@UtilityClass
public class ResolverUtils {
  public final String GLOBAL_GROUP_SCOPE = "__GLOBAL_GROUP_SCOPE__";

  public String prepareLevelRuntimeIdIdx(List<Level> levels) {
    return EmptyPredicate.isEmpty(levels) ? ""
                                          : levels.stream().map(Level::getRuntimeId).collect(Collectors.joining("|"));
  }

  public List<String> prepareLevelRuntimeIdIndices(@NotNull Ambiance ambiance) {
    if (EmptyPredicate.isEmpty(ambiance.getLevelsList())) {
      // If the ambiance has no levels, the instance also shouldn't have any levels.
      return Collections.singletonList("");
    }

    List<String> levelRuntimeIdIndices = new ArrayList<>();
    levelRuntimeIdIndices.add("");
    for (int i = 1; i <= ambiance.getLevelsList().size(); i++) {
      levelRuntimeIdIndices.add(prepareLevelRuntimeIdIdx(ambiance.getLevelsList().subList(0, i)));
    }
    return levelRuntimeIdIndices;
  }

  public List<String> prepareLevelRuntimeIdIndicesUsingGroupName(
      @NotNull Ambiance ambiance, @NotNull String groupName) {
    if (EmptyPredicate.isEmpty(groupName) || EmptyPredicate.isEmpty(ambiance.getLevelsList())) {
      // If group name is not provided, calculate levelRuntimeId without any restrictions
      return prepareLevelRuntimeIdIndices(ambiance);
    }

    if (ResolverUtils.GLOBAL_GROUP_SCOPE.equals(groupName)) {
      // in consume flow, global scope is mapped to empty runtimeIdIdx
      return Collections.singletonList("");
    }
    List<String> levelRuntimeIdIndices = new ArrayList<>();
    levelRuntimeIdIndices.add("");
    List<Level> levels = ambiance.getLevelsList();
    for (int i = levels.size() - 1; i >= 0; i--) {
      Level level = levels.get(i);
      if (groupName.equals(level.getGroup())) {
        levelRuntimeIdIndices.add(prepareLevelRuntimeIdIdx(ambiance.getLevelsList().subList(0, i + 1)));
      }
    }
    return levelRuntimeIdIndices;
  }
}
