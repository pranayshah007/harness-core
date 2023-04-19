/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.gitdiff;

import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.yaml.GitDiffResult;
import software.wings.service.impl.yaml.gitdiff.gitaudit.YamlAuditRecordGenerationUtils;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.sync.GitSyncService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class GitChangeSetProcesserTest extends CategoryTest {
  public static final String ACCOUNTID = "accountid";
  @Mock private GitChangeSetHandler gitChangesToEntityConverter;
  @Mock private YamlAuditRecordGenerationUtils gitChangeAuditRecordHandler;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private YamlGitService yamlGitService;
  @InjectMocks GitChangeSetProcesser gitChangeSetProcesser;
  @Mock private GitSyncService gitSyncService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void processGitChangeSet() {
    final GitDiffResult gitDiffResult = GitDiffResult.builder().build();
    gitChangeSetProcesser.processGitChangeSet(ACCOUNTID, gitDiffResult);
    verify(yamlGitService, Mockito.times(1)).isCommitAlreadyProcessed(eq(ACCOUNTID), any());
    verify(gitChangeAuditRecordHandler, times(1)).processGitChangesForAudit(ACCOUNTID, gitDiffResult);
    verify(gitChangesToEntityConverter, times(1)).ingestGitYamlChangs(ACCOUNTID, gitDiffResult);
    verify(gitChangeAuditRecordHandler, times(1)).finalizeAuditRecord(eq(ACCOUNTID), any(), anyMap());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void processGitChangeSet_commitalreadyprocessed() {
    final GitDiffResult gitDiffResult = GitDiffResult.builder().build();
    doReturn(true).when(yamlGitService).isCommitAlreadyProcessed(any(), any());
    gitChangeSetProcesser.processGitChangeSet(ACCOUNTID, gitDiffResult);
    verify(yamlGitService, Mockito.times(1)).isCommitAlreadyProcessed(eq(ACCOUNTID), any());
    verify(gitChangeAuditRecordHandler, times(0)).processGitChangesForAudit(ACCOUNTID, gitDiffResult);
    verify(gitChangesToEntityConverter, times(0)).ingestGitYamlChangs(ACCOUNTID, gitDiffResult);
    verify(gitChangeAuditRecordHandler, times(0)).finalizeAuditRecord(eq(ACCOUNTID), any(), anyMap());
  }
}
