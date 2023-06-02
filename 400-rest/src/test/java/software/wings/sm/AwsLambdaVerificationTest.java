/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.AwsLambdaContextElement;
import software.wings.api.AwsLambdaFunctionElement;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsLambdaInfraStructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.WingsTestConstants;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class AwsLambdaVerificationTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SettingsService settingsService;
  @Mock private ActivityService activityService;
  @Mock private SecretManager secretManager;
  @Mock private DelegateService delegateService;
  @Mock private StateExecutionService stateExecutionService;

  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @InjectMocks private AwsLambdaVerification awsLambdaVerification;

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecute() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    when(context.fetchInfraMappingId()).thenReturn(WingsTestConstants.INFRA_MAPPING_ID);

    when(infrastructureMappingService.get(anyString(), anyString()))
        .thenReturn(AwsLambdaInfraStructureMapping.builder()
                        .uuid(INFRA_MAPPING_ID)
                        .appId(APP_ID)
                        .computeProviderSettingId(SETTING_ID)
                        .envId(ENV_ID)
                        .build());
    when(settingsService.get(SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(AwsConfig.builder().build()).build());

    Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();
    Application app = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();

    WorkflowStandardParams workflowStandardParams = mock(WorkflowStandardParams.class);
    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams)).thenReturn(app);
    when(workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams)).thenReturn(env);
    when(activityService.save(any())).thenReturn(Activity.builder().build());
    when(context.getContextElement(ContextElementType.AWS_LAMBDA_FUNCTION))
        .thenReturn(AwsLambdaFunctionElement.builder()
                        .functionArn(AwsLambdaContextElement.FunctionMeta.builder().build())
                        .build());
    when(context.getAppId()).thenReturn(APP_ID);
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());

    awsLambdaVerification.execute(context);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD))
        .isEqualTo(INFRA_MAPPING_ID);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() {
    assertThat(awsLambdaVerification.getTimeoutMillis()).isEqualTo(2 * 60 * 1000);
    assertThat(awsLambdaVerification.getTimeoutMillis(mock(ExecutionContextImpl.class))).isEqualTo(2 * 60 * 1000);
  }
}
