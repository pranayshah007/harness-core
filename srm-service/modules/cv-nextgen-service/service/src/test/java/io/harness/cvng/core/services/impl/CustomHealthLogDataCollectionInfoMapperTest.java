/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.cvng.beans.customhealthlog.CustomHealthLogInfo;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.entities.CustomHealthLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthLogDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject private CustomHealthLogDataCollectionInfoMapper mapper;

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testToDataConnectionInfo() {
    CustomHealthLogCVConfig cvConfig = new CustomHealthLogCVConfig();
    cvConfig.setUuid(generateUuid());
    cvConfig.setAccountId(generateUuid());
    cvConfig.setQuery("exception");
    cvConfig.setQueryName("query1");
    cvConfig.setLogMessageJsonPath("$.[0].content.logs.[*].message");
    cvConfig.setServiceInstanceJsonPath("$.[0].content.logs.[*].host");
    cvConfig.setTimestampJsonPath("$.[0].content.logs.[*].timestamp");
    cvConfig.setRequestDefinition(
        CustomHealthRequestDefinition.builder()
            .endTimeInfo(TimestampInfo.builder()
                             .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                             .placeholder("end_time")
                             .build())
            .startTimeInfo(TimestampInfo.builder()
                               .timestampFormat(TimestampInfo.TimestampFormat.SECONDS)
                               .placeholder("start_time")
                               .build())
            .urlPath("https://cool-dolo.com")
            .requestBody("{ \"query\":\"*exception*\",\"time\":{\"from\":\"start_time\",\"to\":\"end_time\"} }")
            .method(CustomHealthMethod.POST)
            .build());
    CustomHealthLogInfo customLogInfo =
        mapper.toDataCollectionInfo(cvConfig, VerificationTask.TaskType.DEPLOYMENT).getCustomHealthLogInfo();
    assertThat(customLogInfo.getQueryName()).isEqualTo(cvConfig.getQueryName());
    assertThat(customLogInfo.getBody()).isEqualTo(cvConfig.getRequestDefinition().getRequestBody());
    assertThat(customLogInfo.getEndTimeInfo()).isEqualTo(cvConfig.getRequestDefinition().getEndTimeInfo());
    assertThat(customLogInfo.getStartTimeInfo()).isEqualTo(cvConfig.getRequestDefinition().getStartTimeInfo());
    assertThat(customLogInfo.getMethod()).isEqualTo(cvConfig.getRequestDefinition().getMethod());
    assertThat(customLogInfo.getLogMessageJsonPath()).isEqualTo(cvConfig.getLogMessageJsonPath());
    assertThat(customLogInfo.getServiceInstanceJsonPath()).isEqualTo(cvConfig.getServiceInstanceJsonPath());
    assertThat(customLogInfo.getTimestampJsonPath()).isEqualTo(cvConfig.getTimestampJsonPath());
  }
}
