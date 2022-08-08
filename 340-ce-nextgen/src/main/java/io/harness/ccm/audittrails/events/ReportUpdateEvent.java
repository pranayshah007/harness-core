/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.CEReportSchedule;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReportUpdateEvent extends ReportEvent {
  public static final String REPORT_UPDATED = "ReportUpdated";
  private CEReportSchedule oldReportDTO;

  public ReportUpdateEvent(String accountIdentifier, CEReportSchedule newReportDTO, CEReportSchedule oldReportDTO) {
    super(accountIdentifier, newReportDTO);
    this.oldReportDTO = oldReportDTO;
  }

  @Override
  public String getEventType() {
    return REPORT_UPDATED;
  }
}
