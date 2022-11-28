/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANGELO;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.ErrorTrackingDataCollectionInfo;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.models.VerificationType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ErrorTrackingDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject private ErrorTrackingDataCollectionInfoMapper mapper;

  @Test
  @Owner(developers = ANGELO)
  @Category(UnitTests.class)
  public void testToDataConnectionInfo() {
    ErrorTrackingCVConfig cvConfig = new ErrorTrackingCVConfig();
    cvConfig.setServiceIdentifier(randomAlphabetic(10));
    cvConfig.setEnvIdentifier(randomAlphabetic(10));
    cvConfig.setUuid(generateUuid());
    cvConfig.setAccountId(generateUuid());
    cvConfig.setOrgIdentifier(generateUuid());
    cvConfig.setProjectIdentifier(generateUuid());
    ErrorTrackingDataCollectionInfo overOpsDataCollectionInfo =
        mapper.toDataCollectionInfo(cvConfig, VerificationTask.TaskType.DEPLOYMENT);
    assertThat(overOpsDataCollectionInfo.getServiceId()).isEqualTo(cvConfig.getServiceIdentifier());
    assertThat(overOpsDataCollectionInfo.getEnvironmentId()).isEqualTo(cvConfig.getEnvIdentifier());
    assertThat(overOpsDataCollectionInfo.getVerificationType()).isEqualTo(VerificationType.LOG);
    assertThat(overOpsDataCollectionInfo.getDataCollectionDsl()).isEqualTo(cvConfig.getDataCollectionDsl());
    assertThat(overOpsDataCollectionInfo.getAccountId()).isEqualTo(cvConfig.getAccountId());
    assertThat(overOpsDataCollectionInfo.getOrgId()).isEqualTo(cvConfig.getOrgIdentifier());
    assertThat(overOpsDataCollectionInfo.getProjectId()).isEqualTo(cvConfig.getProjectIdentifier());
  }
}
