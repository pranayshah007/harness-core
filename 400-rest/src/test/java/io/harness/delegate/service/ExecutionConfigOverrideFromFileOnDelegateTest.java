/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.delegate.service.ExecutionConfigOverrideFromFileOnDelegate.filePath;
import static io.harness.rule.OwnerRule.AMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ExecutionConfigOverrideFromFileOnDelegateTest extends CategoryTest {
  private static final String TEST_VALUE = "testValue";
  private static final String TEST_KEY = "testKey";
  private static final String TEXT_WHICH_IS_NOT_JSON = "Random Text which is not json";

  @Before
  public void setUp() throws Exception {
    deleteConfigFileIfExists();
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void secretsShouldBeInitializedAsEmptyIfFileIsNotPresent() {
    ExecutionConfigOverrideFromFileOnDelegate executionConfigOverrideFromFileOnDelegate =
        new ExecutionConfigOverrideFromFileOnDelegate();
    assertThat(executionConfigOverrideFromFileOnDelegate.getLocalDelegateSecrets()).isNotNull();
    assertThat(executionConfigOverrideFromFileOnDelegate.getLocalDelegateSecrets().size()).isEqualTo(0);
    assertThat(executionConfigOverrideFromFileOnDelegate.isLocalConfigPresent()).isFalse();
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void secretsShouldBeInitializedIfFileIsPresent() throws IOException {
    HashMap<String, String> secrets = new HashMap<>() {
      { put(TEST_KEY, TEST_VALUE); }
    };
    String string = new JSONObject(secrets).toString();
    File file = FileUtils.getFile(filePath);
    FileUtils.writeStringToFile(file, string, StandardCharsets.UTF_8);
    ExecutionConfigOverrideFromFileOnDelegate executionConfigOverrideFromFileOnDelegate =
        new ExecutionConfigOverrideFromFileOnDelegate();
    assertThat(executionConfigOverrideFromFileOnDelegate.getLocalDelegateSecrets()).isNotNull();
    assertThat(executionConfigOverrideFromFileOnDelegate.getLocalDelegateSecrets().size()).isEqualTo(1);
    assertThat(executionConfigOverrideFromFileOnDelegate.getLocalDelegateSecrets().get(TEST_KEY)).isEqualTo(TEST_VALUE);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void secretsShouldBeInitializedAsEmptyIfFileIfFileCorrupted() throws IOException {
    File file = FileUtils.getFile(filePath);
    FileUtils.writeStringToFile(file, TEXT_WHICH_IS_NOT_JSON, StandardCharsets.UTF_8);
    ExecutionConfigOverrideFromFileOnDelegate executionConfigOverrideFromFileOnDelegate =
        new ExecutionConfigOverrideFromFileOnDelegate();
    assertThat(executionConfigOverrideFromFileOnDelegate.getLocalDelegateSecrets()).isNotNull();
    assertThat(executionConfigOverrideFromFileOnDelegate.getLocalDelegateSecrets().size()).isEqualTo(0);
  }

  private void deleteConfigFileIfExists() {
    File file = FileUtils.getFile(filePath);
    if (file.exists()) {
      FileUtils.deleteQuietly(file);
    }
  }
}
