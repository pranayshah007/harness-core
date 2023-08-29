/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DataSourceDsl;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory.DataSourceDslFactory;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfo;

import com.google.inject.Inject;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DataPointDataValueServiceImpl implements DataPointDataValueService {
  DataSourceDslFactory dataSourceDataProviderFactory;

  public Map<String, Object> getDataPointDataValues(
      String datasourceIdentifier, DataSourceDataPointInfo dataSourceDataPointInfo) {
    DataSourceDsl dataSourceDataProvider =
        dataSourceDataProviderFactory.getDataSourceDataProvider(datasourceIdentifier);
    DslDataProvider dslDataProvider = dataSourceDataProvider.getDslDataProvider(
        dataSourceDataPointInfo.getDataSourceLocation().getDataSourceLocationIdentifier());
    return dslDataProvider.getDslData(datasourceIdentifier, dataSourceDataPointInfo);
  }
}
