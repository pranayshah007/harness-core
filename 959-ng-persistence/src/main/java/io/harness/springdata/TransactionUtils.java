/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.utils.RetryUtils;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.transaction.TransactionException;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class TransactionUtils {
  public final Duration DEFAULT_RETRY_SLEEP_DURATION = Duration.ofSeconds(1);

  public final int DEFAULT_MAXIMUM_RETRY_ATTEMPT = 3;

  public static final RetryPolicy<Object> DEFAULT_TRANSACTION_RETRY_POLICY =
      RetryUtils.getRetryPolicy("[Retrying] attempt: {}", "[Failed] attempt: {}",
          ImmutableList.of(
              TransactionException.class, MongoTransactionException.class, UncategorizedMongoDbException.class),
          DEFAULT_RETRY_SLEEP_DURATION, DEFAULT_MAXIMUM_RETRY_ATTEMPT, log);
}
