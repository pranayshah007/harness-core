/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.PAVIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DatadogLogDataCollectionInfo;
import io.harness.cvng.beans.datadog.DatadogLogDefinition;
import io.harness.cvng.core.entities.DatadogLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.impl.monitoredService.DatadogLogDataCollectionInfoMapper;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DatadogLogDataCollectionInfoMapperTest extends CvNextGenTestBase {
  private static final List<String> MOCKED_INDEXES = Arrays.asList("testIndex1", "testIndex2");
  private static final String MOCKED_QUERY = "*";
  private static final String MOCKED_QUERY_NAME = "mockedQueryName";
  private static final String MOCKED_INSTANCE_IDENTIFIER = "host";

  @Inject private DatadogLogDataCollectionInfoMapper classUnderTest;

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo() {
    DatadogLogCVConfig datadogLogCVConfig = DatadogLogCVConfig.builder()
                                                .indexes(MOCKED_INDEXES)
                                                .query(MOCKED_QUERY)
                                                .queryName(MOCKED_QUERY_NAME)
                                                .serviceInstanceIdentifier(MOCKED_INSTANCE_IDENTIFIER)
                                                .build();

    final DatadogLogDefinition expectedDataLogDefinition = DatadogLogDefinition.builder()
                                                               .indexes(MOCKED_INDEXES)
                                                               .name(MOCKED_QUERY_NAME)
                                                               .query(MOCKED_QUERY)
                                                               .serviceInstanceIdentifier(MOCKED_INSTANCE_IDENTIFIER)
                                                               .build();

    DatadogLogDataCollectionInfo collectionInfoResult =
        classUnderTest.toDataCollectionInfo(datadogLogCVConfig, TaskType.DEPLOYMENT);

    assertThat(collectionInfoResult).isNotNull();
    assertThat(collectionInfoResult.getLogDefinition()).isEqualTo(expectedDataLogDefinition);
  }
}
