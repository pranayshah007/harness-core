/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.msp.intf;

import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.msp.entities.ManagedAccountStats;
import io.harness.ccm.msp.entities.ManagedAccountTimeSeriesData;
import io.harness.ccm.msp.entities.ManagedAccountsOverview;

import java.util.List;

public interface ManagedAccountDataService {
  List<String> getEntityList(
      String mspAccountId, String managedAccountId, CCMField entity, String searchParam, Integer limit, Integer offset);
  ManagedAccountsOverview getTotalMarkupAndSpend(String mspAccountId);
  ManagedAccountStats getManagedAccountStats(String mspAccountId, long startTime, long endTime);
  ManagedAccountStats getManagedAccountStats(
      String mspAccountId, String managedAccountId, long startTime, long endTime);
  ManagedAccountTimeSeriesData getManagedAccountTimeSeriesData(
      String mspAccountId, String managedAccountId, long startTime, long endTime);
}