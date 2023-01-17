/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.HINGER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.SmbConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import software.wings.service.impl.realization.SmbHelperService;

@OwnedBy(HarnessTeam.CDC)
public class SmbHelperServiceTest extends WingsBaseTest {
  public static final String ACCOUNT_ID = "ACCOUNT_ID";

  @Inject
  SmbHelperService smbHelperService;
  @Mock SMBClient smbClient;
  @Mock Connection connection;
  @Mock Session session;
  @Mock DiskShare diskShare;
  @Mock FileIdBothDirectoryInformation fileIdBothDirectoryInformation;
  @Mock SmbHelperService mockSMBHelperService;

  private static final String SHARE_FOLDER = "share";
  private static final String PATH = "";
  private static final String SEARCH_PATTERN = "*";
  private static final String DOMAIN = "test";
  private static final String USER = "test";
  private static final String PASSWORD = "test";
  private static final String SHARE_URL = "smb:\\\\10.0.0.1\\share";

  private static final SmbConfig smbConfig = SmbConfig.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .domain(DOMAIN)
                                                 .username(USER)
                                                 .password(PASSWORD.toCharArray())
                                                 .smbUrl(SHARE_URL)
                                                 .build();

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldGetSmbPaths() throws IOException {
    // Mock SmbClient, Connection, DiskShare and Session
    doReturn(connection).when(smbClient).connect(smbHelperService.getSMBConnectionHost(smbConfig.getSmbUrl()));
    AuthenticationContext ac =
        new AuthenticationContext(smbConfig.getUsername(), smbConfig.getPassword(), smbConfig.getDomain());
    doReturn(session).when(connection).authenticate(ac);
    doReturn(diskShare).when(session).connectShare(SHARE_FOLDER);

    // List files and directories from share
    List<FileIdBothDirectoryInformation> fileInfoList = new ArrayList<>();
    fileInfoList.add(fileIdBothDirectoryInformation);
    when(diskShare.list(PATH, SEARCH_PATTERN)).thenReturn(fileInfoList);
    assertThat(fileInfoList).isNotNull().isNotEmpty().hasSize(1);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldGetSmbConnectionHost() {
    assertThat(smbHelperService.getSMBConnectionHost(SHARE_URL)).isNotEmpty().isEqualTo("10.0.0.1");
    assertThat(smbHelperService.getSMBConnectionHost("smb://10.0.0.1/share")).isNotEmpty().isEqualTo("10.0.0.1");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldGetSmbSharedFolder() {
    assertThat(smbHelperService.getSharedFolderName(SHARE_URL)).isNotEmpty().isEqualTo("share");
    assertThat(smbHelperService.getSharedFolderName("smb://10.0.0.1/share")).isNotEmpty().isEqualTo("share");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldGetSmbArtifactDetails() throws IOException {
    // Mock SmbClient, Connection, DiskShare and Session
    doReturn(connection).when(smbClient).connect(smbHelperService.getSMBConnectionHost(smbConfig.getSmbUrl()));
    AuthenticationContext ac =
        new AuthenticationContext(smbConfig.getUsername(), smbConfig.getPassword(), smbConfig.getDomain());
    doReturn(session).when(connection).authenticate(ac);
    doReturn(diskShare).when(session).connectShare(SHARE_FOLDER);

    // List mock artifact build details
    List<BuildDetails> buildDetails = new ArrayList<>();
    buildDetails.add(BuildDetails.Builder.aBuildDetails().build());
    doReturn(buildDetails).when(mockSMBHelperService).getArtifactDetails(smbConfig, null, Collections.EMPTY_LIST);
    assertThat(buildDetails).isNotEmpty().hasSize(1);
  }
}
