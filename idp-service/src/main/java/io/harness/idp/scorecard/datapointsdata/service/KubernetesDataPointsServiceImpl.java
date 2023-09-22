/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.service;

import static io.harness.idp.common.Constants.KUBERNETES_IDENTIFIER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DataSourceDsl;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory.DataSourceDslFactory;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.KubernetesConfig;

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
public class KubernetesDataPointsServiceImpl implements KubernetesDataPointsService {
  DataSourceDslFactory dataSourceDataProviderFactory;
  DataPointService dataPointService;

  @Override
  public Map<String, Object> getDataPointDataValues(String accountIdentifier, KubernetesConfig kubernetesConfig) {
    DataSourceDsl dataSourceDataProvider =
        dataSourceDataProviderFactory.getDataSourceDataProvider(KUBERNETES_IDENTIFIER);

    Map<String, List<DataPointEntity>> dataToFetch = dataPointService.getDslDataPointsInfo(accountIdentifier,
        kubernetesConfig.getDataSourceLocation()
            .getDataPoints()
            .stream()
            .map(DataPointInputValues::getDataPointIdentifier)
            .collect(Collectors.toList()),
        KUBERNETES_IDENTIFIER);

    List<String> dslIdentifiers = new ArrayList<>(dataToFetch.keySet());
    log.info("Mapped DSL identifier for datapoints - {}", dslIdentifiers.get(0));

    DslDataProvider dslDataProvider = dataSourceDataProvider.getDslDataProvider(dslIdentifiers.get(0));
    return dslDataProvider.getDslData(accountIdentifier, kubernetesConfig);
  }
}
