/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Set;
import java.util.concurrent.ExecutionException;

@OwnedBy(HarnessTeam.IDP)
public interface DelegateSelectorsCache {
  Set<String> get(String accountIdentifier, String host) throws ExecutionException;
  void put(String accountIdentifier, String host, Set<String> delegateSelectors) throws ExecutionException;
  void remove(String accountIdentifier, Set<String> hosts) throws ExecutionException;
}
