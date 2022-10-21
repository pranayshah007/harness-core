/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;
import io.harness.ccm.views.entities.Policy;

import java.util.List;

public interface GovernancePolicyService {
  boolean save(Policy policy);
  boolean delete(String accountId, String uuid);
  Policy update(Policy policy);
  List<Policy> list(String accountId);
  List<Policy> findByResource(String resource, String accountId);
  List<Policy> findByTag(String tag, String accountId);
  Policy listid(String accountId, String uuid, boolean create);
  List<Policy> findByTagAndResource(String resource, String tag, String accountId);
  List<Policy> findByStability(String isStablePolicy, String accountId);
  void check(String accountId, List<String> policiesIdentifier);
}
