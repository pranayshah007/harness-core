/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.SumologicLogDataCollectionInfo;
import io.harness.cvng.core.entities.SumologicLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SumologicLogDataCollectionInfoMapperTest extends CvNextGenTestBase {
  private static final String MOCKED_QUERY = "_sourceCategory=windows/performance";
  private static final String MOCKED_QUERY_NAME = "mockedQueryName";
  private static final String MOCKED_INSTANCE_IDENTIFIER = "host";
  @Inject private SumologicLogDataCollectionInfoMapper sumologicLogDataCollectionInfoMapper;

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo() {
    SumologicLogCVConfig sumologicLogCVConfig = SumologicLogCVConfig.builder()
                                                    .query(MOCKED_QUERY)
                                                    .queryName(MOCKED_QUERY_NAME)
                                                    .serviceInstanceIdentifier(MOCKED_INSTANCE_IDENTIFIER)
                                                    .build();

    SumologicLogDataCollectionInfo sumologicLogDataCollectionInfo =
        sumologicLogDataCollectionInfoMapper.toDataCollectionInfo(
            sumologicLogCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    assertThat(sumologicLogDataCollectionInfo).isNotNull();
    assertThat(sumologicLogDataCollectionInfo.getQuery()).isEqualTo(MOCKED_QUERY);
    assertThat(sumologicLogDataCollectionInfo.getServiceInstanceIdentifier()).isEqualTo(MOCKED_INSTANCE_IDENTIFIER);
  }
}
