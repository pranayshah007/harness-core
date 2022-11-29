/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.SplunkDataCollectionInfo;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.models.VerificationType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SplunkDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject private SplunkDataCollectionInfoMapper mapper;

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testToDataConnectionInfo() {
    SplunkCVConfig cvConfig = new SplunkCVConfig();
    cvConfig.setUuid(generateUuid());
    cvConfig.setAccountId(generateUuid());
    cvConfig.setQuery("exception");
    cvConfig.setServiceInstanceIdentifier("host");
    SplunkDataCollectionInfo splunkDataCollectionInfo = mapper.toDataCollectionInfo(cvConfig, TaskType.DEPLOYMENT);
    assertThat(splunkDataCollectionInfo.getQuery()).isEqualTo(cvConfig.getQuery());
    assertThat(splunkDataCollectionInfo.getServiceInstanceIdentifier())
        .isEqualTo(cvConfig.getServiceInstanceIdentifier());
    assertThat(splunkDataCollectionInfo.getVerificationType()).isEqualTo(VerificationType.LOG);
    assertThat(splunkDataCollectionInfo.getDataCollectionDsl()).isEqualTo(cvConfig.getDataCollectionDsl());
  }
}
