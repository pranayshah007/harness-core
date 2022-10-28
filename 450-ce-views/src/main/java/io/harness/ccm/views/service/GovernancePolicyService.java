/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;
import io.harness.ccm.views.entities.GovernancePolicyFilter;
import io.harness.ccm.views.entities.Policy;

import java.util.List;

public interface GovernancePolicyService {
  boolean save(Policy policy);
  boolean deleteOOTB(String uuid);
  boolean delete(String accountId, String uuid);
  Policy update(Policy policy, String accountId);
  List<Policy> list(GovernancePolicyFilter governancePolicyFilter);
  Policy listName(String accountId, String name, boolean create);
  void check(List<String> policiesIdentifier);
}
