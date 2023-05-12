/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import io.harness.cvng.CVConstants;

import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SLODashboardApiFilter {
  @Parameter(description = CVConstants.USER_JOURNEY_PARAM_MESSAGE)
  @QueryParam("userJourneyIdentifiers")
  List<String> userJourneyIdentifiers;
  @Parameter(description = CVConstants.MONITORED_SERVICE_PARAM_MESSAGE)
  @QueryParam("monitoredServiceIdentifier")
  String monitoredServiceIdentifier;
  @Parameter(description = CVConstants.SLI_TYPE_PARAM_MESSAGE)
  @QueryParam("sliTypes")
  List<ServiceLevelIndicatorType> sliTypes;
  @Parameter(description = CVConstants.TARGET_TYPE_PARAM_MESSAGE)
  @QueryParam("targetTypes")
  List<SLOTargetType> targetTypes;
  @Parameter(description = CVConstants.ERROR_BUDGET_RISK_PARAM_MESSAGE)
  @QueryParam("errorBudgetRisks")
  List<ErrorBudgetRisk> errorBudgetRisks;
  @Parameter(description = "For filtering on the basis of name") @QueryParam("filter") String searchFilter;
  @Parameter(description = "For filtering on the basis of SLO type") ServiceLevelObjectiveType type;
  @Parameter(description = "For filtering on the basis of SLO target spec") SLOTargetFilterDTO sloTargetFilterDTO;
  @Parameter(description = "For filtering on the basis of Composite SLO") String compositeSLOIdentifier;
  @Parameter(description = "For filtering on the basis of SLI Evaluation type")
  @QueryParam("evaluationType")
  SLIEvaluationType evaluationType;
  @Parameter(description = "For filtering the simple slo's on the basis of accountId") boolean childResource;
}
