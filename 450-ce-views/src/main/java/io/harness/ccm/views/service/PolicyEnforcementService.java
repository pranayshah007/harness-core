/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import io.harness.ccm.views.dto.EnforcementCountDTO;
import io.harness.ccm.views.entities.EnforcementCount;
import io.harness.ccm.views.entities.EnforcementCountRequest;
import io.harness.ccm.views.entities.PolicyEnforcement;

import java.util.List;

public interface PolicyEnforcementService {
  PolicyEnforcement get(String uuid);
  boolean save(PolicyEnforcement policyEnforcement);
  boolean delete(String accountId, String uuid);
  PolicyEnforcement update(PolicyEnforcement policyEnforcement);
  PolicyEnforcement listName(String accountId, String name, boolean create);
  PolicyEnforcement listId(String accountId, String uuid, boolean create);
  List<PolicyEnforcement> list(String accountId);
  EnforcementCount getCount(String accountId, EnforcementCountRequest enforcementCountRequest );
}
