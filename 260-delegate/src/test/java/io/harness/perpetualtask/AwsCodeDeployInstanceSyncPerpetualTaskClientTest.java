/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CodeDeployInfrastructureMapping.CodeDeployInfrastructureMappingBuilder.aCodeDeployInfrastructureMapping;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.US_EAST;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.instancesync.AwsCodeDeployInstanceSyncPerpetualTaskParams;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInstanceFilter;
import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesRequest;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.amazonaws.services.ec2.model.Filter;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AwsCodeDeployInstanceSyncPerpetualTaskClientTest extends CategoryTest {
  @Mock private PerpetualTaskService perpetualTaskService;
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private SettingsService settingsService;
  @Mock private SecretManager secretManager;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private AwsUtils awsUtils;
  @InjectMocks AwsCodeDeployInstanceSyncPerpetualTaskClient client;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private KryoSerializer kryoSerializer;
  private CodeDeployInfrastructureMapping infraMapping;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    infraMapping = aCodeDeployInfrastructureMapping()
                       .withAccountId(ACCOUNT_ID)
                       .withAppId(APP_ID)
                       .withRegion(US_EAST)
                       .withComputeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
                       .withDeploymentType(DeploymentType.AWS_CODEDEPLOY.name())
                       .withServiceId(SERVICE_ID)
                       .withEnvId(ENV_ID)
                       .build();
    infraMapping.setDisplayName("infraName");
    AwsConfig awsConfig = AwsConfig.builder().accountId(ACCOUNT_ID).build();

    doReturn(infraMapping).when(infraMappingService).get(APP_ID, INFRA_MAPPING_ID);
    doReturn(aSettingAttribute().withValue(awsConfig).build()).when(settingsService).get(COMPUTE_PROVIDER_SETTING_ID);
    doReturn(emptyList()).when(secretManager).getEncryptionDetails(any(EncryptableSetting.class));
    doReturn(Application.Builder.anApplication().appId(APP_ID).accountId(ACCOUNT_ID).name(APP_NAME).build())
        .when(appService)
        .get(any());
    doReturn(DeploymentType.AWS_CODEDEPLOY)
        .when(serviceResourceService)
        .getDeploymentType(infraMapping, null, SERVICE_ID);
    doReturn(Service.builder().appId(APP_ID).accountId(ACCOUNT_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build())
        .when(serviceResourceService)
        .get(any(), any());
    doReturn(
        Environment.Builder.anEnvironment().accountId(ACCOUNT_ID).uuid(ENV_ID).appId(APP_ID).name(ENV_NAME).build())
        .when(environmentService)
        .get(any(), any());
    when(kryoSerializer.asBytes(any()))
        .thenAnswer(invocationOnMock -> String.valueOf(invocationOnMock.hashCode()).getBytes());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetValidationTask() {
    List<Filter> ec2Filters = singletonList(new Filter("instance-state", singletonList("running")));
    doReturn(ec2Filters).when(awsUtils).getFilters(eq(DeploymentType.AWS_CODEDEPLOY), any());
    DelegateTask validationTask = client.getValidationTask(getClientContext(), ACCOUNT_ID);

    verify(infraMappingService, times(1)).get(APP_ID, INFRA_MAPPING_ID);
    verify(settingsService, times(1)).get(COMPUTE_PROVIDER_SETTING_ID);
    verify(secretManager, times(1)).getEncryptionDetails(any());

    assertThat(validationTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(validationTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(GLOBAL_APP_ID);
    assertThat(validationTask.getData().isAsync()).isFalse();
    assertThat(validationTask.getData().getParameters()).isNotEmpty();
    assertThat(validationTask.getData().getParameters()[0]).isInstanceOf(AwsEc2ListInstancesRequest.class);
    AwsEc2ListInstancesRequest awsRequest = (AwsEc2ListInstancesRequest) validationTask.getData().getParameters()[0];
    assertThat(awsRequest.getRegion()).isEqualTo(US_EAST);
    assertThat(awsRequest.getAwsConfig().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(awsRequest.getEncryptionDetails()).isNotNull();
    assertThat(awsRequest.getFilters()).isEqualTo(ec2Filters);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetTaskParams() {
    List<Filter> ec2Filters = singletonList(new Filter("instance-state", singletonList("running")));
    doReturn(ec2Filters).when(awsUtils).getFilters(eq(DeploymentType.AWS_CODEDEPLOY), any(AwsInstanceFilter.class));
    AwsCodeDeployInstanceSyncPerpetualTaskParams taskParams =
        (AwsCodeDeployInstanceSyncPerpetualTaskParams) client.getTaskParams(getClientContext());

    assertThat(taskParams.getFilter()).isNotNull();
    assertThat(taskParams.getRegion()).isEqualTo(US_EAST);
    assertThat(taskParams.getAwsConfig()).isNotNull();
    assertThat(taskParams.getEncryptedData()).isNotNull();
  }

  private PerpetualTaskClientContext getClientContext() {
    return PerpetualTaskClientContext.builder()
        .clientParams(ImmutableMap.of(INFRASTRUCTURE_MAPPING_ID, INFRA_MAPPING_ID, HARNESS_APPLICATION_ID, APP_ID))
        .build();
  }
}
