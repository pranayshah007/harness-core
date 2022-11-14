/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.event.handler.impl.Constants.ACCOUNT_ID;
import static io.harness.rule.OwnerRule.ABHINAV;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.common.TemplateConstants.SSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.EntityType;
import software.wings.beans.template.BaseTemplate;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.beans.yaml.GitFileChange;
import software.wings.service.intfc.template.TemplateFolderService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.errorhandling.GitToHarnessErrorDetails;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class YamlGitServiceTest extends WingsBaseTest {
  @Inject private HPersistence persistence;
  @Inject private TemplateService templateService;
  @InjectMocks @Inject private YamlGitServiceImpl yamlGitService;
  @Inject protected TemplateGalleryService templateGalleryService;
  @Inject private TemplateFolderService templateFolderService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    templateGalleryService.loadHarnessGallery();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testSyncForTemplates() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder rootLevelFolder =
        templateFolderService.getRootLevelFolder(GLOBAL_ACCOUNT_ID, templateGallery.getUuid());

    TemplateFolder templateFolder = TemplateFolder.builder()
                                        .appId(GLOBAL_APP_ID)
                                        .accountId(GLOBAL_ACCOUNT_ID)
                                        .parentId(rootLevelFolder.getUuid())
                                        .name("test folder")
                                        .build();
    templateFolder = templateFolderService.save(templateFolder, templateGallery.getUuid());

    BaseTemplate baseTemplate = SshCommandTemplate.builder().build();
    Template template = Template.builder()
                            .appId(GLOBAL_APP_ID)
                            .name("test template")
                            .uuid("uuidtemplate")
                            .version(1)
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .folderId(templateFolder.getUuid())
                            .type(SSH)
                            .templateObject(baseTemplate)
                            .build();
    templateService.save(template);

    YamlGitConfig yamlGitConfig = YamlGitConfig.builder()
                                      .accountId(GLOBAL_ACCOUNT_ID)
                                      .entityId(GLOBAL_ACCOUNT_ID)
                                      .enabled(true)
                                      .syncMode(YamlGitConfig.SyncMode.BOTH)
                                      .entityType(EntityType.ACCOUNT)
                                      .build();
    persistence.save(yamlGitConfig);
    yamlGitService.syncForTemplates(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID);
    YamlChangeSet yamlChangeSet = persistence.createQuery(YamlChangeSet.class).get();
    assertThat(yamlChangeSet).isNotNull();
    boolean isPresent = false;
    String filePath = "Setup/Template Library/" + rootLevelFolder.getName() + "/test folder/test template.yaml";
    for (GitFileChange gitFileChange : yamlChangeSet.getGitFileChanges()) {
      if (gitFileChange.getFilePath().equals(filePath)) {
        isPresent = true;
        break;
      }
    }
    assertThat(isPresent).isEqualTo(true);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testListGitSync() {
    String firstCommitName = "commit1";
    String secondCommitName = "commit2";
    String firstCommitUpdatedName = "updated-commit1";

    GitSyncError gitSyncError =
        GitSyncError.builder()
            .accountId(ACCOUNT_ID)
            .additionalErrorDetails(GitToHarnessErrorDetails.builder().gitCommitId(firstCommitName).build())
            .build();
    persistence.save(gitSyncError);
    persistence.save(
        GitSyncError.builder()
            .accountId(ACCOUNT_ID)
            .additionalErrorDetails(GitToHarnessErrorDetails.builder().gitCommitId(secondCommitName).build())
            .build());

    RestResponse<List<GitSyncError>> response = yamlGitService.listGitSyncErrors(ACCOUNT_ID);
    assertThat(((GitToHarnessErrorDetails) response.getResource().get(0).getAdditionalErrorDetails()).getGitCommitId())
        .isEqualTo(firstCommitName);
    assertThat(((GitToHarnessErrorDetails) response.getResource().get(1).getAdditionalErrorDetails()).getGitCommitId())
        .isEqualTo(secondCommitName);

    ((GitToHarnessErrorDetails) gitSyncError.getAdditionalErrorDetails()).setGitCommitId(firstCommitUpdatedName);
    persistence.save(gitSyncError);

    response = yamlGitService.listGitSyncErrors(ACCOUNT_ID);
    assertThat(((GitToHarnessErrorDetails) response.getResource().get(0).getAdditionalErrorDetails()).getGitCommitId())
        .isEqualTo(firstCommitUpdatedName);
    assertThat(((GitToHarnessErrorDetails) response.getResource().get(1).getAdditionalErrorDetails()).getGitCommitId())
        .isEqualTo(secondCommitName);
  }
}
