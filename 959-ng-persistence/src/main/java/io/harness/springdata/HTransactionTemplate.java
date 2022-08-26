/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import java.util.function.Consumer;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class HTransactionTemplate extends TransactionTemplate {
  boolean transactionsEnabled;

  public HTransactionTemplate(MongoTransactionManager mongoTransactionManager, boolean transactionsEnabled) {
    super(mongoTransactionManager);
    this.transactionsEnabled = transactionsEnabled;
  }

  @Override
  public <T> T execute(TransactionCallback<T> action) throws TransactionException {
    if (!transactionsEnabled) {
      return action.doInTransaction(new SimpleTransactionStatus(false));
    }
    return super.execute(action);
  }

  @Override
  public void executeWithoutResult(Consumer<TransactionStatus> action) throws TransactionException {
    if (!transactionsEnabled) {
      action.accept(new SimpleTransactionStatus(false));
    } else {
      super.executeWithoutResult(action);
    }
  }
}
