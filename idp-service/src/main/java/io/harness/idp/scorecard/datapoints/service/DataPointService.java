/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapoints.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.List;
import java.util.Map;
import org.apache.commons.math3.util.Pair;

@OwnedBy(HarnessTeam.IDP)
public interface DataPointService {
  List<DataPoint> getAllDataPointsDetailsForAccountAndDataSource(String accountIdentifier, String dataSourceIdentifier);

  Map<String, List<DataPointEntity>> getDslDataPointsInfo(
      String accountIdentifier, List<String> identifiers, String dataSourceIdentifier);

  List<DataPointEntity> getAllDataPointsForAccount(String accountIdentifier);
  Map<String, DataPoint> getDataPointsMap(String accountIdentifier);
  Map<String, List<Pair<DataPointEntity, List<InputValue>>>> getDslDataPointsInfo(
      String identifier, String s, List<Pair<String, List<InputValue>>> dataPointIdsAndInputValues);
}
