/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.cvng.beans.change.ChangeSummaryDTO;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
@Builder
public class MSHealthReport {
  ServiceHealthDetails serviceHealthDetails;
  ChangeSummaryDTO changeSummary;
  List<AssociatedSLOsDetails> associatedSLOsDetails;
  String internalLinkToEntity;

  @Value
  @Builder
  public static class AssociatedSLOsDetails {
    String identifier;
    String name;
    String scopedMonitoredServiceIdentifier;
    Double sloPerformance;
    Double errorBudgetBurnRate;
  }

  @Value
  @Builder
  public static class ServiceHealthDetails {
    long currentHealthScore;
    long pastHealthScore;
    Double percentageChange;
  }
}
