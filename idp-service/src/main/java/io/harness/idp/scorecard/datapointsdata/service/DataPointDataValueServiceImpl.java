/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.service;

import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DataSourceDsl;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory.DataSourceDslFactory;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfo;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DataPointDataValueServiceImpl implements DataPointDataValueService {
  DataSourceDslFactory dataSourceDataProviderFactory;
  DataPointService dataPointService;

  public Map<String, Object> getDataPointDataValues(
      String accountIdentifier, String datasourceIdentifier, DataSourceDataPointInfo dataSourceDataPointInfo) {
    DataSourceDsl dataSourceDataProvider =
        dataSourceDataProviderFactory.getDataSourceDataProvider(datasourceIdentifier);

    Map<String, List<DataPointEntity>> dataToFetch = dataPointService.getDslDataPointsInfo(accountIdentifier,
        dataSourceDataPointInfo.getDataSourceLocation()
            .getDataPoints()
            .stream()
            .map(dataPointInputValues -> dataPointInputValues.getDataPointIdentifier())
            .collect(Collectors.toList()),
        datasourceIdentifier);

    List<String> dslIdentifiers = dataToFetch.keySet().stream().collect(Collectors.toList());
    log.info("Mapped DSL identifier for datapoints - {}", dslIdentifiers.get(0));

    DslDataProvider dslDataProvider = dataSourceDataProvider.getDslDataProvider(dslIdentifiers.get(0));
    return dslDataProvider.getDslData(datasourceIdentifier, dataSourceDataPointInfo);
  }
}
