/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.usage.beans.cd;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class CDLicenseUsageConstants {
  public static final String DISPLAY_NAME = "Last 30 Days";
  public static final int TIME_PERIOD_IN_DAYS = 30;
  public static final double PERCENTILE = 0.95;
  public static final int SERVICE_INSTANCE_LIMIT = 20;
  public static final String SERVICE_INSTANCES_QUERY_PROPERTY = "serviceInstances";
  public static final String SERVICE_INSTANCES_SORT_PROPERTY = "instanceCount";
  public static final String LAST_DEPLOYED_SERVICE_PROPERTY = "lastDeployed";
  public static final String LICENSES_CONSUMED_QUERY_PROPERTY = "licensesConsumed";
  public static final String LICENSES_CONSUMED_SORT_PROPERTY = "instanceCount";
  public static final String ACTIVE_SERVICES_FILTER_PARAM_MESSAGE = "Details of the Active Services Filter";
  public static final String LICENSE_DATE_USAGE_PARAMS_MESSAGE = "License Date Usage params";
  public static final List<String> ACTIVE_SERVICES_SORT_QUERY_PROPERTIES =
      Arrays.asList(SERVICE_INSTANCES_QUERY_PROPERTY, LAST_DEPLOYED_SERVICE_PROPERTY, LICENSES_CONSUMED_QUERY_PROPERTY);
}
