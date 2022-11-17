/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancestats;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.entities.Project;
import io.harness.repositories.instancestats.InstanceStatsRepository;
import io.harness.rule.Owner;

import java.sql.Timestamp;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
public class InstanceStatsServiceImplTest extends InstancesTestBase {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SERVICE_ID = "serviceId";
  @Mock InstanceStatsRepository instanceStatsRepository;
  @InjectMocks InstanceStatsServiceImpl instanceStatsService;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getLastSnapshotTimeTestIfRecordReturnedNotNull() throws Exception {
    Timestamp timestamp = Timestamp.valueOf("2012-07-07 01:01:01");
    Project project =
        Project.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_ID).identifier(PROJECT_ID).build();
    when(instanceStatsRepository.getLastSnapshotTime(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(timestamp);
    assertThat(instanceStatsService.getLastSnapshotTime(project)).isEqualTo(timestamp.toInstant());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getLastSnapshotTimeTestIfRecordReturnedNull() throws Exception {
    Project project =
        Project.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_ID).identifier(PROJECT_ID).build();
    when(instanceStatsRepository.getLastSnapshotTime(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(null);
    assertThat(instanceStatsService.getLastSnapshotTime(project)).isNull();
  }
}
