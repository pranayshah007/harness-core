/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.timeseries.data.PrunedInstanceData;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InstanceDataServiceImplTest extends CategoryTest {
  @InjectMocks InstanceDataServiceImpl instanceDataService;
  @Mock InstanceDataDao instanceDataDao;

  private static final String ACCOUNT_ID = "accountId";
  private static final String SETTING_ID = "settingId";
  private static final String INSTANCE_ID = "instanceId";
  private static final long OCCURRED_AT = 21313413;

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testPrunedInstanceDataWithNameCaching() {
    when(instanceDataDao.fetchInstanceDataWithName(ACCOUNT_ID, SETTING_ID, INSTANCE_ID, OCCURRED_AT))
        .thenReturn(InstanceData.builder()
                        .totalResource(Resource.builder().memoryMb(1024.0).cpuUnits(500.0).build())
                        .instanceId(INSTANCE_ID)
                        .build());
    PrunedInstanceData prunedInstanceData =
        instanceDataService.fetchPrunedInstanceDataWithName(ACCOUNT_ID, SETTING_ID, INSTANCE_ID, OCCURRED_AT);
    assertThat(prunedInstanceData.getInstanceId()).isEqualTo(INSTANCE_ID);
  }
}
