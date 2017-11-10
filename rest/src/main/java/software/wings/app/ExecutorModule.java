package software.wings.app;

import static software.wings.common.thread.ThreadPool.create;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import software.wings.utils.ManagedExecutorService;
import software.wings.utils.ManagedScheduledExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by peeyushaggarwal on 5/25/16.
 */
public class ExecutorModule extends AbstractModule {
  private ExecutorService executorService;

  /**
   * Instantiates a new executor module.
   */
  public ExecutorModule() {
    executorService = create(20, 1000, 500L, TimeUnit.MILLISECONDS);
  }

  /**
   * Instantiates a new executor module.
   *
   * @param executorService the executor service
   */
  public ExecutorModule(ExecutorService executorService) {
    this.executorService = executorService;
  }

  /* (non-Javadoc)
   * @see com.google.inject.AbstractModule#configure()
   */
  @Override
  protected void configure() {
    bind(ExecutorService.class).toInstance(new ManagedExecutorService(executorService));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("waitStateResumer"))
        .toInstance(new ManagedScheduledExecutorService(
            new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("WaitStateResumer").build())));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("timer"))
        .toInstance(new ManagedScheduledExecutorService(
            new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("Timer").build())));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("notifier"))
        .toInstance(new ManagedScheduledExecutorService(
            new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("Notifier").build())));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("notifyResponseCleaner"))
        .toInstance(new ManagedScheduledExecutorService(new ScheduledThreadPoolExecutor(
            1, new ThreadFactoryBuilder().setNameFormat("NotifyResponseCleaner").build())));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("delegateTaskNotifier"))
        .toInstance(new ManagedScheduledExecutorService(new ScheduledThreadPoolExecutor(
            1, new ThreadFactoryBuilder().setNameFormat("DelegateTaskNotifier").build())));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("gitChangeSet"))
        .toInstance(new ManagedScheduledExecutorService(
            new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("gitChangeSet").build())));
  }
}
