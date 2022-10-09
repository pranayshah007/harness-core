/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OwnedBy(HarnessTeam.DEL)
public class SecretManagerModule extends AbstractModule {
  public static final String EXPRESSION_EVALUATOR_EXECUTOR = "expressionEvaluatorExecutor";

  @Override
  protected void configure() {}

  @Provides
  @Singleton
  @Named(EXPRESSION_EVALUATOR_EXECUTOR)
  public ExecutorService expressionEvaluatorExecutor() {
    return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
        new ThreadFactoryBuilder().setNameFormat("expression-evaluator-%d").build());
  }
}
