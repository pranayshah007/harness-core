/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.scorecard.datasources.constants.Constants.CATALOG_PROVIDER;
import static io.harness.idp.scorecard.datasources.constants.Constants.CUSTOM_PROVIDER;
import static io.harness.idp.scorecard.datasources.constants.Constants.GITHUB_PROVIDER;
import static io.harness.idp.scorecard.datasources.constants.Constants.HARNESS_PROVIDER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.envvariable.repositories.BackstageEnvVariableRepository;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.IDP)
public class DataSourceProviderFactory {
  @Inject DataPointService dataPointService;
  @Inject DataSourceLocationFactory dataSourceLocationFactory;
  @Inject DataSourceLocationRepository dataSourceLocationRepository;
  @Inject DataPointParserFactory dataPointParserFactory;

  @Inject BackstageEnvVariableRepository backstageEnvVariableRepository;
  @Inject SecretManagerClientService ngSecretService;

  @Inject IdpAuthInterceptor idpAuthInterceptor;

  public DataSourceProvider getProvider(String dataSource) {
    switch (dataSource) {
      case CATALOG_PROVIDER:
        return new CatalogProvider(
            dataPointService, dataSourceLocationFactory, dataSourceLocationRepository, dataPointParserFactory);
      case GITHUB_PROVIDER:
        return new GithubProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataPointParserFactory, backstageEnvVariableRepository, ngSecretService);
      case HARNESS_PROVIDER:
        return new HarnessProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataPointParserFactory, idpAuthInterceptor);
      case CUSTOM_PROVIDER:
        return new CustomProvider(
            dataPointService, dataSourceLocationFactory, dataSourceLocationRepository, dataPointParserFactory);
      default:
        throw new IllegalArgumentException("DataSource provider " + dataSource + " is not supported yet");
    }
  }
}
