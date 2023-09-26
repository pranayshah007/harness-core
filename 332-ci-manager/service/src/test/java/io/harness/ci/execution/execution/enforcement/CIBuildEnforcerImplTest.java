/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution.enforcement;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.enforcement.CIBuildEnforcerImpl;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.ExecutionQueueLimitRepository;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CIBuildEnforcerImplTest extends CIExecutionTestBase {
  @InjectMocks private CIBuildEnforcerImpl ciBuildEnforcer;
  @Mock private QueueExecutionUtils queueExecutionUtils;
  @Mock private ExecutionQueueLimitRepository executionQueueLimitRepository;
  private static final String accountID = "accountID";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testShouldQueue() {
    Platform platform = Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build();
    Infrastructure infrastructure = HostedVmInfraYaml.builder()
                                        .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                                                  .platform(ParameterField.createValueField(platform))
                                                  .build())
                                        .build();
    when(queueExecutionUtils.getActiveExecutionsCount(
             accountID, List.of(Status.QUEUED.toString(), Status.RUNNING.toString())))
        .thenReturn(5L);
    when(queueExecutionUtils.getActiveMacExecutionsCount(
             accountID, List.of(Status.QUEUED.toString(), Status.RUNNING.toString())))
        .thenReturn(0L);
    doReturn(
        Optional.of(
            ExecutionQueueLimit.builder().accountIdentifier(accountID).totalExecLimit("4").macExecLimit("2").build()))
        .when(executionQueueLimitRepository)
        .findFirstByAccountIdentifier(accountID);
    boolean actual = ciBuildEnforcer.shouldQueue(accountID, infrastructure);
    assertThat(actual).isTrue();

    platform = Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build();
    infrastructure = HostedVmInfraYaml.builder()
                         .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                                   .platform(ParameterField.createValueField(platform))
                                   .build())
                         .build();
    when(queueExecutionUtils.getActiveMacExecutionsCount(
             accountID, List.of(Status.QUEUED.toString(), Status.RUNNING.toString())))
        .thenReturn(3L);
    actual = ciBuildEnforcer.shouldQueue(accountID, infrastructure);
    assertThat(actual).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testShouldNotQueue() {
    Platform platform = Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build();
    Infrastructure infrastructure = HostedVmInfraYaml.builder()
                                        .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                                                  .platform(ParameterField.createValueField(platform))
                                                  .build())
                                        .build();
    when(queueExecutionUtils.getActiveExecutionsCount(
             accountID, List.of(Status.QUEUED.toString(), Status.RUNNING.toString())))
        .thenReturn(4L);
    when(queueExecutionUtils.getActiveMacExecutionsCount(
             accountID, List.of(Status.QUEUED.toString(), Status.RUNNING.toString())))
        .thenReturn(0L);
    doReturn(
        Optional.of(
            ExecutionQueueLimit.builder().accountIdentifier(accountID).totalExecLimit("4").macExecLimit("2").build()))
        .when(executionQueueLimitRepository)
        .findFirstByAccountIdentifier(accountID);
    boolean actual = ciBuildEnforcer.shouldQueue(accountID, infrastructure);
    assertThat(actual).isFalse();

    platform = Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build();
    infrastructure = HostedVmInfraYaml.builder()
                         .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                                   .platform(ParameterField.createValueField(platform))
                                   .build())
                         .build();
    when(queueExecutionUtils.getActiveMacExecutionsCount(
             accountID, List.of(Status.QUEUED.toString(), Status.RUNNING.toString())))
        .thenReturn(2L);
    actual = ciBuildEnforcer.shouldQueue(accountID, infrastructure);
    assertThat(actual).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testShouldRun() {
    Platform platform = Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build();
    Infrastructure infrastructure = HostedVmInfraYaml.builder()
                                        .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                                                  .platform(ParameterField.createValueField(platform))
                                                  .build())
                                        .build();
    when(queueExecutionUtils.getActiveExecutionsCount(accountID, List.of(Status.RUNNING.toString()))).thenReturn(3L);
    when(queueExecutionUtils.getActiveMacExecutionsCount(accountID, List.of(Status.RUNNING.toString()))).thenReturn(0L);
    doReturn(
        Optional.of(
            ExecutionQueueLimit.builder().accountIdentifier(accountID).totalExecLimit("4").macExecLimit("2").build()))
        .when(executionQueueLimitRepository)
        .findFirstByAccountIdentifier(accountID);
    boolean actual = ciBuildEnforcer.shouldRun(accountID, infrastructure);
    assertThat(actual).isTrue();

    platform = Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build();
    infrastructure = HostedVmInfraYaml.builder()
                         .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                                   .platform(ParameterField.createValueField(platform))
                                   .build())
                         .build();
    when(queueExecutionUtils.getActiveMacExecutionsCount(accountID, List.of(Status.RUNNING.toString()))).thenReturn(1L);
    actual = ciBuildEnforcer.shouldRun(accountID, infrastructure);
    assertThat(actual).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testShouldNotRun() {
    Platform platform = Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build();
    Infrastructure infrastructure = HostedVmInfraYaml.builder()
                                        .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                                                  .platform(ParameterField.createValueField(platform))
                                                  .build())
                                        .build();
    when(queueExecutionUtils.getActiveExecutionsCount(accountID, List.of(Status.RUNNING.toString()))).thenReturn(4L);
    when(queueExecutionUtils.getActiveMacExecutionsCount(accountID, List.of(Status.RUNNING.toString()))).thenReturn(0L);
    doReturn(
        Optional.of(
            ExecutionQueueLimit.builder().accountIdentifier(accountID).totalExecLimit("4").macExecLimit("2").build()))
        .when(executionQueueLimitRepository)
        .findFirstByAccountIdentifier(accountID);
    boolean actual = ciBuildEnforcer.shouldRun(accountID, infrastructure);
    assertThat(actual).isFalse();

    platform = Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build();
    infrastructure = HostedVmInfraYaml.builder()
                         .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                                   .platform(ParameterField.createValueField(platform))
                                   .build())
                         .build();
    when(queueExecutionUtils.getActiveMacExecutionsCount(accountID, List.of(Status.RUNNING.toString()))).thenReturn(2L);
    actual = ciBuildEnforcer.shouldRun(accountID, infrastructure);
    assertThat(actual).isFalse();
  }
}
