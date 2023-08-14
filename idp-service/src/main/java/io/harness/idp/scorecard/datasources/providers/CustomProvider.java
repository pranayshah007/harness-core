/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.HttpDsl;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class CustomProvider implements DataSourceProvider {
  private HttpDsl httpDsl;

  @Override
  public String getProviderIdentifier() {
    return "custom";
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, BackstageCatalogEntity entity, Map<String, Set<String>> dataPointsAndInputValues) {
    Map<DataSourceLocationEntity, List<DataPointEntity>> dataToFetch = new HashMap<>();
    Map<String, Map<String, Object>> aggregatedData = new HashMap<>();
    for (DataSourceLocationEntity dataSourceLocationEntity : dataToFetch.keySet()) {
      Map<String, Object> data =
          httpDsl.fetchData(accountIdentifier, entity, dataSourceLocationEntity.getIdentifier(), new HashMap<>());
      aggregatedData.getOrDefault("custom", new HashMap<>()).putAll(data);
    }
    return aggregatedData;
  }
}
