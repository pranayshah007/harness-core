/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.sync;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.event.handler.impl.Constants.ACCOUNT_ID;
import static io.harness.rule.OwnerRule.DEEPAK;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.SettingAttribute;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.SettingsService;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.gitSync.GitFileActivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitSyncRBACHelperTest extends CategoryTest {
  @Mock SettingsService settingService;
  @Mock YamlHelper yamlHelper;
  @InjectMocks GitSyncRBACHelper gitSyncRBACHelper;
  String filePath1 = "Setup/Cloud Providers/settingAttribute1.yaml";
  String filePath2 = "Setup/Cloud Providers/settingAttribute2.yaml";
  String filePath3 = "Setup/Template Library/Harness/HTTP Verifications/template1.yaml";

  private SettingAttribute settingAttribute1;
  private SettingAttribute settingAttribute2;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doCallRealMethod().when(yamlHelper).isAccountLevelEntity(any());
    doCallRealMethod().when(yamlHelper).getYamlTypeFromSettingAttributePath(any());
    doCallRealMethod().when(yamlHelper).extractEntityNameFromYamlPath(any(), any(), any());
    doCallRealMethod().when(yamlHelper).getYamlTypeOfAccountLevelEntity(any());
    doCallRealMethod().when(yamlHelper).getSettingAttributeCategoryFromYamlType(any());
    settingAttribute1 = aSettingAttribute()
                            .withAccountId(ACCOUNT_ID)
                            .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                            .withName("settingAttribute1")
                            .build();
    // Accessible To User
    settingAttribute2 = aSettingAttribute().withAccountId(ACCOUNT_ID).withName("settingAttribute2").build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void filterFileActivitiesAccessibleToUser() {
    GitFileActivity gitFileActivity1 = GitFileActivity.builder().accountId(ACCOUNT_ID).filePath(filePath1).build();
    GitFileActivity gitFileActivity2 = GitFileActivity.builder().accountId(ACCOUNT_ID).filePath(filePath2).build();
    GitFileActivity templateFileActivity = GitFileActivity.builder().accountId(ACCOUNT_ID).filePath(filePath3).build();
    List<GitFileActivity> gitFileActivityList = Arrays.asList(gitFileActivity1, gitFileActivity2, templateFileActivity);
    PageResponse<SettingAttribute> pageResponse =
        aPageResponse().withResponse(Collections.singletonList(settingAttribute1)).withTotal(1).build();
    doReturn(pageResponse).when(settingService).list(any(), any(), any(), anyBoolean());
    List<GitFileActivity> gitFileActivities =
        gitSyncRBACHelper.populateUserHasPermissionForFileField(gitFileActivityList, ACCOUNT_ID);
    assertThat(gitFileActivities.size()).isEqualTo(3);
    GitFileActivity gitFileActivityForSettingAttribute1 = null;
    GitFileActivity gitFileActivityForSettingAttribute2 = null;
    GitFileActivity gitFileActivityForSettingAttribute3 = null;
    for (GitFileActivity gitFileActivity : gitFileActivities) {
      if (gitFileActivity.getFilePath().equals(filePath1)) {
        gitFileActivityForSettingAttribute1 = gitFileActivity;
      }
      if (gitFileActivity.getFilePath().equals(filePath2)) {
        gitFileActivityForSettingAttribute2 = gitFileActivity;
      }
      if (gitFileActivity.getFilePath().equals(filePath3)) {
        gitFileActivityForSettingAttribute3 = gitFileActivity;
      }
    }
    assertThat(gitFileActivityForSettingAttribute1.isUserDoesNotHavePermForFile()).isEqualTo(false);
    assertThat(gitFileActivityForSettingAttribute2.isUserDoesNotHavePermForFile()).isEqualTo(true);
    assertThat(gitFileActivityForSettingAttribute3.isUserDoesNotHavePermForFile()).isEqualTo(false);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void filterErrorsAccessibleToUser() {
    GitSyncError gitSyncError1 = GitSyncError.builder().accountId(ACCOUNT_ID).yamlFilePath(filePath1).build();
    GitSyncError gitSyncError2 = GitSyncError.builder().accountId(ACCOUNT_ID).yamlFilePath(filePath2).build();
    GitSyncError gitSyncError3 = GitSyncError.builder().accountId(ACCOUNT_ID).yamlFilePath(filePath3).build();
    List<GitSyncError> gitSyncErrorList = Arrays.asList(gitSyncError1, gitSyncError2, gitSyncError3);
    PageResponse<SettingAttribute> pageResponse =
        aPageResponse().withResponse(Collections.singletonList(settingAttribute1)).withTotal(1).build();
    doReturn(pageResponse).when(settingService).list(any(), any(), any(), anyBoolean());
    List<GitSyncError> gitSyncErrors =
        gitSyncRBACHelper.populateUserHasPermissionForFileFieldInErrors(gitSyncErrorList, ACCOUNT_ID);
    assertThat(gitSyncErrors.size()).isEqualTo(3);
    GitSyncError gitSyncErrorForSettingAttribute1 = null;
    GitSyncError gitSyncErrorForSettingAttribute2 = null;
    GitSyncError gitSyncErrorForSettingAttribute3 = null;
    for (GitSyncError error : gitSyncErrors) {
      if (error.getYamlFilePath().equals(filePath1)) {
        gitSyncErrorForSettingAttribute1 = error;
      }
      if (error.getYamlFilePath().equals(filePath2)) {
        gitSyncErrorForSettingAttribute2 = error;
      }
      if (error.getYamlFilePath().equals(filePath3)) {
        gitSyncErrorForSettingAttribute3 = error;
      }
    }
    assertThat(gitSyncErrorForSettingAttribute1.isUserDoesNotHavePermForFile()).isEqualTo(false);
    assertThat(gitSyncErrorForSettingAttribute2.isUserDoesNotHavePermForFile()).isEqualTo(true);
    assertThat(gitSyncErrorForSettingAttribute3.isUserDoesNotHavePermForFile()).isEqualTo(false);
  }
}
