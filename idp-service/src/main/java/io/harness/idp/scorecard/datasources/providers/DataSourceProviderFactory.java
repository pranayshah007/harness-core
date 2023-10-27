/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.BITBUCKET_IDENTIFIER;
import static io.harness.idp.common.Constants.CATALOG_IDENTIFIER;
import static io.harness.idp.common.Constants.CUSTOM_IDENTIFIER;
import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.common.Constants.GITLAB_IDENTIFIER;
import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;
import static io.harness.idp.common.Constants.JIRA_IDENTIFIER;
import static io.harness.idp.common.Constants.KUBERNETES_IDENTIFIER;
import static io.harness.idp.common.Constants.PAGERDUTY_IDENTIFIER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.envvariable.repositories.BackstageEnvVariableRepository;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.datapoints.parser.DataSourceDataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import com.google.inject.Inject;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.IDP)
public class DataSourceProviderFactory {
  @Inject DataPointService dataPointService;
  @Inject DataSourceLocationFactory dataSourceLocationFactory;
  @Inject DataSourceLocationRepository dataSourceLocationRepository;
  @Inject DataSourceDataPointParserFactory dataSourceDataPointParserFactory;

  @Inject BackstageEnvVariableRepository backstageEnvVariableRepository;
  @Inject SecretManagerClientService ngSecretService;

  @Inject IdpAuthInterceptor idpAuthInterceptor;
  @Inject @Named("env") private String env;

  @Inject ConfigReader configReader;
  @Inject DataSourceRepository dataSourceRepository;

  public DataSourceProvider getProvider(String dataSource) {
    switch (dataSource) {
      case CATALOG_IDENTIFIER:
        return new CatalogProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(CATALOG_IDENTIFIER), dataSourceRepository);
      case GITHUB_IDENTIFIER:
        return new GithubProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(GITHUB_IDENTIFIER),
            backstageEnvVariableRepository, ngSecretService, configReader, dataSourceRepository);
      case BITBUCKET_IDENTIFIER:
        return new BitbucketProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(BITBUCKET_IDENTIFIER), configReader,
            dataSourceRepository);
      case GITLAB_IDENTIFIER:
        return new GitlabProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(GITLAB_IDENTIFIER), configReader,
            dataSourceRepository);
      case HARNESS_IDENTIFIER:
        return new HarnessProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(HARNESS_IDENTIFIER), idpAuthInterceptor, env,
            dataSourceRepository);
      case CUSTOM_IDENTIFIER:
        return new CustomProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(CUSTOM_IDENTIFIER), dataSourceRepository);
      case PAGERDUTY_IDENTIFIER:
        return new PagerDutyProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(PAGERDUTY_IDENTIFIER), configReader,
            dataSourceRepository);
      case JIRA_IDENTIFIER:
        return new JiraProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(JIRA_IDENTIFIER), configReader,
            dataSourceRepository);
      case KUBERNETES_IDENTIFIER:
        return new KubernetesProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(KUBERNETES_IDENTIFIER), configReader,
            idpAuthInterceptor, env, dataSourceRepository);
      default:
        throw new IllegalArgumentException("DataSource provider " + dataSource + " is not supported yet");
    }
  }
}
