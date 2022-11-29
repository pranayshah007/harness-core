/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.demo.changesource;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.PagerDutyEventMetaData;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource;
import io.harness.cvng.core.services.api.demo.ChangeSourceDemoDataGenerator;
import io.harness.cvng.core.utils.DateTimeUtils;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PagerdutyChangeSourceDemoDataGenerator implements ChangeSourceDemoDataGenerator<PagerDutyChangeSource> {
  @Inject private Clock clock;
  @Override
  public List<ChangeEventDTO> generate(PagerDutyChangeSource changeSource) {
    Instant time = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());
    return Arrays.asList(
        ChangeEventDTO.builder()
            .accountId(changeSource.getAccountId())
            .changeSourceIdentifier(changeSource.getIdentifier())
            .projectIdentifier(changeSource.getProjectIdentifier())
            .orgIdentifier(changeSource.getOrgIdentifier())
            .monitoredServiceIdentifier(changeSource.getMonitoredServiceIdentifier())
            .eventTime(time.toEpochMilli())
            .type(ChangeSourceType.PAGER_DUTY)
            .metadata(PagerDutyEventMetaData.builder()
                          .assignment("Mark")
                          .assignmentUrl("https://harnesstest.pagerduty.com/users/PGRH68A")
                          .escalationPolicyUrl("https://harnesstest.pagerduty.com/escalation_policies/PTLDSSS")
                          .escalationPolicy("Default")
                          .priority("P3")
                          .pagerDutyUrl("https://api.pagerduty.com/incidents/PDVUQ17")
                          .status("triggered")
                          .title("Service response time alert")
                          .urgency("low")
                          .triggeredAt(time.minus(Duration.ofSeconds(30)))
                          .htmlUrl("https://harnesstest.pagerduty.com/incidents/PDVUQ17")
                          .eventId(UUID.randomUUID().toString())
                          .build())
            .build());
  }
}
