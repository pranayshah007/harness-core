/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.KARAN_SARASWAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.AzureLogsDataCollectionInfo;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureLogsDataCollectionInfoMapperTest extends CvNextGenTestBase {
  private final String MOCKED_QUERY = "let FindString = \"\";\n"
      + "ContainerLog \n"
      + "| where LogEntry has FindString \n"
      + "|take 100\n";
  private final String MOCKED_INDEX = "resourceId";
  private final String MOCKED_QUERY_NAME = "mockedQueryName";
  private final String MOCKED_INSTANCE_IDENTIFIER = "[3]";
  private final String MOCKED_MESSAGE_IDENTIFIER = "[10]";
  private final String MOCKED_TIMESTAMP_IDENTIFIER = "[4]";
  @Inject private AzureLogsDataCollectionInfoMapper azureLogsDataCollectionInfoMapper;

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testToAzureDataCollectionInfo() {
    NextGenLogCVConfig nextGenLogCVConfig = NextGenLogCVConfig.builder()
                                                .query(MOCKED_QUERY)
                                                .queryName(MOCKED_QUERY_NAME)
                                                .queryParams(QueryParams.builder()
                                                                 .serviceInstanceField(MOCKED_INSTANCE_IDENTIFIER)
                                                                 .messageIdentifier(MOCKED_MESSAGE_IDENTIFIER)
                                                                 .timeStampIdentifier(MOCKED_TIMESTAMP_IDENTIFIER)
                                                                 .index(MOCKED_INDEX)
                                                                 .build())
                                                .build();

    AzureLogsDataCollectionInfo azureLogsDataCollectionInfo = azureLogsDataCollectionInfoMapper.toDataCollectionInfo(
        nextGenLogCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    assertThat(azureLogsDataCollectionInfo.getQuery()).isEqualTo(MOCKED_QUERY);
    assertThat(azureLogsDataCollectionInfo.getResourceId()).isEqualTo(MOCKED_INDEX);
    assertThat(azureLogsDataCollectionInfo.getServiceInstanceIdentifier()).isEqualTo(MOCKED_INSTANCE_IDENTIFIER);
    assertThat(azureLogsDataCollectionInfo.getMessageIdentifier()).isEqualTo(MOCKED_MESSAGE_IDENTIFIER);
    assertThat(azureLogsDataCollectionInfo.getTimeStampIdentifier()).isEqualTo(MOCKED_TIMESTAMP_IDENTIFIER);
  }
}
