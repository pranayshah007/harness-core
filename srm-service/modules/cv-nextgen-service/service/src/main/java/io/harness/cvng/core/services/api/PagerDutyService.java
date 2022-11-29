/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.pagerduty.PagerDutyServiceDetail;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource;

import java.util.List;

@OwnedBy(CV)
public interface PagerDutyService {
  List<PagerDutyServiceDetail> getPagerDutyServices(
      ProjectParams projectParams, String connectorIdentifier, String requestGuid, String query);

  void registerPagerDutyWebhook(
      MonitoredServiceParams monitoredServiceParams, PagerDutyChangeSource pagerDutyChangeSource);

  void deletePagerdutyWebhook(ProjectParams projectParams, PagerDutyChangeSource pagerDutyChangeSource);
}
