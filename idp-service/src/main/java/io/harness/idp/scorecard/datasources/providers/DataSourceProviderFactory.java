/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

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

  public List<DataSourceProvider> getProviders() {
    List<DataSourceProvider> dataSourceProviders = new ArrayList<>();
    dataSourceProviders.add(catalogProvider);
    dataSourceProviders.add(githubProvider);
    dataSourceProviders.add(harnessProvider);
    return dataSourceProviders;
  }
}
