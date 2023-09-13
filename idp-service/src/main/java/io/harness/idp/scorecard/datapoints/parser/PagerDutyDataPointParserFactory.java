/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class PagerDutyDataPointParserFactory implements DataPointParserFactory {
  private PagerDutyOnCallSetParser pagerDutyOnCallSetParser;
  private PagerDutyIsEscalationPolicySetParser pagerDutyIsEscalationPolicySetParser;
  private PagerDutyNoOfIncidentsInLastThirtyDaysParser pagerDutyNoOfIncidentsInLastThirtyDaysParser;
  private PagerDutyAvgAcknowledgementTimeForLastTenIncidents pagerDutyAvgAcknowledgementTimeForLastTenIncidents;

  public DataPointParser getParser(String identifier) {
    switch (identifier) {
      case IS_ON_CALL_SET:
        return pagerDutyOnCallSetParser;
      case IS_ESCALATION_POLICY_SET:
        return pagerDutyIsEscalationPolicySetParser;
      case NO_OF_INCIDENTS_IN_LAST_THIRTY_DAYS:
        return pagerDutyNoOfIncidentsInLastThirtyDaysParser;
      case AVG_ACKNOWLEDGEMENT_TIME_FOR_LAST_TEN_INCIDENTS_IN_MINUTES:
        return pagerDutyAvgAcknowledgementTimeForLastTenIncidents;

      default:
        throw new UnsupportedOperationException(String.format("Could not find DataPoint parser for %s", identifier));
    }
  }
}
