/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.reader;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.SettingAttribute;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.StepExecution;

public class SettingAttributeReaderTest extends CategoryTest {
  @Inject @InjectMocks private SettingAttributeReader settingAttributeReader;
  @Mock StepExecution stepExecution;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    settingAttributeReader.beforeStep(stepExecution);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testS3SyncRead() {
    SettingAttribute settingAttribute = settingAttributeReader.read();
    assertThat(settingAttribute).isNotNull();
    SettingAttribute secondReadOutput = settingAttributeReader.read();
    assertThat(secondReadOutput).isNull();
  }
}
