/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class TransactionHelper {
  @Inject private TransactionTemplate transactionTemplate;

  public <T> T performTransaction(TransactionFunction<T> transactionFunction) {
    return Failsafe.with(PersistenceUtils.DEFAULT_RETRY_POLICY)
        .get(() -> transactionTemplate.execute(t -> transactionFunction.execute()));
  }

  public interface TransactionFunction<R> {
    R execute();
  }
}
