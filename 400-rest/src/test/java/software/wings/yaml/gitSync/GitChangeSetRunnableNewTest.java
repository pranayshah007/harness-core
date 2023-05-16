/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.yaml.gitSync.YamlChangeSet.Status.QUEUED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.yaml.YamlChangeSetServiceImpl;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class GitChangeSetRunnableNewTest extends WingsBaseTest {
  @Inject @Spy GitChangeSetRunnableHelper gitChangeSetRunnableHelper;
  @Inject YamlChangeSetServiceImpl yamlChangeSetService;
  @Mock private YamlGitService yamlGitSyncService;
  @Inject private HPersistence persistence;

  @Inject @Spy @InjectMocks private GitChangeSetRunnable gitChangeSetRunnable;

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_run() {
    doReturn(2).when(gitChangeSetRunnable).getMaxRunningChangesetsForAccount();
    final String webhookToken = "Webhook_Token";

    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withAccountId(ACCOUNT_ID)
            .withUuid(SETTING_ID)
            .withName("GitConnector")
            .withValue(GitConfig.builder().accountId(ACCOUNT_ID).webhookToken(webhookToken).build())
            .build();

    YamlGitConfig yamlGitConfig =
        YamlGitConfig.builder().accountId(ACCOUNT_ID).gitConnectorId(SETTING_ID).branchName("master").build();

    persistence.save(settingAttribute);
    persistence.save(yamlGitConfig);

    final YamlChangeSet fullSyncChangeSetQ1 =
        yamlChangeSetService.save(YamlChangeSet.builder()
                                      .fullSync(true)
                                      .status(QUEUED)
                                      .accountId(ACCOUNT_ID)
                                      .gitToHarness(false)
                                      .appId(GLOBAL_APP_ID)
                                      .gitFileChanges(Collections.emptyList())
                                      .queueKey("queuekey1")
                                      .gitSyncMetadata(GitSyncMetadata.builder().build())
                                      .build());

    final YamlChangeSet fullSyncChangeSet1Q1 =
        yamlChangeSetService.save(YamlChangeSet.builder()
                                      .fullSync(true)
                                      .status(QUEUED)
                                      .accountId(ACCOUNT_ID)
                                      .gitToHarness(false)
                                      .appId(GLOBAL_APP_ID)
                                      .gitFileChanges(Collections.emptyList())
                                      .queueKey("queuekey1")
                                      .gitSyncMetadata(GitSyncMetadata.builder().build())
                                      .build());

    final YamlChangeSet gtohChangesetQ2 =
        yamlChangeSetService.save(YamlChangeSet.builder()
                                      .fullSync(false)
                                      .status(QUEUED)
                                      .accountId(ACCOUNT_ID)
                                      .gitToHarness(true)
                                      .appId(GLOBAL_APP_ID)
                                      .gitFileChanges(Collections.emptyList())
                                      .queueKey("queuekey2")
                                      .gitSyncMetadata(GitSyncMetadata.builder().build())
                                      .build());

    final YamlChangeSet gtohChangesetQ3 =
        yamlChangeSetService.save(YamlChangeSet.builder()
                                      .fullSync(false)
                                      .status(QUEUED)
                                      .accountId(ACCOUNT_ID)
                                      .gitToHarness(true)
                                      .appId(GLOBAL_APP_ID)
                                      .gitFileChanges(Collections.emptyList())
                                      .queueKey("queuekey3")
                                      .gitSyncMetadata(GitSyncMetadata.builder().build())
                                      .build());

    doNothing().when(yamlGitSyncService).handleGitChangeSet(any(YamlChangeSet.class), eq(ACCOUNT_ID));
    doNothing().when(yamlGitSyncService).handleHarnessChangeSet(any(YamlChangeSet.class), eq(ACCOUNT_ID));

    gitChangeSetRunnable.run();

    verify(yamlGitSyncService, times(1)).handleHarnessChangeSet(any(YamlChangeSet.class), eq(ACCOUNT_ID));
    verify(yamlGitSyncService, times(1)).handleGitChangeSet(any(YamlChangeSet.class), eq(ACCOUNT_ID));
  }
}
