/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.msp.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.msp.dto.ManagedAccount;
import io.harness.ccm.msp.dto.ManagedAccount.ManagedAccountKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class ManagedAccountDao {
  @Inject private HPersistence hPersistence;

  public String save(ManagedAccount managedAccount) {
    return hPersistence.save(managedAccount);
  }

  public List<ManagedAccount> list(String mspAccountId) {
    return hPersistence.createQuery(ManagedAccount.class)
        .field(ManagedAccountKeys.mspAccountId)
        .equal(mspAccountId)
        .asList();
  }
}
