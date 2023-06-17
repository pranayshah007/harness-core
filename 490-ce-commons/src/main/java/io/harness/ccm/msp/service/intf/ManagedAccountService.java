/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.msp.service.intf;

import io.harness.ccm.msp.dto.ManagedAccount;

import java.util.List;

public interface ManagedAccountService {
  String save(String mspAccountId, String managedAccountId);
  ManagedAccount get(String mspAccountId, String managedAccountId);
  List<ManagedAccount> list(String mspAccountId);
  ManagedAccount update(ManagedAccount managedAccount);
  boolean delete(String accountId);
}
