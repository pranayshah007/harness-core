/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import software.wings.beans.stats.DeploymentStatistics;
import software.wings.beans.stats.ServiceInstanceStatistics;

import java.util.List;
import javax.validation.constraints.NotNull;

public interface StatisticsService {
  DeploymentStatistics getDeploymentStatistics(@NotNull String accountId, List<String> appIds, int numOfDays);

  DeploymentStatistics getDeploymentStatisticsNew(String accountId, List<String> appIds, int numOfDays);

  ServiceInstanceStatistics getServiceInstanceStatisticsNew(String accountId, List<String> appIds, int numOfDays);

  ServiceInstanceStatistics getServiceInstanceStatistics(@NotNull String accountId, List<String> appIds, int numOfDays);
}
