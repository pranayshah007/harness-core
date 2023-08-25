/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import io.harness.idp.onboarding.beans.BackstageCatalogEntity;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class CatalogDsl implements DataSourceLocation {
  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      String dataSourceLocationEntity, Map<String, Set<String>> dataPointsAndInputValues) {
    return null;
  }
}
