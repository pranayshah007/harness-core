/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.smb;

import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.UNKNOWN;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.SmbConfig;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.SmbHelperService;

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

public class SmbServiceTest extends WingsBaseTest {
  @Mock private SmbHelperService smbHelperService;
  @Inject private SmbService smbService;
  private static final String TEST_SMB_URL = "smb://192.168.128.161/share";
  private static final String TEST_USER = "test";
  private static final String TEST_PASSWORD = "test";

  private static final SmbConfig smbConfig = SmbConfig.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .smbUrl(TEST_SMB_URL)
                                                 .username(TEST_USER)
                                                 .password(TEST_PASSWORD.toCharArray())
                                                 .build();

  @Before
  public void setUp() throws IllegalAccessException {
    FieldUtils.writeField(smbService, "smbHelperService", smbHelperService, true);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldGetArtifactPaths() throws IOException {
    List<String> artifactPaths = new ArrayList(Arrays.asList("a.txt", "dir1/b.txt"));
    when(smbService.getArtifactPaths(smbConfig, null)).thenReturn(artifactPaths);
    assertThat(smbService.getArtifactPaths(smbConfig, null)).isNotNull().hasSize(2);
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

    when(smbService.getBuildDetails(smbConfig, null, artifactPaths, false)).thenReturn(buildDetailsList);
    assertThat(smbService.getBuildDetails(smbConfig, null, artifactPaths, false)).isNotNull().hasSize(1);
  }
}
