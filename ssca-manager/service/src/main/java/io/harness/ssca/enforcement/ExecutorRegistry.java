/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.beans.AllowList;
import io.harness.ssca.beans.DenyList;
import io.harness.ssca.enforcement.constants.RuleExecutorType;
import io.harness.ssca.enforcement.executors.mongo.MongoAllowListExecutor;
import io.harness.ssca.enforcement.executors.mongo.MongoDenyListExecutor;
import io.harness.ssca.enforcement.rule.IRuleExecutor;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@OwnedBy(HarnessTeam.SSCA)
@Singleton
public class ExecutorRegistry {
  @Inject private Injector injector;

  private final Map<RuleExecutorType, Class<? extends IRuleExecutor<AllowList>>> registeredAllowListExecutors =
      new HashMap<>();
  private final Map<RuleExecutorType, Class<? extends IRuleExecutor<DenyList>>> registeredDenyListExecutors =
      new HashMap<>();

  public ExecutorRegistry() {
    registeredAllowListExecutors.put(RuleExecutorType.MONGO_EXECUTOR, MongoAllowListExecutor.class);

    registeredDenyListExecutors.put(RuleExecutorType.MONGO_EXECUTOR, MongoDenyListExecutor.class);
  }

  public Optional<IRuleExecutor> getExecutor(RuleExecutorType type, Class classType) {
    if (classType == AllowList.class) {
      if (!registeredAllowListExecutors.containsKey(type)) {
        return Optional.empty();
      }
      return Optional.of(injector.getInstance(registeredAllowListExecutors.get(type)));
    }
    if (classType == DenyList.class) {
      if (!registeredDenyListExecutors.containsKey(type)) {
        return Optional.empty();
      }
      return Optional.of(injector.getInstance(registeredDenyListExecutors.get(type)));
    }
    return Optional.empty();
  }
}
