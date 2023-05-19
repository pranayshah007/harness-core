/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.dto.CVActivityLog;
import software.wings.beans.dto.CVActivityLog.LogLevel;
import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.delegatetasks.DelegateLogService;

import java.util.Arrays;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class DelegateCVActivityLogServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private DelegateLogService delegateLogService;
  DelegateCVActivityLogService delegateCVActivityLogService;
  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    delegateCVActivityLogService = new DelegateCVActivityLogServiceImpl();
    FieldUtils.writeField(delegateCVActivityLogService, "delegateLogService", delegateLogService, true);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLogger_withPrefixAndParams() {
    int dataCollectionMin = 0;
    String stateExecutionId = generateUuid();
    long now = System.currentTimeMillis();
    DelegateCVActivityLogService.Logger log = delegateCVActivityLogService.getLogger(
        generateUuid(), null, dataCollectionMin, stateExecutionId, "prefix %t, %t", now, now);
    log.info("this is a test log");
    ArgumentCaptor<CVActivityLog> capture = ArgumentCaptor.forClass(CVActivityLog.class);
    verify(delegateLogService).save(any(), capture.capture());
    assertThat(capture.getValue().getLog()).isEqualTo("[Delegate] prefix %t, %t this is a test log");
    assertThat(capture.getValue().getTimestampParams()).isEqualTo(Arrays.asList(now, now));
    assertThat(capture.getValue().getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(capture.getValue().getLogLevel()).isEqualTo(LogLevel.INFO);
  }
}
