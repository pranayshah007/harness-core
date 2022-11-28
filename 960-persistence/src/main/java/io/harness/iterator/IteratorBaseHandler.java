package io.harness.iterator;

import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.FilterExpander;

import java.time.Duration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class IteratorBaseHandler<T extends PersistentIterable, F extends FilterExpander> {
  @Getter protected String iteratorName;
  @Getter protected MongoPersistenceIterator<T, F> iterator;

  /**
   * This method returns true if executor service is terminated and vice versa.
   * @return true / false
   */
  public abstract boolean isExecutorTerminated();

  /**
   * This method will shut down the executor service gracefully.
   */
  protected abstract void stopExecutor();

  /**
   * This method is to register the iterator with the IteratorConfigHandler.
   *
   * @param iteratorExecutionHandler the handler for iterator configurations
   */
  protected abstract void registerIterator(IteratorExecutionHandler iteratorExecutionHandler);

  /**
   * This method is to create and start the iterator.
   *
   * @param executorOptions provides the executor thread-pool options
   * @param targetInterval the targetInterval for iteration
   */
  protected abstract void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval);
}
