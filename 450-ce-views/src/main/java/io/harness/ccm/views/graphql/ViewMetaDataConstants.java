/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.graphql;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ViewMetaDataConstants {
  public static final String entityConstantMinStartTime = "startTime_MIN";
  public static final String entityConstantMaxStartTime = "startTime_MAX";
  public static final String entityConstantCost = "cost";
  public static final String entityMarkupAmount = "markupAmount";
  public static final String entityConstantClusterCost = "billingamount";
  public static final String entityConstantIdleCost = "actualidlecost";
  public static final String entityConstantUnallocatedCost = "unallocatedcost";
  public static final String entityConstantSystemCost = "systemcost";
}
