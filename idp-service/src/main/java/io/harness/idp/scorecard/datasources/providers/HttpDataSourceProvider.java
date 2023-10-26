/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.common.beans.DataSourceType;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.entity.HttpDataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;

import java.util.Map;

public abstract class HttpDataSourceProvider extends DataSourceProvider {
  protected HttpDataSourceProvider(String identifier, DataPointService dataPointService,
      DataSourceLocationFactory dataSourceLocationFactory, DataSourceLocationRepository dataSourceLocationRepository,
      DataPointParserFactory dataPointParserFactory, DataSourceRepository dataSourceRepository) {
    super(identifier, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository, dataPointParserFactory,
        dataSourceRepository);
  }

  protected DataSourceConfig getDataSourceConfig(DataSourceEntity dataSourceEntity,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableUrlPairs) {
    if (!dataSourceEntity.getType().equals(DataSourceType.HTTP)) {
      throw new InvalidRequestException(
          String.format("Wrong data source type (HTTP) mapped for data source %s and account %s",
              dataSourceEntity.getIdentifier(), dataSourceEntity.getAccountIdentifier()));
    }
    HttpDataSourceEntity httpDataSourceEntity = (HttpDataSourceEntity) dataSourceEntity;
    HttpConfig httpConfig = httpDataSourceEntity.getHttpConfig();
    httpConfig.setTarget(replaceUrlsPlaceholdersIfAny(httpConfig.getTarget(), possibleReplaceableUrlPairs));
    matchAndReplaceHeaders(httpConfig.getHeaders(), replaceableHeaders);
    return httpConfig;
  }
}
