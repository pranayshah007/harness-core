/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.utils.WingsTestConstants.PARENT;
import static software.wings.utils.WingsTestConstants.PATH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.SftpConfig;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.api.client.util.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.PathComponents;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class SftpHelperServiceTest extends WingsBaseTest {
  public static final String ACCOUNT_ID = "ACCOUNT_ID";

  @Inject SftpHelperService sftpHelperService;
  @Mock SSHClient sshClient;
  @Mock SFTPClient sftpClient;
  @Mock SftpHelperService mockSftpHelperService;

  private static final String A_DIR = ".";
  private static final String A_PATH = "test.zip";
  private static final String SFTP_WIN_URL = "sftp:\\\\10.0.0.1";
  private static final String SFTP_UNIX_URL = "sftp://10.0.0.1";
  private static final String FTP_WIN_URL = "ftp:\\\\10.0.0.1";
  private static final String FTP_UNIX_URL = "ftp://10.0.0.1";
  private static final String DOMAIN = "test";
  private static final String USER = "test";
  private static final String PASSWORD = "test";

  private static final SftpConfig sftpConfig = SftpConfig.builder()
                                                   .accountId(ACCOUNT_ID)
                                                   .domain(DOMAIN)
                                                   .username(USER)
                                                   .password(PASSWORD.toCharArray())
                                                   .sftpUrl(SFTP_WIN_URL)
                                                   .build();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetSftpPaths() throws IOException {
    List<String> artifactPaths = new ArrayList<>();
    artifactPaths.add(ArtifactMetadataKeys.artifactPath);

    // Create SFTP client
    doReturn(sftpClient).when(sshClient).newSFTPClient();
    List<RemoteResourceInfo> remoteResourceInfoList = new ArrayList<>();
    remoteResourceInfoList.add(new RemoteResourceInfo(new PathComponents(A_DIR, A_PATH, "\\"), FileAttributes.EMPTY));
    doReturn(remoteResourceInfoList).when(sftpClient).ls(A_DIR);

    assertThat(remoteResourceInfoList).isNotNull().hasSize(1);
    assertThat(remoteResourceInfoList.get(0).getName()).isEqualTo(A_PATH);
    assertThat(remoteResourceInfoList.get(0).getParent()).isEqualTo(A_DIR);
    assertThat(remoteResourceInfoList.get(0).getPath()).isEqualTo(A_DIR + "\\" + A_PATH);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetSftpConnectionHost() {
    assertThat(sftpHelperService.getSFTPConnectionHost(SFTP_WIN_URL)).isNotEmpty().isEqualTo("10.0.0.1");
    assertThat(sftpHelperService.getSFTPConnectionHost(SFTP_UNIX_URL)).isNotEmpty().isEqualTo("10.0.0.1");
    assertThat(sftpHelperService.getSFTPConnectionHost(FTP_WIN_URL)).isNotEmpty().isEqualTo("10.0.0.1");
    assertThat(sftpHelperService.getSFTPConnectionHost(FTP_UNIX_URL)).isNotEmpty().isEqualTo("10.0.0.1");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetArtifactBuildDetails() throws IOException {
    // Create SFTP client
    doReturn(sftpClient).when(sshClient).newSFTPClient();
    List<RemoteResourceInfo> remoteResourceInfoList = new ArrayList<>();
    remoteResourceInfoList.add(new RemoteResourceInfo(new PathComponents(A_DIR, A_PATH, "\\"), FileAttributes.EMPTY));
    doReturn(remoteResourceInfoList).when(sftpClient).ls(A_DIR);

    assertThat(remoteResourceInfoList).isNotNull().hasSize(1);
    assertThat(remoteResourceInfoList.get(0).getName()).isEqualTo(A_PATH);
    assertThat(remoteResourceInfoList.get(0).getParent()).isEqualTo(A_DIR);
    assertThat(remoteResourceInfoList.get(0).getPath()).isEqualTo(A_DIR + "\\" + A_PATH);

    List<BuildDetails> buildDetailsListForArtifactPath = Lists.newArrayList();
    Map<String, String> map = new HashMap<>();
    map.put(ArtifactMetadataKeys.artifactPath, ArtifactMetadataKeys.artifactPath);
    map.put(ArtifactMetadataKeys.url, SFTP_WIN_URL);
    map.put(ArtifactMetadataKeys.artifactFileName, remoteResourceInfoList.get(0).getName());
    map.put(PATH, remoteResourceInfoList.get(0).getPath());
    map.put(PARENT, remoteResourceInfoList.get(0).getParent());

    buildDetailsListForArtifactPath.add(aBuildDetails()
                                            .withNumber(remoteResourceInfoList.get(0).getName())
                                            .withArtifactPath(ArtifactMetadataKeys.artifactPath)
                                            .withBuildUrl(SFTP_WIN_URL)
                                            .withBuildParameters(map)
                                            .build());

    List<String> artifactPaths = Lists.newArrayList();
    artifactPaths.add(A_PATH);
    doReturn(buildDetailsListForArtifactPath)
        .when(mockSftpHelperService)
        .getArtifactDetails(sftpConfig, null, artifactPaths);
    assertThat(buildDetailsListForArtifactPath).isNotNull().hasSize(1);
  }
}
