/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.perpetualtask.instancesync.CustomDeploymentInstanceSyncTaskParams;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.api.CustomDeploymentTypeInfo;
import software.wings.api.DeploymentSummary;
import software.wings.beans.shellscript.provisioner.ShellScriptProvisionParameters;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.service.InstanceSyncConstants;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.states.customdeployment.InstanceFetchState;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CustomDeploymentInstanceSyncClientTest extends WingsBaseTest {
  @Mock private DeploymentService deploymentService;
  @Mock private ManagerExpressionEvaluator expressionEvaluator;
  @Mock private ManagerDecryptionService managerDecryptionService;
  @Mock private SecretManager secretManager;
  @Mock private FeatureFlagService featureFlagService;
  @InjectMocks private CustomDeploymentInstanceSyncClient instanceSyncClient;

  @Before
  public void setUp() {
    doReturn(Optional.empty()).when(deploymentService).getWithInfraMappingId(ACCOUNT_ID, INFRA_MAPPING_ID);
    doAnswer(invocation -> invocation.getArgument(0, String.class))
        .when(expressionEvaluator)
        .substitute(anyString(), anyMap());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getTaskParams() {
    doReturn(Optional.of(DeploymentSummary.builder()
                             .deploymentInfo(CustomDeploymentTypeInfo.builder().instanceFetchScript("echo abc").build())
                             .build()))
        .when(deploymentService)
        .getWithInfraMappingId(ACCOUNT_ID, INFRA_MAPPING_ID);
    PerpetualTaskClientContext clientContext = buildPerpetualTaskClientContext();

    final CustomDeploymentInstanceSyncTaskParams taskParams =
        (CustomDeploymentInstanceSyncTaskParams) instanceSyncClient.getTaskParams(clientContext);

    assertThat(taskParams.getScript()).isEqualTo("echo abc");
    assertThat(taskParams.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(taskParams.getAppId()).isEqualTo(APP_ID);
    assertThat(taskParams.getOutputPathKey()).isEqualTo(InstanceFetchState.OUTPUT_PATH_KEY);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getTaskParamsIfDeploymentSummaryNotFound() {
    PerpetualTaskClientContext clientContext = buildPerpetualTaskClientContext();

    final CustomDeploymentInstanceSyncTaskParams taskParams =
        (CustomDeploymentInstanceSyncTaskParams) instanceSyncClient.getTaskParams(clientContext);

    assertThat(taskParams).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getValidationTask() {
    final DelegateTask validationTask =
        instanceSyncClient.getValidationTask(buildPerpetualTaskClientContext(), ACCOUNT_ID);

    assertThat(validationTask.getData().getParameters()[0]).isInstanceOf(ShellScriptProvisionParameters.class);
    assertThat(validationTask.getData().getTimeout())
        .isEqualTo(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES));
    assertThat(validationTask.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  private PerpetualTaskClientContext buildPerpetualTaskClientContext() {
    return PerpetualTaskClientContext.builder()
        .clientParams(ImmutableMap.<String, String>builder()
                          .put(InstanceSyncConstants.HARNESS_ACCOUNT_ID, ACCOUNT_ID)
                          .put(InstanceSyncConstants.HARNESS_APPLICATION_ID, APP_ID)
                          .put(InstanceSyncConstants.HARNESS_ENV_ID, ENV_ID)
                          .put(InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID, INFRA_MAPPING_ID)
                          .build())
        .build();
  }
}
