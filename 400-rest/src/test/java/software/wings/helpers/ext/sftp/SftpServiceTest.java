/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.sftp;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.SftpConfig;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.SftpHelperService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class SftpServiceTest extends WingsBaseTest {
  @Mock private SftpHelperService sftpHelperService;
  @Inject private SftpService sftpService;
  private static final String TEST_SFTP_URL = "sftp://192.168.128.32";
  private static final String TEST_USER = "test";
  private static final String TEST_PASSWORD = "test";

  private static final SftpConfig sftpConfig = SftpConfig.builder()
                                                   .accountId(ACCOUNT_ID)
                                                   .sftpUrl(TEST_SFTP_URL)
                                                   .username(TEST_USER)
                                                   .password(TEST_PASSWORD.toCharArray())
                                                   .build();

  @Before
  public void setUp() throws IllegalAccessException {
    FieldUtils.writeField(sftpService, "sftpHelperService", sftpHelperService, true);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifactPaths() throws IOException {
    List<String> artifactPaths = new ArrayList(Arrays.asList("a.txt", "dir1/b.txt"));
    when(sftpService.getArtifactPaths(sftpConfig, null)).thenReturn(artifactPaths);
    assertThat(sftpService.getArtifactPaths(sftpConfig, null)).isNotNull().hasSize(2);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetBuildDetails() throws IOException {
    List<String> artifactPaths = new ArrayList(Arrays.asList("a.txt"));
    List<BuildDetails> buildDetailsList = Lists.newArrayList();
    Map<String, String> buildParams = new HashMap<>();
    buildParams.put(ArtifactMetadataKeys.artifactPath, "a.txt");
    buildParams.put("fileName", "a.txt");
    buildParams.put("allocationSize", "100");

    buildDetailsList.add(aBuildDetails()
                             .withNumber(buildParams.get("filename"))
                             .withArtifactPath(buildParams.get(ArtifactMetadataKeys.artifactPath))
                             .withBuildParameters(buildParams)
                             .build());

    when(sftpService.getBuildDetails(sftpConfig, null, artifactPaths, false)).thenReturn(buildDetailsList);
    assertThat(sftpService.getBuildDetails(sftpConfig, null, artifactPaths, false)).isNotNull().hasSize(1);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenGetBuildDetails() throws IOException {
    when(sftpHelperService.getArtifactDetails(sftpConfig, null, new ArrayList<>()))
        .thenThrow(new RuntimeException("Some Error"));
    assertThatThrownBy(() -> sftpService.getBuildDetails(sftpConfig, null, new ArrayList<>(), false))
        .isInstanceOf(InvalidArtifactServerException.class)
        .extracting("message")
        .isEqualTo("INVALID_ARTIFACT_SERVER");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenGetArtifactPaths() throws IOException {
    when(sftpHelperService.getSftpPaths(sftpConfig, null)).thenThrow(new RuntimeException("Some Error"));
    assertThatThrownBy(() -> sftpService.getArtifactPaths(sftpConfig, null))
        .isInstanceOf(InvalidArtifactServerException.class)
        .extracting("message")
        .isEqualTo("INVALID_ARTIFACT_SERVER");
  }
}
