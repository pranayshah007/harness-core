/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dashboard;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import java.util.List;
import javax.validation.constraints.NotNull;

public interface DashboardSettingsService {
  DashboardSettings createDashboardSettings(@NotNull String accountId, @NotNull DashboardSettings dashboardSettings);

  DashboardSettings updateDashboardSettings(@NotNull String accountId, @NotNull DashboardSettings dashboardSettings);

  DashboardSettings get(@NotNull String accountId, @NotNull String id);

  boolean doesPermissionsMatch(@NotNull DashboardSettings newDashboard, @NotNull DashboardSettings existingDashboard);

  boolean deleteDashboardSettings(@NotNull String accountId, @NotNull String id);

  PageResponse<DashboardSettings> getDashboardSettingSummary(
      @NotNull String accountId, @NotNull PageRequest pageRequest);

  void addToToMigrateAccountIds(List<String> accountIds);
}
