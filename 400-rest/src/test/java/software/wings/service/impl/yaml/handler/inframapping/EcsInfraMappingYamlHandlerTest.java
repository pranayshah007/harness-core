/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.inframapping;

import static io.harness.exception.WingsException.ReportTarget.REST_API;
import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.beans.EcsInfrastructureMapping.Builder.anEcsInfrastructureMapping;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.scheduler.PersistentScheduler;
import io.harness.yaml.BaseYaml;

import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping.Yaml;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.utils.ArtifactType;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.amazonaws.services.ecs.model.LaunchType;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.Key;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class EcsInfraMappingYamlHandlerTest extends YamlHandlerTestBase {
  @Mock protected SettingsService settingsService;
  @Mock protected ServiceResourceService serviceResourceService;
  @Mock protected ServiceTemplateService serviceTemplateService;
  @Mock protected AppService appService;
  @Mock protected EnvironmentService environmentService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private ContainerService containerService;
  @Mock @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;
  @Mock private YamlDirectoryService yamlDirectoryService;

  @InjectMocks @Inject protected YamlHelper yamlHelper;
  @InjectMocks @Inject protected InfrastructureMappingService infrastructureMappingService;
  @InjectMocks @Inject protected EcsInfraMappingYamlHandler yamlHandler;

  private String validYamlContent1 = "harnessApiVersion: '1.0'\n"
      + "type: AWS_ECS\n"
      + "assignPublicIp: false\n"
      + "cluster: ec2ecs\n"
      + "computeProviderName: ecs-infra\n"
      + "computeProviderType: AWS\n"
      + "deploymentType: ECS\n"
      + "infraMappingType: AWS_ECS\n"
      + "launchType: EC2\n"
      + "region: us-east-1\n"
      + "serviceName: dockersvc";

  private String validYamlContent2 = "harnessApiVersion: '1.0'\n"
      + "type: AWS_ECS\n"
      + "assignPublicIp: true\n"
      + "cluster: ABfargate\n"
      + "computeProviderName: ecs-infra\n"
      + "computeProviderType: AWS\n"
      + "deploymentType: ECS\n"
      + "infraMappingType: AWS_ECS\n"
      + "launchType: FARGATE\n"
      + "region: us-east-1\n"
      + "securityGroupIds: sg-4e8f0c38\n"
      + "serviceName: dockersvc\n"
      + "subnetIds: subnet-2fa27920,subnet-ad9b92c9\n"
      + "vpcId: vpc-bfff4dc4";

  private String invalidYamlContent = "InvalidharnessApiVersion: '1.0'\n"
      + "type: AWS_ECS\n"
      + "vpcId: vpc-bfff4dc4";

  private String validYamlFilePath =
      "Setup/Applications/APP_NAME/Environments/ENVIRONMENT_NAME/Service Infrastructure/ecs.yaml";
  private String infraMappingName = "ecs";
  private String serviceName = "dockersvc";
  private String computeProviderName = "ecs-infra";
  private ServiceTemplate serviceTemplate =
      ServiceTemplate.Builder.aServiceTemplate().withUuid("uuid").withName("name").build();
  private SettingAttribute settingAttribute = getSettingAttribute();

  @Before
  public void runBeforeTest() {
    setup();
  }

  private void setup() {
    MockitoAnnotations.initMocks(this);

    when(settingsService.getByName(anyString(), anyString(), anyString())).thenReturn(settingAttribute);
    when(settingsService.get(anyString())).thenReturn(settingAttribute);
    when(appService.get(anyString())).thenReturn(getApplication());
    when(appService.getAppByName(anyString(), anyString())).thenReturn(getApplication());
    when(environmentService.getEnvironmentByName(anyString(), anyString())).thenReturn(getEnvironment());
    when(containerService.validate(any(), anyBoolean())).thenReturn(true);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(containerService);
    when(serviceResourceService.getServiceByName(anyString(), anyString())).thenReturn(getService());
    when(serviceResourceService.getWithDetails(anyString(), anyString())).thenReturn(getService());
    when(serviceTemplateService.getTemplateRefKeysByService(anyString(), anyString(), anyString()))
        .thenReturn(asList(new Key(ServiceTemplate.class, "serviceTemplates", SERVICE_ID)));
    when(serviceTemplateService.get(anyString(), anyString())).thenReturn(serviceTemplate);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void tbsValidateNetworkParameters() {
    Yaml yaml = Yaml.builder()
                    .assignPublicIp(true)
                    .cluster(CLUSTER_NAME)
                    .computeProviderName(COMPUTE_PROVIDER_ID)
                    .launchType(LaunchType.FARGATE.name())
                    .build();
    EcsInfrastructureMapping ecsInfrastructureMapping =
        anEcsInfrastructureMapping().withAppId(APP_ID).withName("name").build();

    try {
      EcsInfraMappingYamlHandler.validateNetworkParameters(yaml, ecsInfrastructureMapping);
      fail();
    } catch (Exception e) {
      WingsException wingsException = (WingsException) e;
      Assertions
          .assertThat("Invalid argument(s): Failed to parse yaml for EcsInfraMapping: name, App: " + APP_ID
              + ", For Fargate Launch type, VpcId  -  SubnetIds  - SecurityGroupIds are required,"
              + " can not be blank")
          .isEqualTo(ExceptionLogger.getResponseMessageList(wingsException, REST_API).get(0).getMessage());
    }
  }

  private Service getService() {
    return Service.builder().name(serviceName).appId(APP_ID).uuid(SERVICE_ID).artifactType(ArtifactType.DOCKER).build();
  }

  private Environment getEnvironment() {
    return Environment.Builder.anEnvironment().uuid("ANY_UUID").name("ENV_NAME").build();
  }

  private Application getApplication() {
    return Application.Builder.anApplication().uuid("ANY_UUID").name(APP_NAME).accountId(ACCOUNT_ID).build();
  }

  private SettingAttribute getSettingAttribute() {
    return aSettingAttribute()
        .withName(computeProviderName)
        .withUuid(SETTING_ID)
        .withValue(
            AwsConfig.builder().accessKey(ACCESS_KEY.toCharArray()).secretKey(SECRET_KEY).accountId(ACCOUNT_ID).build())
        .build();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    // testCrud(validYamlContent1);
    testCrud(validYamlContent2);
  }

  private void testCrud(String validYamlContent) throws Exception {
    ChangeContext<Yaml> changeContext = getChangeContext(validYamlContent, validYamlFilePath, yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    EcsInfrastructureMapping ecsInfraMapping = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(ecsInfraMapping).isNotNull();
    assertThat(infraMappingName).isEqualTo(ecsInfraMapping.getName());

    Yaml yaml = yamlHandler.toYaml(ecsInfraMapping, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo(InfrastructureMappingType.AWS_ECS.name());

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    InfrastructureMapping infraMapping = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(infraMapping).isNotNull();
    assertThat(infraMappingName).isEqualTo(infraMapping.getName());

    yamlHandler.delete(changeContext);

    InfrastructureMapping deletedInfraMapping = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(deletedInfraMapping).isNull();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    ChangeContext<Yaml> changeContext = getChangeContext(invalidYamlContent, validYamlFilePath, yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(validYamlContent2, EcsInfrastructureMapping.Yaml.class);
    changeContext.setYaml(yamlObject);

    yamlObject = (Yaml) getYaml(invalidYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);
    thrown.expect(Exception.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
  }

  protected <Y extends BaseYaml, H extends BaseYamlHandler> ChangeContext<Y> getChangeContext(
      String yamlContent, String yamlFilePath, H yamlHandler) {
    // Invalid yaml path
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(ACCOUNT_ID)
                                      .withFilePath(yamlFilePath)
                                      .withFileContent(yamlContent)
                                      .build();

    ChangeContext<Y> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.INFRA_MAPPING);
    changeContext.setYamlSyncHandler(yamlHandler);
    return changeContext;
  }
}
