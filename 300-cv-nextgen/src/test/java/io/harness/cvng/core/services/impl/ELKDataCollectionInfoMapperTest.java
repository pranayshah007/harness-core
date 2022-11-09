/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.ELKDataCollectionInfo;
import io.harness.cvng.core.entities.ELKCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ELKDataCollectionInfoMapperTest extends CvNextGenTestBase {
  private static final String MOCKED_INDEX = "testIndex1";
  private static final String MOCKED_QUERY = "*";
  private static final String MOCKED_QUERY_NAME = "mockedQueryName";
  private static final String MOCKED_INSTANCE_IDENTIFIER = "host";
  private static final String MOCKED_MESSAGE_IDENTIFIER = "message";
  private static final String MOCKED_TIMESTAMP_IDENTIFIER = "timeStamp";
  private static final String MOCKED_TIMESTAMP_FORMAT = "timeStampFormat";

  @Inject private ELKDataCollectionInfoMapper elkDataCollectionInfoMapper;

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo() {
    ELKCVConfig elkcvConfig = ELKCVConfig.builder()
                                  .index(MOCKED_INDEX)
                                  .query(MOCKED_QUERY)
                                  .queryName(MOCKED_QUERY_NAME)
                                  .serviceInstanceIdentifier(MOCKED_INSTANCE_IDENTIFIER)
                                  .messageIdentifier(MOCKED_MESSAGE_IDENTIFIER)
                                  .timeStampIdentifier(MOCKED_TIMESTAMP_IDENTIFIER)
                                  .timeStampFormat(MOCKED_TIMESTAMP_FORMAT)
                                  .build();

    ELKDataCollectionInfo elkDataCollectionInfo =
        elkDataCollectionInfoMapper.toDataCollectionInfo(elkcvConfig, VerificationTask.TaskType.DEPLOYMENT);

    assertThat(elkDataCollectionInfo).isNotNull();
    assertThat(elkDataCollectionInfo.getIndex()).isEqualTo(MOCKED_INDEX);
    assertThat(elkDataCollectionInfo.getQuery()).isEqualTo(MOCKED_QUERY);
    assertThat(elkDataCollectionInfo.getServiceInstanceIdentifier()).isEqualTo(MOCKED_INSTANCE_IDENTIFIER);
    assertThat(elkDataCollectionInfo.getTimeStampIdentifier()).isEqualTo(MOCKED_TIMESTAMP_IDENTIFIER);
    assertThat(elkDataCollectionInfo.getTimeStampFormat()).isEqualTo(MOCKED_TIMESTAMP_FORMAT);
    assertThat(elkDataCollectionInfo.getMessageIdentifier()).isEqualTo(MOCKED_MESSAGE_IDENTIFIER);
  }
}
