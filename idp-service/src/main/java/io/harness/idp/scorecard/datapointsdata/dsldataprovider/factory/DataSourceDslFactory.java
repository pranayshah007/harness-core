/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory;

import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DataSourceDsl;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DataSourceDslFactory {
  HarnessDslFactory harnessDslFactory;

  public DataSourceDsl getDataSourceDataProvider(String dataSourceIdentifier) {
    switch (dataSourceIdentifier) {
      case HARNESS_IDENTIFIER:
        return harnessDslFactory;
      default:
        throw new UnsupportedOperationException(
            String.format("Datasource - %s is not supported ", dataSourceIdentifier));
    }
  }
}
