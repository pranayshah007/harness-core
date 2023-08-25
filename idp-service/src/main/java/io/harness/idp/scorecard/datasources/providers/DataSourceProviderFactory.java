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

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class DataSourceProviderFactory {
  private CatalogProvider catalogProvider;
  private GithubProvider githubProvider;
  private HarnessProvider harnessProvider;
  private CustomProvider customProvider;

  public DataSourceProvider getProvider(String dataSource) {
    switch (dataSource) {
      case CATALOG_PROVIDER:
        return catalogProvider;
      case GITHUB_PROVIDER:
        return githubProvider;
      case HARNESS_PROVIDER:
        return harnessProvider;
      case CUSTOM_PROVIDER:
        return customProvider;
      default:
        throw new IllegalArgumentException("DataSource provider " + dataSource + " is not supported yet");
    }
  }
}
