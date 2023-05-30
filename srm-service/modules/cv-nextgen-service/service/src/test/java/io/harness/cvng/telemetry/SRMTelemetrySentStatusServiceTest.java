/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.telemetry;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.services.api.SRMTelemetrySentStatusService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CV)
public class SRMTelemetrySentStatusServiceTest extends CvNextGenTestBase {
  @Inject private SRMTelemetrySentStatusService srmTelemetrySentStatusService;

  private BuilderFactory builderFactory;

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpdateTimestampIfOlderThan_update() {
    builderFactory = BuilderFactory.getDefault();
    Instant instant = Instant.now();
    boolean result = srmTelemetrySentStatusService.updateTimestampIfOlderThan(
        builderFactory.getContext().getAccountId(), instant.toEpochMilli(), instant.toEpochMilli());
    assertThat(result).isEqualTo(true);
    result = srmTelemetrySentStatusService.updateTimestampIfOlderThan(builderFactory.getContext().getAccountId(),
        instant.toEpochMilli(), instant.plus(Duration.ofDays(1l)).toEpochMilli());
    assertThat(result).isEqualTo(true);
  }
}
