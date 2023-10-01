/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.rule;

import io.harness.ssca.enforcement.ExecutorRegistry;
import io.harness.ssca.enforcement.constants.RuleExecutorType;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementResultEntity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Engine<T> {
  ExecutorRegistry executorRegistry;
  RuleExecutorType executorType;
  T rules;
  ArtifactEntity artifact;
  String enforcementId;

  public List<EnforcementResultEntity> executeRules() {
    return executorRegistry.getExecutor(executorType, rules.getClass()).get().execute(this);
  }
}
