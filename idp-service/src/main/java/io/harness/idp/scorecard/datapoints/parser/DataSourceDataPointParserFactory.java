/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.kubernetes.KubernetesDataPointParserFactory;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DataSourceDataPointParserFactory {
  GithubDataPointParserFactory githubDataPointParserFactory;
  BitbucketDataPointParserFactory bitbucketDataPointParserFactory;
  HarnessDataPointParserFactory harnessDataPointParserFactory;
  CatalogDataPointParserFactory catalogDataPointParserFactory;
  KubernetesDataPointParserFactory kubernetesDataPointParserFactory;
  CustomDataPointParserFactory customDataPointParserFactory;
  PagerDutyDataPointParserFactory pagerDutyDataPointParserFactory;
  JiraDataPointParserFactory jiraDataPointParserFactory;

  public DataPointParserFactory getDataPointParserFactory(String identifier) {
    switch (identifier) {
      case HARNESS_IDENTIFIER:
        return harnessDataPointParserFactory;
      case GITHUB_IDENTIFIER:
        return githubDataPointParserFactory;
      case BITBUCKET_IDENTIFIER:
        return bitbucketDataPointParserFactory;
      case CATALOG_IDENTIFIER:
        return catalogDataPointParserFactory;
      case KUBERNETES_IDENTIFIER:
        return kubernetesDataPointParserFactory;
      case CUSTOM_IDENTIFIER:
        return customDataPointParserFactory;
      case PAGERDUTY_IDENTIFIER:
        return pagerDutyDataPointParserFactory;
      case JIRA_IDENTIFIER:
        return jiraDataPointParserFactory;
      default:
        throw new UnsupportedOperationException(String.format("Could not find Datasource for %s", identifier));
    }
  }
}
