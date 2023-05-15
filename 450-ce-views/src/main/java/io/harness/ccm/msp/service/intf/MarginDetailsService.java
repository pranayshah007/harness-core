/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.msp.service.intf;

import io.harness.ccm.msp.entities.ManagedAccountDetails;
import io.harness.ccm.msp.entities.MarginDetails;

import java.util.List;

public interface MarginDetailsService {
  String save(MarginDetails marginDetails);
  String addManagedAccount(String mspAccountId, String managedAccountId, String managedAccountName);
  MarginDetails update(MarginDetails marginDetails);
  MarginDetails unsetMargins(String uuid, String accountId);
  MarginDetails get(String uuid);
  MarginDetails get(String mspAccountId, String managedAccountId);
  List<MarginDetails> list(String mspAccountId);
  List<ManagedAccountDetails> listManagedAccountDetails(String mspAccountId);
}
