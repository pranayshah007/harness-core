/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class PolicySetServiceImpl implements PolicySetService {
  @Inject private PolicySetDAO policySetDAO;

  @Override

  public boolean save(PolicySet policySet) {
    return policySetDAO.save(policySet);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return policySetDAO.delete(accountId, uuid);
  }

  @Override
  public PolicySet update(PolicySet policySet) {
    { return policySetDAO.update(policySet); }
  }
}
