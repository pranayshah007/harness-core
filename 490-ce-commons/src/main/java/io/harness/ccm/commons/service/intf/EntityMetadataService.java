/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.service.intf;

import java.util.List;
import java.util.Map;

public interface EntityMetadataService {
  Map<String, String> getEntityIdToNameMapping(List<String> entityIds, String harnessAccountId, String fieldName);
  Map<String, String> getAccountNamePerAwsAccountId(List<String> awsAccountIds, String harnessAccountId);
  Map<String, String> getAccountIdAndNameByAccountNameFilter(String filterAccountName, String harnessAccountId);
}
