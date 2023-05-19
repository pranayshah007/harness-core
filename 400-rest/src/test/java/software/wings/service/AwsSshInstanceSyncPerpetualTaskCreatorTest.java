/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PERPETUAL_TASK_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.Application;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.service.impl.instance.InstanceSyncTestConstants;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.util.Durations;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AwsSshInstanceSyncPerpetualTaskCreatorTest extends CategoryTest {
  @Mock private PerpetualTaskService perpetualTaskService;
  @InjectMocks private AwsSshInstanceSyncPerpetualTaskCreator perpetualTaskController;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceResourceService serviceResourceService;
  private AwsInfrastructureMapping infrastructureMapping;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(PERPETUAL_TASK_ID)
        .when(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.AWS_SSH_INSTANCE_SYNC), anyString(), any(), any(), eq(false), eq(""));
    infrastructureMapping = getInfraMapping();
    when(environmentService.get(any(), any()))
        .thenReturn(Environment.Builder.anEnvironment().accountId(ACCOUNT_ID).appId(APP_ID).name(ENV_NAME).build());
    when(serviceResourceService.get(any(), any()))
        .thenReturn(Service.builder().accountId(ACCOUNT_ID).appId(APP_ID).name(SERVICE_NAME).build());
    when(appService.get(any()))
        .thenReturn(Application.Builder.anApplication().appId(APP_ID).accountId(ACCOUNT_ID).name(APP_NAME).build());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void createPerpetualTasks() {
    perpetualTaskController.createPerpetualTasks(getInfraMapping());
    verifyCreatePerpetualTaskInternal();
  }

  private AwsInfrastructureMapping getInfraMapping() {
    AwsInfrastructureMapping infrastructureMapping = new AwsInfrastructureMapping();
    infrastructureMapping.setAccountId(ACCOUNT_ID);
    infrastructureMapping.setAppId(APP_ID);
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    infrastructureMapping.setDisplayName("infraName");
    infrastructureMapping.setAccountId(ACCOUNT_ID);
    return infrastructureMapping;
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void createPerpetualTasksForNewDeployment() {
    perpetualTaskController.createPerpetualTasksForNewDeployment(
        Collections.emptyList(), Collections.emptyList(), infrastructureMapping);

    verifyCreatePerpetualTaskInternal();
  }

  private void verifyCreatePerpetualTaskInternal() {
    verify(perpetualTaskService, Mockito.times(1))
        .createTask(eq(PerpetualTaskType.AWS_SSH_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID),
            eq(PerpetualTaskClientContext.builder()
                    .clientParams(ImmutableMap.of(InstanceSyncConstants.HARNESS_APPLICATION_ID, APP_ID,
                        InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID, INFRA_MAPPING_ID))
                    .build()),
            eq(PerpetualTaskSchedule.newBuilder()
                    .setInterval(Durations.fromMinutes(InstanceSyncConstants.INTERVAL_MINUTES))
                    .setTimeout(Durations.fromSeconds(InstanceSyncConstants.TIMEOUT_SECONDS))
                    .build()),
            eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));
  }
}
