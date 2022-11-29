/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.StackdriverLogDataCollectionInfo;
import io.harness.cvng.beans.stackdriver.StackdriverLogDefinition;
import io.harness.cvng.core.entities.StackdriverLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StackdriverLogDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject private StackdriverLogDataCollectionInfoMapper mapper;

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo() {
    StackdriverLogCVConfig stackdriverLogCVConfig =
        StackdriverLogCVConfig.builder().messageIdentifier("message").serviceInstanceIdentifier("host").build();
    stackdriverLogCVConfig.setQuery("query");
    stackdriverLogCVConfig.setQueryName("name");

    StackdriverLogDataCollectionInfo info = mapper.toDataCollectionInfo(stackdriverLogCVConfig, TaskType.DEPLOYMENT);

    assertThat(info).isNotNull();
    assertThat(info.getLogDefinition())
        .isEqualTo(StackdriverLogDefinition.builder()
                       .name(stackdriverLogCVConfig.getQueryName())
                       .query(stackdriverLogCVConfig.getQuery())
                       .serviceInstanceIdentifier(stackdriverLogCVConfig.getServiceInstanceIdentifier())
                       .messageIdentifier(stackdriverLogCVConfig.getMessageIdentifier())
                       .build());
    assertThat(info.getDataCollectionDsl()).startsWith("Var header = {}");
  }
}
