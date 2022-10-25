/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.version;

import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.PUNEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.YamlUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class VersionInfoManagerTest extends CategoryTest {
  public static final Pattern VERSION_PATTERN = Pattern.compile("^[1-9]\\.[0-9]\\.[0-9]*\\-\\d{3}$");

  private static String sampleVersionInfoYaml = "version   : 1.0.1\n"
      + "patch : 123\n"
      + "buildNo   : 1.0\n"
      + "gitCommit : 6f6df35\n"
      + "gitBranch : master\n"
      + "timestamp : 180621-0636";

  private static String wrongVersionInfoYaml = "version   : 1.0.1\n"
      + "patch : 1234";

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testGetVersionInfo() {
    VersionInfoManager versionInfoManager = new VersionInfoManager(sampleVersionInfoYaml);
    VersionInfo versionInfo = versionInfoManager.getVersionInfo();
    assertThat(versionInfo.getVersion()).isEqualTo("1.0.1");
    assertThat(versionInfo.getBuildNo()).isEqualTo("1.0");
    assertThat(versionInfo.getGitCommit()).isEqualTo("6f6df35");
    assertThat(versionInfo.getGitBranch()).isEqualTo("master");
    assertThat(versionInfo.getTimestamp()).isEqualTo("180621-0636");
    assertThat(versionInfoManager.getFullVersion()).isEqualTo("1.0.1-123");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void testGetVersionInfoDefault() throws Exception {
    InputStream stream =
        VersionInfoManager.class.getClassLoader().getResourceAsStream("main/resources-filtered/versionInfo.yaml");
    String versionInfoString = IOUtils.toString(stream, StandardCharsets.UTF_8);
    VersionInfo versionInfo = new YamlUtils().read(versionInfoString, VersionInfo.class);
    VersionInfoManager versionInfoManager = new VersionInfoManager();
    assertThat(versionInfoManager.getVersionInfo()).isEqualTo(versionInfo);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testGetVersionWithPatch() {
    VersionInfoManager versionInfoManager = new VersionInfoManager();

    log.info("Full version is: " + versionInfoManager.getFullVersion());

    if (!versionInfoManager.getFullVersion().equals("${build.fullVersion}-${build.patch}")) {
      assertThat(VERSION_PATTERN.matcher(versionInfoManager.getFullVersion()).matches()).isTrue();
    }
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testGetVersionWithPatchFromYamlFile() {
    VersionInfoManager versionInfoManager = new VersionInfoManager(sampleVersionInfoYaml);

    assertThat(VERSION_PATTERN.matcher(versionInfoManager.getFullVersion()).matches()).isTrue();
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testGetVersionWithPatchFromYamlFileShouldFail() {
    VersionInfoManager versionInfoManager = new VersionInfoManager(wrongVersionInfoYaml);

    assertThat(VERSION_PATTERN.matcher(versionInfoManager.getFullVersion()).matches()).isFalse();
  }
}
