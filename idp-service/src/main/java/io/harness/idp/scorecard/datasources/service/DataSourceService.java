/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datasources.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.DataSource;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public interface DataSourceService {
  List<DataSource> getAllDataSourcesDetailsForAnAccount(String accountIdentifier);

  List<DataPoint> getAllDataPointsDetailsForDataSource(String accountIdentifier, String dataSourceIdentifier);
}
