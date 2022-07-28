package software.wings.expression;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.threading.ThreadPool;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;

@OwnedBy(HarnessTeam.DEL)
public class SecretManagerModule extends AbstractModule {
  public static final String EXPRESSION_EVALUATOR_EXECUTOR = "expressionEvaluatorExecutor";

  @Override
  protected void configure() {}

  @Provides
  @Singleton
  @Named(EXPRESSION_EVALUATOR_EXECUTOR)
  public ExecutorService expressionEvaluatorExecutor() {
    return ThreadPool.create(
        1, 100, 5, SECONDS, new ThreadFactoryBuilder().setNameFormat("expression-evaluator-%d").build());
  }
}
