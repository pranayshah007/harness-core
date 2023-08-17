/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl.HarnessPolicyEvaluationDsl;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl.HarnessStoScanDsl;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DslDataProviderFactory {
  HarnessStoScanDsl harnessStoScanDsl;
  HarnessPolicyEvaluationDsl harnessPolicyEvaluationDsl;
  private static final String HARNESS_DATA_SOURCE = "Harness";
  private static final String HARNESS_STO_SCAN_SETUP_DSL = "harness_sto_scan_dsl";
  private static final String HARNESS_POLICY_EVALUATION_DSL = "harness_policy_evaluation_dsl";

  public DslDataProvider getDslDataProvider(String datasourceIdentifier, String dslIdentifier) {
    if (datasourceIdentifier.equals(HARNESS_DATA_SOURCE)) {
      switch (dslIdentifier) {
        case HARNESS_STO_SCAN_SETUP_DSL:
          return harnessStoScanDsl;
        case HARNESS_POLICY_EVALUATION_DSL:
          return harnessPolicyEvaluationDsl;
      }
    } else {
      throw new UnsupportedOperationException(
          String.format("For data source - %s , Dsl is not supported - %s", datasourceIdentifier, dslIdentifier));
    }
    return null;
  }
}
