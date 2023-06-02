/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.ChangeType;
import io.harness.rule.Owner;

import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.BuildWorkflow;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowBuilder;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.yaml.GitFileChange;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.YamlHandlerFromBeanFactory;
import software.wings.service.impl.yaml.handler.workflow.WorkflowYamlHandler;
import software.wings.service.intfc.yaml.EntityUpdateService;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.SyncMode;
import software.wings.yaml.workflow.BasicWorkflowYaml;
import software.wings.yaml.workflow.BuildWorkflowYaml;

import com.google.inject.Inject;
import java.util.List;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class YamlChangeSetHelperTest extends CategoryTest {
  public static final String ACCOUNTID = "000111";
  public static final String OLD = "old";
  public static final String NEW = "new";
  private YamlGitConfig yamlGitConfig;
  @Mock private YamlChangeSetService yamlChangeSetService;
  @Mock private EntityUpdateService entityUpdateService;
  @Mock private YamlHandlerFactory yamlHandlerFactory;
  @Mock private YamlHandlerFromBeanFactory yamlHandlerFromBeanFactory;
  @Mock private YamlDirectoryService yamlDirectoryService;
  @Mock private YamlGitService yamlGitService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private WorkflowYamlHandler workflowYamlHandler;
  @Spy @InjectMocks @Inject private YamlChangeSetHelper yamlChangeSetHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    yamlGitConfig = YamlGitConfig.builder()
                        .accountId(ACCOUNTID)
                        .branchName("master")
                        .syncMode(SyncMode.BOTH)
                        .url("git.com")
                        .username("username")
                        .encryptedPassword("xxxxxx")
                        .webhookToken("token")
                        .build();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testRenameYamlChangeForInfraMapping() throws Exception {
    GitFileChange gitFileChangeForDelete = GitFileChange.Builder.aGitFileChange()
                                               .withChangeType(ChangeType.DELETE)
                                               .withAccountId(ACCOUNTID)
                                               .withFileContent(OLD)
                                               .build();
    GitFileChange gitFileChangeForADD = GitFileChange.Builder.aGitFileChange()
                                            .withChangeType(ChangeType.ADD)
                                            .withAccountId(ACCOUNTID)
                                            .withFileContent(NEW)
                                            .build();

    // Validate for InfrastructureMapping
    when(entityUpdateService.obtainEntityGitSyncFileChangeSet(anyString(), any(), any(), any()))
        .thenReturn(Lists.newArrayList(gitFileChangeForDelete))
        .thenReturn(Lists.newArrayList(gitFileChangeForADD));

    YamlChangeSet changeSet = YamlChangeSet.builder().build();
    changeSet.setUuid(USER_ID);
    when(yamlChangeSetService.saveChangeSet(any(), any(), any())).thenReturn(changeSet);

    when(yamlDirectoryService.weNeedToPushChanges(any(), any())).thenReturn(YamlGitConfig.builder().build());
    InfrastructureMapping oldValue =
        AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping().withName(OLD).build();
    InfrastructureMapping newValue =
        AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping().withName(NEW).build();

    yamlChangeSetHelper.entityUpdateYamlChange(ACCOUNTID, oldValue, newValue, true);
    ArgumentCaptor<List> gitFileChangesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(yamlChangeSetService).saveChangeSet(accountIdCaptor.capture(), gitFileChangesCaptor.capture(), any());

    assertThat(gitFileChangesCaptor.getValue()).hasSize(2);
    gitFileChangeForDelete = (GitFileChange) gitFileChangesCaptor.getValue().get(0);
    assertThat(gitFileChangeForDelete.getAccountId()).isEqualTo(ACCOUNTID);
    assertThat(gitFileChangeForDelete.getChangeType()).isEqualTo(ChangeType.DELETE);
    assertThat(gitFileChangeForDelete.getFileContent()).isEqualTo(OLD);

    gitFileChangeForADD = (GitFileChange) gitFileChangesCaptor.getValue().get(1);
    assertThat(gitFileChangeForADD.getAccountId()).isEqualTo(ACCOUNTID);
    assertThat(gitFileChangeForADD.getChangeType()).isEqualTo(ChangeType.ADD);
    assertThat(gitFileChangeForADD.getFileContent()).isEqualTo(NEW);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testUpdateYamlChangeForInfraMapping() throws Exception {
    GitFileChange gitFileChangeForModify = GitFileChange.Builder.aGitFileChange()
                                               .withChangeType(ChangeType.MODIFY)
                                               .withAccountId(ACCOUNTID)
                                               .withFileContent(OLD)
                                               .build();

    // Validate for InfrastructureMapping
    when(entityUpdateService.obtainEntityGitSyncFileChangeSet(anyString(), any(), any(), any()))
        .thenReturn(Lists.newArrayList(gitFileChangeForModify));

    YamlChangeSet changeSet = YamlChangeSet.builder().build();
    changeSet.setUuid(USER_ID);
    when(yamlChangeSetService.saveChangeSet(any(), any(), any())).thenReturn(changeSet);
    when(yamlDirectoryService.weNeedToPushChanges(any(), any())).thenReturn(YamlGitConfig.builder().build());
    InfrastructureMapping oldValue =
        AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping().withName(OLD).build();

    yamlChangeSetHelper.entityUpdateYamlChange(ACCOUNTID, oldValue, oldValue, false);
    ArgumentCaptor<List> fileChangesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(yamlChangeSetService).saveChangeSet(accountIdCaptor.capture(), fileChangesCaptor.capture(), any());

    assertThat(fileChangesCaptor.getValue()).hasSize(1);
    gitFileChangeForModify = (GitFileChange) fileChangesCaptor.getValue().get(0);
    assertThat(gitFileChangeForModify.getAccountId()).isEqualTo(ACCOUNTID);
    assertThat(gitFileChangeForModify.getChangeType()).isEqualTo(ChangeType.MODIFY);
    assertThat(gitFileChangeForModify.getFileContent()).isEqualTo(OLD);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testRenameYamlChangeForArtifactStream() throws Exception {
    GitFileChange gitFileChangeForDelete = GitFileChange.Builder.aGitFileChange()
                                               .withChangeType(ChangeType.DELETE)
                                               .withAccountId(ACCOUNTID)
                                               .withFileContent(OLD)
                                               .build();
    GitFileChange gitFileChangeForADD = GitFileChange.Builder.aGitFileChange()
                                            .withChangeType(ChangeType.ADD)
                                            .withAccountId(ACCOUNTID)
                                            .withFileContent(NEW)
                                            .build();

    // Validate for Artifact Stream
    when(entityUpdateService.obtainEntityGitSyncFileChangeSet(anyString(), any(), any(), any()))
        .thenReturn(Lists.newArrayList(gitFileChangeForDelete))
        .thenReturn(Lists.newArrayList(gitFileChangeForADD));
    when(yamlDirectoryService.weNeedToPushChanges(any(), any())).thenReturn(YamlGitConfig.builder().build());

    YamlChangeSet changeSet = YamlChangeSet.builder().build();
    changeSet.setUuid(USER_ID);
    when(yamlChangeSetService.saveChangeSet(any(), any(), any())).thenReturn(changeSet);

    ArtifactStream oldValue = new DockerArtifactStream();
    oldValue.setName(OLD);
    ArtifactStream newValue = new DockerArtifactStream();
    oldValue.setName(NEW);

    yamlChangeSetHelper.entityUpdateYamlChange(ACCOUNTID, oldValue, newValue, true);

    ArgumentCaptor<List> gitFileChangesCaptorForAS = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(yamlChangeSetService).saveChangeSet(accountIdCaptor.capture(), gitFileChangesCaptorForAS.capture(), any());

    assertThat(gitFileChangesCaptorForAS.getValue()).hasSize(2);
    gitFileChangeForDelete = (GitFileChange) gitFileChangesCaptorForAS.getValue().get(0);
    assertThat(gitFileChangeForDelete.getAccountId()).isEqualTo(ACCOUNTID);
    assertThat(gitFileChangeForDelete.getChangeType()).isEqualTo(ChangeType.DELETE);
    assertThat(gitFileChangeForDelete.getFileContent()).isEqualTo(OLD);

    gitFileChangeForADD = (GitFileChange) gitFileChangesCaptorForAS.getValue().get(1);
    assertThat(gitFileChangeForADD.getAccountId()).isEqualTo(ACCOUNTID);
    assertThat(gitFileChangeForADD.getChangeType()).isEqualTo(ChangeType.ADD);
    assertThat(gitFileChangeForADD.getFileContent()).isEqualTo(NEW);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testUpdateYamlChangeForArtifactStream() throws Exception {
    GitFileChange gitFileChangeForModify = GitFileChange.Builder.aGitFileChange()
                                               .withChangeType(ChangeType.MODIFY)
                                               .withAccountId(ACCOUNTID)
                                               .withFileContent(OLD)
                                               .build();

    // Validate for Artifact Stream
    when(entityUpdateService.obtainEntityGitSyncFileChangeSet(anyString(), any(), any(), any()))
        .thenReturn(Lists.newArrayList(gitFileChangeForModify));
    when(yamlDirectoryService.weNeedToPushChanges(any(), any())).thenReturn(YamlGitConfig.builder().build());
    when(yamlChangeSetService.saveChangeSet(any(), any(), any())).thenReturn(null);
    ArtifactStream oldValue = new DockerArtifactStream();
    oldValue.setName(OLD);
    yamlChangeSetHelper.entityUpdateYamlChange(ACCOUNTID, oldValue, oldValue, false);

    ArgumentCaptor<List> fileChangesCaptorForAS = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(yamlChangeSetService).saveChangeSet(accountIdCaptor.capture(), fileChangesCaptorForAS.capture(), any());

    assertThat(fileChangesCaptorForAS.getValue()).hasSize(1);
    gitFileChangeForModify = (GitFileChange) fileChangesCaptorForAS.getValue().get(0);
    assertThat(gitFileChangeForModify.getAccountId()).isEqualTo(ACCOUNTID);
    assertThat(gitFileChangeForModify.getChangeType()).isEqualTo(ChangeType.MODIFY);
    assertThat(gitFileChangeForModify.getFileContent()).isEqualTo(OLD);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_compareYaml() {
    OrchestrationWorkflow orchestrationWorkflow1 =
        BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow().build();
    orchestrationWorkflow1.setOrchestrationWorkflowType(OrchestrationWorkflowType.BASIC);
    Workflow workflow1 = WorkflowBuilder.aWorkflow()
                             .accountId(ACCOUNTID)
                             .appId(APP_ID)
                             .uuid("UUID1")
                             .orchestrationWorkflow(orchestrationWorkflow1)
                             .build();

    Workflow workflow2 = WorkflowBuilder.aWorkflow()
                             .accountId(ACCOUNTID)
                             .appId(APP_ID)
                             .uuid("UUID2")
                             .orchestrationWorkflow(orchestrationWorkflow1)
                             .build();

    when(yamlHandlerFromBeanFactory.getYamlHandler(any())).thenReturn(workflowYamlHandler);
    when(workflowYamlHandler.toYaml(eq(workflow1), any())).thenReturn(BasicWorkflowYaml.builder().build());
    when(workflowYamlHandler.toYaml(eq(workflow2), any())).thenReturn(BasicWorkflowYaml.builder().build());
    yamlChangeSetHelper.entityUpdateYamlChange(ACCOUNTID, workflow1, workflow2, false);
    verify(yamlChangeSetHelper, times(0)).entityYamlChangeSet(eq(ACCOUNTID), any(), any());

    OrchestrationWorkflow orchestrationWorkflow2 =
        BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow().build();
    orchestrationWorkflow2.setOrchestrationWorkflowType(OrchestrationWorkflowType.BUILD);
    Workflow workflow3 = WorkflowBuilder.aWorkflow()
                             .accountId(ACCOUNTID)
                             .appId(APP_ID)
                             .uuid("UUID3")
                             .orchestrationWorkflow(orchestrationWorkflow2)
                             .build();

    doReturn(BasicWorkflowYaml.builder().build()).when(workflowYamlHandler).toYaml(eq(workflow1), any());
    doReturn(BuildWorkflowYaml.builder().build()).when(workflowYamlHandler).toYaml(eq(workflow3), any());
    yamlChangeSetHelper.entityUpdateYamlChange(ACCOUNTID, workflow1, workflow3, false);
    verify(yamlChangeSetHelper, times(1)).entityYamlChangeSet(eq(ACCOUNTID), any(), any());
  }
}
