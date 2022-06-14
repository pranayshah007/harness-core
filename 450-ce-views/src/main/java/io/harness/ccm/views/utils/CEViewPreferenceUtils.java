/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.utils;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewPreferences;

import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CE)
public class CEViewPreferenceUtils {
  public ViewPreferences getCEViewPreferencesForMigration(final CEView ceView) {
    return ViewPreferences.builder()
        .includeOthers(true)
        .includeUnallocatedCost(isClusterDataSourceOnly(ceView.getDataSources()))
        .build();
  }

  public ViewPreferences getCEViewPreferences(final CEView ceView) {
    final ViewPreferences viewPreferences = ceView.getViewPreferences();
    final boolean includeOthers =
        Objects.nonNull(viewPreferences) && Boolean.TRUE.equals(viewPreferences.getIncludeOthers());
    final boolean includeUnallocatedCost =
        Objects.nonNull(viewPreferences) && Boolean.TRUE.equals(viewPreferences.getIncludeUnallocatedCost());
    return ViewPreferences.builder()
        .includeOthers(includeOthers)
        .includeUnallocatedCost(includeUnallocatedCost && isClusterDataSourceOnly(ceView.getDataSources()))
        .build();
  }

  private boolean isClusterDataSourceOnly(final List<ViewFieldIdentifier> dataSources) {
    return Objects.nonNull(dataSources) && dataSources.contains(ViewFieldIdentifier.CLUSTER) && dataSources.size() == 1;
  }
}