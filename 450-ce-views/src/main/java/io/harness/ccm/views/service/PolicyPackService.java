/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import io.harness.ccm.views.entities.PolicyPack;

import java.util.List;

public interface PolicyPackService {
  boolean save(PolicyPack policyPack);
  boolean delete(String accountId, String uuid);
  PolicyPack update(PolicyPack policyPack);
  PolicyPack listName(String accountId, String name, boolean create);
  List<PolicyPack> list(String accountId, PolicyPack policyPack);
  void check( List<String> policyPackIdentifier);
  boolean deleteOOTB(String uuid);
  List<PolicyPack> listPacks(String accountId, List<String> policyPackIDs);
}
