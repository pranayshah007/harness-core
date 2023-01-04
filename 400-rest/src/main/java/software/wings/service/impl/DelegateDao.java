/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;

public class DelegateDao {
  @Inject private HPersistence persistence;

  public Delegate get(String delegateId) {
    Query<Delegate> query = persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegateId);
    return query.get();
  }
}
