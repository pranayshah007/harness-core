/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.citasks.cik8handler.helper.SecretVolumesHelper.CI_MOUNT_VOLUMES;
import static io.harness.rule.OwnerRule.VISTAAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.system.SystemWrapper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
@PrepareForTest(SecretVolumesHelper.class)
public class SecretVolumesHelperTest extends CategoryTest {
  @InjectMocks SecretVolumesHelper secretVolumesHelper;
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private File tempFile1, tempFile2;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    tempFile1 = temporaryFolder.newFile();
    tempFile2 = temporaryFolder.newFile();
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void testGetSecretKey() {
    String key1 = secretVolumesHelper.getSecretKey("prefix", "/path/to/a.crt");
    assertThat(key1).startsWith("prefix-a-crt");
    String key2 = secretVolumesHelper.getSecretKey("prefix", "/different/path/to/a.crt");
    assertThat(key2).startsWith("prefix-a-crt");
    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void testGetSecretVolumeMappingsNotConfigured() {
    try (MockedStatic<SystemWrapper> mockStatic = mockStatic(SystemWrapper.class)) {
      mockStatic.when(() -> SystemWrapper.getenv(Mockito.eq(CI_MOUNT_VOLUMES))).thenReturn("");
      Map<String, List<String>> ret = secretVolumesHelper.getSecretVolumeMappings();
      assertThat(isEmpty(ret));
    }
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void testGetSecretVolumeMappingsConfigured() {
    String dest1 = "/path/to/dest.crt";
    String dest2 = "/another/path/to/dest446.crt";
    String dest3 = "/different/path/to/dest39393.crt";
    String dest4 = "/path/to/random.crt";
    String path1 = tempFile1.getAbsolutePath();
    String path2 = tempFile2.getAbsolutePath();
    String secretVolumes =
        String.format("%s:%s,%s:%s,%s:%s,%s:%s", path1, dest1, path1, dest2, path2, dest3, path2, dest4);
    try (MockedStatic<SystemWrapper> mockStatic = mockStatic(SystemWrapper.class)) {
      mockStatic.when(() -> SystemWrapper.getenv(CI_MOUNT_VOLUMES)).thenReturn(secretVolumes);
      Map<String, List<String>> ret = secretVolumesHelper.getSecretVolumeMappings();

      // Mappings should look like: [path1 -> [dest1, dest2], path2 -> [dest3, dest4]]
      assertThat(ret.size()).isEqualTo(2);
      assertThat(ret.containsKey(path1));
      assertThat(ret.containsKey(path2));
      assertThat(ret.get(path1).contains(dest1));
      assertThat(ret.get(path1).contains(dest2));
      assertThat(ret.get(path2).contains(dest3));
      assertThat(ret.get(path2).contains(dest4));
    }
  }
}
