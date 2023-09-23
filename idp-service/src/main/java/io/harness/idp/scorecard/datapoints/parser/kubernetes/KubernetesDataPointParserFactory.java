/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapoints.parser.kubernetes;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.DAYS_SINCE_LAST_DEPLOYED;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.REPLICAS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class KubernetesDataPointParserFactory implements DataPointParserFactory {
  private KubernetesReplicasParser kubernetesReplicasParser;
  private KubernetesLastDeployedParser kubernetesLastDeployedParser;

  public DataPointParser getParser(String identifier) {
    switch (identifier) {
      case REPLICAS:
        return kubernetesReplicasParser;
      case DAYS_SINCE_LAST_DEPLOYED:
        return kubernetesLastDeployedParser;
      default:
        throw new UnsupportedOperationException(String.format("Could not find DataPoint parser for %s", identifier));
    }
  }
}
