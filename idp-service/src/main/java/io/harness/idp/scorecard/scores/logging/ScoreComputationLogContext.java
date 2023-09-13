/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.logging;

import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.logging.AutoLogContext;

import com.google.common.collect.ImmutableMap;

public class ScoreComputationLogContext extends AutoLogContext {
  public static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  public static final String SCORECARD_IDENTIFIER = "scorecardIdentifier";
  public static final String THREAD = "thread";
  public ScoreComputationLogContext(ImmutableMap<String, String> context, OverrideBehavior behavior) {
    super(context, behavior);
  }

  public static ScoreComputationLogContext.Builder builder() {
    return new ScoreComputationLogContext.Builder();
  }

  public static class Builder {
    private final NullSafeImmutableMap.NullSafeBuilder<String, String> nullSafeBuilder = NullSafeImmutableMap.builder();

    public ScoreComputationLogContext.Builder accountIdentifier(String accountIdentifier) {
      nullSafeBuilder.putIfNotNull(ACCOUNT_IDENTIFIER, accountIdentifier);
      return this;
    }

    public ScoreComputationLogContext.Builder threadName(String threadName) {
      nullSafeBuilder.putIfNotNull(THREAD, threadName);
      return this;
    }
    public ScoreComputationLogContext.Builder scorecardIdentifier(String scorecardIdentifier) {
      nullSafeBuilder.putIfNotNull(SCORECARD_IDENTIFIER, scorecardIdentifier);
      return this;
    }

    public ScoreComputationLogContext build(OverrideBehavior behavior) {
      return new ScoreComputationLogContext(nullSafeBuilder.build(), behavior);
    }
  }
}
