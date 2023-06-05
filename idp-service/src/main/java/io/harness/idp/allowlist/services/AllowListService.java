/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.allowlist.services;

import io.harness.spec.server.idp.v1.model.HostInfo;

import java.util.List;

public interface AllowListService {
  List<HostInfo> getAllowList(String harnessAccount) throws Exception;
  List<HostInfo> saveAllowList(List<HostInfo> hostInfoList, String harnessAccount) throws Exception;
}
