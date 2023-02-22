/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.RollbackModeBehaviour;

import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class DependenciesUtils {
  public Dependencies toDependenciesProto(Map<String, YamlField> fields) {
    if (EmptyPredicate.isEmpty(fields)) {
      return Dependencies.newBuilder().build();
    }

    Dependencies.Builder builder = Dependencies.newBuilder();
    fields.forEach((k, v) -> builder.putDependencies(k, v.getYamlPath()));
    return builder.build();
  }

  public Dependencies toDependenciesProtoWithRollbackMode(
      Map<String, YamlField> fields, RollbackModeBehaviour behaviour) {
    if (EmptyPredicate.isEmpty(fields)) {
      return Dependencies.newBuilder().build();
    }

    Dependencies.Builder builder = Dependencies.newBuilder();
    fields.forEach((k, v) -> {
      builder.putDependencies(k, v.getYamlPath());
      builder.putDependencyMetadata(k, Dependency.newBuilder().setRollbackModeBehaviour(behaviour).build());
    });
    return builder.build();
  }
}
