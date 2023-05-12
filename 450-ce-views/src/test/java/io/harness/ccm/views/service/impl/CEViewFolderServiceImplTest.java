/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.rule.OwnerRule.TRUNAPUSHPA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.dao.CEViewFolderDao;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CEViewFolderServiceImplTest extends CategoryTest {
  @InjectMocks @Inject private CEViewFolderServiceImpl ceViewFolderService;
  @Mock private CEViewDao ceViewDao;
  @Mock private CEViewFolderDao ceViewFolderDao;

  private static final String ACCOUNT_ID = "account_id";
  private static final String FOLDER_NAME = "folder_name";
  private static final String VIEW_NAME = "view_name";
  private static final String UUID = "uuid";
  private static final String UUID2 = "uuid2";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testSave() {
    doReturn(ceViewFolder()).when(ceViewFolderDao).save(any());
    CEViewFolder ceViewfolder = ceViewFolderDao.save(ceViewFolder());
    assertThat(ceViewfolder.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ceViewfolder.getName()).isEqualTo(FOLDER_NAME);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetFolderCount() {
    final long count = 1;
    doReturn(count).when(ceViewFolderDao).getNumberOfFolders(any());
    doReturn(count).when(ceViewFolderDao).getNumberOfFolders(any(), any());
    long countReturned = ceViewFolderService.numberOfFolders(ACCOUNT_ID);
    assertThat(countReturned).isEqualTo(count);
    countReturned = ceViewFolderService.numberOfFolders(ACCOUNT_ID, Collections.singletonList(UUID));
    assertThat(countReturned).isEqualTo(count);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetFolders() {
    final List<CEViewFolder> ceViewFolders = Collections.singletonList(ceViewFolder());
    doReturn(ceViewFolders).when(ceViewFolderDao).getFolders(any(), anyString());
    doReturn(ceViewFolders).when(ceViewFolderDao).getFolders(any(), anyList());
    List<CEViewFolder> ceViewFoldersReturned = ceViewFolderService.getFolders(ACCOUNT_ID, "");
    assertThat(ceViewFoldersReturned.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ceViewFoldersReturned.get(0).getName()).isEqualTo(FOLDER_NAME);
    ceViewFoldersReturned = ceViewFolderService.getFolders(ACCOUNT_ID, "");
    assertThat(ceViewFoldersReturned.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ceViewFoldersReturned.get(0).getName()).isEqualTo(FOLDER_NAME);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testCreateDefaultFolders() {
    doReturn(null).when(ceViewFolderDao).getDefaultFolder(any());
    doReturn(null).when(ceViewFolderDao).getSampleFolder(any());
    doReturn(UUID).when(ceViewFolderDao).createDefaultOrSampleFolder(any(), any());
    ceViewFolderService.createDefaultFolders(ACCOUNT_ID);
    verify(ceViewFolderDao, times(1)).getDefaultFolder(any());
    verify(ceViewFolderDao, times(1)).getSampleFolder(any());
    verify(ceViewFolderDao, times(2)).createDefaultOrSampleFolder(any(), any());
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testUpdateFolder() {
    doReturn(ceViewFolder()).when(ceViewFolderDao).updateFolder(any(), any());
    CEViewFolder ceViewFolderReturned = ceViewFolderService.updateFolder(ACCOUNT_ID, ceViewFolder());
    assertThat(ceViewFolderReturned.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ceViewFolderReturned.getUuid()).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testMoveMultipleCEViews() {
    doReturn(Collections.singletonList(ceView())).when(ceViewDao).moveMultiplePerspectiveFolder(any(), any(), any());
    List<CEView> ceViews = ceViewFolderService.moveMultipleCEViews(ACCOUNT_ID, Collections.singletonList(UUID), UUID);
    assertThat(ceViews.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(ceViews.get(0).getName()).isEqualTo(VIEW_NAME);
    assertThat(ceViews.get(0).getFolderId()).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testDelete() {
    doReturn(Collections.emptyList()).when(ceViewDao).findByAccountIdAndFolderId(any(), any(), any());
    doReturn(ceViewFolder()).when(ceViewFolderDao).getDefaultFolder(any());
    doReturn(Collections.emptyList()).when(ceViewDao).moveMultiplePerspectiveFolder(any(), any(), any());
    doReturn(true).when(ceViewFolderDao).delete(any(), any());
    boolean folderDeleted = ceViewFolderService.delete(ACCOUNT_ID, UUID2);
    assertThat(folderDeleted).isEqualTo(true);
  }

  private CEViewFolder ceViewFolder() {
    return CEViewFolder.builder().uuid(UUID).name(FOLDER_NAME).accountId(ACCOUNT_ID).pinned(false).build();
  }

  private CEView ceView() {
    return CEView.builder().uuid(UUID).name(VIEW_NAME).accountId(ACCOUNT_ID).folderId(UUID).build();
  }
}
