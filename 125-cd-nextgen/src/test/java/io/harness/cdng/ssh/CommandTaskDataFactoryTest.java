/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static software.wings.beans.TaskType.COMMAND_TASK_NG;
import static software.wings.beans.TaskType.COMMAND_TASK_NG_WITH_AZURE_ARTIFACT;
import static software.wings.beans.TaskType.COMMAND_TASK_NG_WITH_AZURE_UNIVERSAL_PACKAGE_ARTIFACT;
import static software.wings.beans.TaskType.COMMAND_TASK_NG_WITH_GCS_ARTIFACT;
import static software.wings.beans.TaskType.COMMAND_TASK_NG_WITH_GITHUB_PACKAGE_ARTIFACT;
import static software.wings.beans.TaskType.COMMAND_TASK_NG_WITH_GIT_CONFIGS;
import static software.wings.beans.TaskType.COMMAND_TASK_NG_WITH_OUTPUT_VARIABLE_SECRETS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.storeconfig.GitFetchedStoreDelegateConfig;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.AwsS3ArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.CustomArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.GithubPackagesArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.GoogleCloudStorageArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.JenkinsArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.NexusArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.NexusDockerArtifactDelegateConfig;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class CommandTaskDataFactoryTest extends CategoryTest {
  @InjectMocks private CommandTaskDataFactory commandTaskDataFactory;

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testCreateTaskData_AZURE() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .artifactDelegateConfig(AzureArtifactDelegateConfig.builder().build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG_WITH_AZURE_ARTIFACT.name());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testCreateTaskData_AZURE_UPACK() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .artifactDelegateConfig(AzureArtifactDelegateConfig.builder().packageType("upack").build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG_WITH_AZURE_UNIVERSAL_PACKAGE_ARTIFACT.name());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testCreateTaskData_ARTIFACTORY() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .artifactDelegateConfig(ArtifactoryArtifactDelegateConfig.builder().build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG.name());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testCreateTaskData_JENKINS() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .artifactDelegateConfig(JenkinsArtifactDelegateConfig.builder().build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG.name());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testCreateTaskData_CUSTOM_ARTIFACT() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .artifactDelegateConfig(CustomArtifactDelegateConfig.builder().build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG.name());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testCreateTaskData_NEXUS() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .artifactDelegateConfig(NexusDockerArtifactDelegateConfig.builder().build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG.name());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testCreateTaskData_AWS_S3() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .artifactDelegateConfig(AwsS3ArtifactDelegateConfig.builder().build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG.name());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testCreateTaskData_NEXUS_PACKAGE() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .artifactDelegateConfig(NexusArtifactDelegateConfig.builder().build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG.name());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testCreateTaskData_WITH_GIT_CONFIGS() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .fileDelegateConfig(FileDelegateConfig.builder()
                                    .stores(Arrays.asList(GitFetchedStoreDelegateConfig.builder().build()))
                                    .build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG_WITH_GIT_CONFIGS.name());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateTaskData_WITH_OUTPUT_VARIABLE_SECRETS() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .fileDelegateConfig(
                FileDelegateConfig.builder().stores(List.of(GitFetchedStoreDelegateConfig.builder().build())).build())
            .secretOutputVariables(List.of("secret-var-to-collect-on-delegate"))
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG_WITH_OUTPUT_VARIABLE_SECRETS.name());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testCreateTaskData_WITH_GITHUB_PACKAGE_ARTIFACT() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .artifactDelegateConfig(GithubPackagesArtifactDelegateConfig.builder().build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG_WITH_GITHUB_PACKAGE_ARTIFACT.name());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCreateCommandTaskWithGcs() {
    SshCommandTaskParameters sshCommandTaskParameters =
        SshCommandTaskParameters.builder()
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .artifactDelegateConfig(GoogleCloudStorageArtifactDelegateConfig.builder().build())
            .executeOnDelegate(false)
            .accountId("accountId")
            .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
                ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
            .build();
    TaskData taskData = commandTaskDataFactory.create(sshCommandTaskParameters, ParameterField.createValueField("5"));
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND_TASK_NG_WITH_GCS_ARTIFACT.name());
  }
}
