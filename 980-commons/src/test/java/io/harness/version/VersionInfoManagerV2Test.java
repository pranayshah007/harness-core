/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.version;

import io.harness.CategoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@Slf4j
public class VersionInfoManagerV2Test extends CategoryTest {

  @Mock
  private FileInputStream fileInputStreamMock;

  @Mock
  private Yaml yamlMock;

  @InjectMocks
  private VersionInfoManagerV2 versionInfoManager;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testGetVersionInfoSuccessfully() throws Exception {
    when(new FileInputStream("/opt/harness/version.yaml")).thenReturn(fileInputStreamMock);
    when(yamlMock.load(any(InputStream.class))).thenReturn(createMockData());
    VersionInfoV2 result = versionInfoManager.getVersionInfo();
    assertEquals(result, versionInfoManager.getCachedVersionInfo());
    verify(fileInputStreamMock, times(1)).close();
  }

  @Test
  void testGetVersionInfoFileNotFound() throws Exception {
    when(new FileInputStream("/opt/harness/version.yaml")).thenThrow(FileNotFoundException.class);
    assertThrows(VersionInfoException.class, () -> versionInfoManager.getVersionInfo());
  }

  @Test
  void testGetVersionInfoYamlException() throws Exception {
    // Mock the FileInputStream and YAML objects
    when(new FileInputStream("/opt/harness/version.yaml")).thenReturn(fileInputStreamMock);
    when(yamlMock.load(any(InputStream.class))).thenThrow(YAMLException.class);

    // Call the method under test and assert that it throws the expected exception
    assertThrows(VersionInfoException.class, () -> versionInfoManager.getVersionInfo());
  }

  @Test
  void testGetVersionInfoOtherException() throws Exception {
    when(new FileInputStream("/opt/harness/version.yaml")).thenReturn(fileInputStreamMock);
    when(yamlMock.load(any(InputStream.class))).thenThrow(Exception.class);
    assertThrows(VersionInfoException.class, () -> versionInfoManager.getVersionInfo());
  }

  private Map<String, Object> createMockData() {
    Map<String, Object> data = new HashMap<>();
    data.put("BUILD_VERSION", "1.0");
    data.put("BUILD_TIME", new Date());
    data.put("BRANCH_NAME", "main");
    data.put("COMMIT_SHA", "abc123");
    return data;
  }
}
