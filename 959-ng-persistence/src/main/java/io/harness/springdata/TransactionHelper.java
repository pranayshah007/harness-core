/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import com.google.inject.Inject;
import com.mongodb.MongoException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class TransactionHelper {
  private final RetryPolicy<Object> transactionRetryPolicy =
      new RetryPolicy<>()
          .withDelay(Duration.ofSeconds(1))
          .withMaxAttempts(3)
          .onFailedAttempt(event
              -> log.info("Retrying Transaction. Attempt No. {}", event.getAttemptCount(), event.getLastFailure()))
          .onFailure(event -> log.error("Transaction Failed", event.getFailure()))
          .handle(TransactionException.class)
          .handle(MongoException.class)
          .handle(MongoTransactionException.class)
          .handle(UncategorizedMongoDbException.class);

  @Inject private TransactionTemplate transactionTemplate;

  public <T> T performTransaction(TransactionFunction<T> transactionFunction) {
    return Failsafe.with(transactionRetryPolicy)
        .get(() -> transactionTemplate.execute(t -> transactionFunction.execute()));
  }

  public <T> T performTransactionWithoutRetry(TransactionFunction<T> transactionFunction) {
    return transactionTemplate.execute(t -> transactionFunction.execute());
  }

  public interface TransactionFunction<R> {
    R execute();
  }
}
