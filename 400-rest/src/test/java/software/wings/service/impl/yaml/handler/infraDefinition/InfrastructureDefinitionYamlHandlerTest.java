/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.infraDefinition;

import static io.harness.annotations.dev.HarnessModule._955_CG_YAML;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.beans.InfrastructureType;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.infra.InfraDefinitionTestConstants;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinitionYaml;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsAmiInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsEcsInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsInstanceInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsLambdaInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AzureInstanceInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AzureKubernetesServiceYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AzureVMSSInfraYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AzureWebAppInfraYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.CodeDeployInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.CustomInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.DirectKubernetesInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.GoogleKubernetesEngineYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.InfrastructureDefinitionYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.PcfInfraStructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.PhysicalInfraWinrmYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.PhysicalInfraYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeService;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(_955_CG_YAML)
public class InfrastructureDefinitionYamlHandlerTest extends YamlHandlerTestBase {
  @Mock private YamlHelper mockYamlHelper;
  @Mock private SettingsService mockSettingsService;
  @Mock private YamlHandlerFactory mockYamlHandlerFactory;
  @Mock private AppService appService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock private CustomDeploymentTypeService customDeploymentTypeService;

  @InjectMocks @Inject private InfrastructureDefinitionYamlHandler handler;
  @InjectMocks @Inject private AwsLambdaInfrastructureYamlHandler awsLambdaInfrastructureYamlHandler;
  @InjectMocks @Inject private AwsAmiInfrastructureYamlHandler awsAmiInfrastructureYamlHandler;
  @InjectMocks @Inject private AwsInstanceInfrastructureYamlHandler awsInstanceInfrastructureYamlHandler;
  @InjectMocks @Inject private AwsEcsInfrastructureYamlHandler awsEcsInfrastructureYamlHandler;
  @InjectMocks @Inject private AzureKubernetesServiceYamlHandler azureKubernetesServiceYamlHandler;
  @InjectMocks @Inject private AzureInstanceInfrastructureYamlHandler azureInstanceInfrastructureYamlHandler;
  @InjectMocks @Inject private GoogleKubernetesEngineYamlHandler googleKubernetesEngineYamlHandler;
  @InjectMocks @Inject private DirectKubernetesInfrastructureYamlHandler directKubernetesInfrastructureYamlHandler;
  @InjectMocks @Inject private CodeDeployInfrastructureYamlHandler codeDeployInfrastructureYamlHandler;
  @InjectMocks @Inject private PcfInfraStructureYamlHandler pcfInfraStructureYamlHandler;
  @InjectMocks @Inject private PhysicalInfraYamlHandler physicalInfraYamlHandler;
  @InjectMocks @Inject private PhysicalInfraWinrmYamlHandler physicalInfraWinrmYamlHandler;
  @InjectMocks @Inject private AzureVMSSInfraYamlHandler azureVMSSInfraYamlHandler;
  @InjectMocks @Inject private AzureWebAppInfraYamlHandler azureWebAppInfraYamlHandler;
  @InjectMocks @Inject private CustomInfrastructureYamlHandler customInfrastructureYamlHandler;

  private final String yamlFilePath = "Setup/Applications/APP_NAME/Environments/"
      + "ENV_NAME/Infrastructure Definitions/infra-def.yaml";
  private final String resourcePath = "400-rest/src/test/resources/infrastructureDefinitions";

  @UtilityClass
  private static class validYamlInfraStructureFiles {
    // Make sure that CloudProviderName is TEST_CLOUD_PROVIDER
    private static final String AWS_ECS = "aws_ecs.yaml";
    private static final String AWS_AMI = "aws_ami.yaml";
    private static final String AZURE_VMSS = "azure_vmss.yaml";
    private static final String AZURE_WEBAPP = "azure_webapp.yaml";
    private static final String AWS_ECS_PROVISIONER = "aws_ecs_provisioner.yaml";
    private static final String AWS_LAMBDA = "aws_lambda.yaml";
    private static final String AWS_LAMBDA_PROVISIONER = "aws_lambda_provisioner.yaml";
    private static final String GCP_KUBERNETES = "gcp_kubernetes.yaml";
    private static final String AZURE_KUBERNETES = "azure_kubernetes.yaml";
    private static final String AZURE_INSTANCE = "azure_instance.yaml";
    private static final String DIRECT_KUBERNETES = "direct_kubernetes.yaml";
    private static final String AWS_INSTANCE = "aws_instance.yaml";
    private static final String AWS_INSTANCE_GROUP = "aws_instance_group.yaml";
    private static final String AWS_INSTANCE_PROVISIONER = "aws_instance_provisioner.yaml";
    private static final String AWS_CODEDEPLOY = "aws_codedeploy.yaml";
    private static final String GCP_KUBERNETES_PROVISIONER = "gcp_kubernetes_provisioner.yaml";
    private static final String PCF = "pcf.yaml";
    private static final String PHYSICAL_INFRA = "physical_infra.yaml";
    private static final String PHYSICAL_INFRA_WINRM = "physical_infra_winrm.yaml";
    private static final String PHYSICAL_INFRA_PROVISIONER = "physicalInfra_provisioner.yaml";
    private static final String DIRECT_KUBERNETES_PROVISIONER = "direct_kubernetes_provisioner.yaml";
    private static final String CUSTOM_INFRA = "custom_infra.yaml";
  }
  private ArgumentCaptor<InfrastructureDefinition> captor = ArgumentCaptor.forClass(InfrastructureDefinition.class);

  @Before
  public void setup() {
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withName("TEST_CLOUD_PROVIDER").build();
    Service service = Service.builder().name("httpd").uuid(SERVICE_ID).build();
    TerraformInfrastructureProvisioner infrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder()
            .uuid(InfraDefinitionTestConstants.INFRA_PROVISIONER_ID)
            .name(InfraDefinitionTestConstants.INFRA_PROVISIONER)
            .build();
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(nullable(String.class));
    doReturn(APP_ID).when(mockYamlHelper).getAppId(nullable(String.class), nullable(String.class));
    doReturn(ENV_ID).when(mockYamlHelper).getEnvironmentId(nullable(String.class), nullable(String.class));
    doReturn(settingAttribute)
        .doReturn(settingAttribute)
        .when(mockSettingsService)
        .getSettingAttributeByName(nullable(String.class), nullable(String.class));
    doReturn(settingAttribute).doReturn(settingAttribute).when(mockSettingsService).get(nullable(String.class));
    doReturn("infra-def")
        .when(mockYamlHelper)
        .extractEntityNameFromYamlPath(nullable(String.class), nullable(String.class), nullable(String.class));
    doReturn(service).when(serviceResourceService).get(nullable(String.class), nullable(String.class));
    doReturn(service).when(serviceResourceService).getServiceByName(nullable(String.class), nullable(String.class));
    doReturn(Optional.of(anApplication().uuid(APP_ID).build()))
        .when(mockYamlHelper)
        .getApplicationIfPresent(nullable(String.class), nullable(String.class));
    doReturn(Optional.of(anEnvironment().uuid(ENV_ID).build()))
        .when(mockYamlHelper)
        .getEnvIfPresent(nullable(String.class), nullable(String.class));
    doReturn(infrastructureProvisioner)
        .when(infrastructureProvisionerService)
        .get(nullable(String.class), nullable(String.class));
    doReturn(infrastructureProvisioner)
        .when(infrastructureProvisionerService)
        .getByName(nullable(String.class), nullable(String.class));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCRUDAndGet_AWS_LAMBDA() throws IOException {
    doReturn(awsLambdaInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.AWS_LAMBDA, InfrastructureType.AWS_LAMBDA, DeploymentType.AWS_LAMBDA,
        CloudProviderType.AWS);
    testCRUD(validYamlInfraStructureFiles.AWS_LAMBDA_PROVISIONER, InfrastructureType.AWS_LAMBDA,
        DeploymentType.AWS_LAMBDA, CloudProviderType.AWS);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testCRUDAndGet_AWS_ECS() throws IOException {
    doReturn(awsEcsInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(
        validYamlInfraStructureFiles.AWS_ECS, InfrastructureType.AWS_ECS, DeploymentType.ECS, CloudProviderType.AWS);
    testCRUD(validYamlInfraStructureFiles.AWS_ECS_PROVISIONER, InfrastructureType.AWS_ECS, DeploymentType.ECS,
        CloudProviderType.AWS);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCRUDAndGet_GCP_KUBERNETES() throws IOException {
    doReturn(googleKubernetesEngineYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.GCP_KUBERNETES, InfrastructureType.GCP_KUBERNETES_ENGINE,
        DeploymentType.KUBERNETES, CloudProviderType.GCP);
    testCRUD(validYamlInfraStructureFiles.GCP_KUBERNETES_PROVISIONER, InfrastructureType.GCP_KUBERNETES_ENGINE,
        DeploymentType.KUBERNETES, CloudProviderType.GCP);
  }
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCRUDAndGet_AZURE_KUBERNETES() throws IOException {
    doReturn(azureKubernetesServiceYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.AZURE_KUBERNETES, InfrastructureType.AZURE_KUBERNETES,
        DeploymentType.KUBERNETES, CloudProviderType.AZURE);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void TestCRUDAndGet_AZURE_SSH() throws IOException {
    doReturn(azureInstanceInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.AZURE_INSTANCE, InfrastructureType.AZURE_SSH, DeploymentType.SSH,
        CloudProviderType.AZURE);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void TestCRUDAndGet_DIRECT_KUBERNETES() throws IOException {
    doReturn(directKubernetesInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.DIRECT_KUBERNETES, InfrastructureType.DIRECT_KUBERNETES,
        DeploymentType.KUBERNETES, CloudProviderType.KUBERNETES_CLUSTER);
    testCRUD(validYamlInfraStructureFiles.DIRECT_KUBERNETES_PROVISIONER, InfrastructureType.DIRECT_KUBERNETES,
        DeploymentType.KUBERNETES, CloudProviderType.KUBERNETES_CLUSTER);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void TestCRUDAndGet_AWS_INSTANCE() throws IOException {
    doReturn(awsInstanceInfrastructureYamlHandler)
        .when(mockYamlHandlerFactory)
        .getYamlHandler(any(), nullable(String.class));
    testCRUD(validYamlInfraStructureFiles.AWS_INSTANCE, InfrastructureType.AWS_INSTANCE, DeploymentType.WINRM,
        CloudProviderType.AWS);
    testCRUD(validYamlInfraStructureFiles.AWS_INSTANCE_PROVISIONER, InfrastructureType.AWS_INSTANCE, DeploymentType.SSH,
        CloudProviderType.AWS);
    testCRUD(validYamlInfraStructureFiles.AWS_INSTANCE_GROUP, InfrastructureType.AWS_INSTANCE, DeploymentType.SSH,
        CloudProviderType.AWS);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void TestCRUDAndGet_AWS_CODEDEPLOY() throws IOException {
    doReturn(codeDeployInfrastructureYamlHandler)
        .when(mockYamlHandlerFactory)
        .getYamlHandler(any(), nullable(String.class));
    testCRUD(validYamlInfraStructureFiles.AWS_CODEDEPLOY, InfrastructureType.CODE_DEPLOY, DeploymentType.AWS_CODEDEPLOY,
        CloudProviderType.AWS);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void TestCRUDAndGet_PCF() throws IOException {
    doReturn(pcfInfraStructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.PCF, InfrastructureType.PCF_INFRASTRUCTURE, DeploymentType.PCF,
        CloudProviderType.PCF);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void TestCRUDAndGet_PhysicalInfra() throws IOException {
    doReturn(physicalInfraYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.PHYSICAL_INFRA, InfrastructureType.PHYSICAL_INFRA, DeploymentType.SSH,
        CloudProviderType.PHYSICAL_DATA_CENTER);
    testCRUD(validYamlInfraStructureFiles.PHYSICAL_INFRA_PROVISIONER, InfrastructureType.PHYSICAL_INFRA,
        DeploymentType.SSH, CloudProviderType.PHYSICAL_DATA_CENTER);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void TestCRUDAndGet_PhysicalInfraWinrm() throws IOException {
    doReturn(physicalInfraWinrmYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.PHYSICAL_INFRA_WINRM, InfrastructureType.PHYSICAL_INFRA_WINRM,
        DeploymentType.WINRM, CloudProviderType.PHYSICAL_DATA_CENTER);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void TestCRUDAndGetAwsAMi() throws IOException {
    doReturn(awsAmiInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(
        validYamlInfraStructureFiles.AWS_AMI, InfrastructureType.AWS_AMI, DeploymentType.AMI, CloudProviderType.AWS);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCRUDAndGet_AzureWebApp() throws IOException {
    doReturn(azureWebAppInfraYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.AZURE_WEBAPP, InfrastructureType.AZURE_WEBAPP, DeploymentType.AZURE_WEBAPP,
        CloudProviderType.AZURE);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCRUDAndGet_AzureVMSS() throws IOException {
    doReturn(azureVMSSInfraYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.AZURE_VMSS, InfrastructureType.AZURE_VMSS, DeploymentType.AZURE_VMSS,
        CloudProviderType.AZURE);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCRUDAndGet_CustomInfra() throws IOException {
    doReturn("Harness/My-Deployment-Types/My-Webshphere")
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplateUri("templateId");
    doReturn("templateId")
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplateIdFromUri(nullable(String.class), eq("Harness/My-Deployment-Types/My-Webshphere"));
    doReturn(customInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.CUSTOM_INFRA, InfrastructureType.CUSTOM_INFRASTRUCTURE, DeploymentType.CUSTOM,
        CloudProviderType.CUSTOM);
  }

  private void testCRUD(String yamlFileName, String cloudProviderInfrastructureType, DeploymentType deploymentType,
      CloudProviderType cloudProviderType) throws IOException {
    doReturn(null)
        .when(mockYamlHelper)
        .getInfraDefinitionIdByAppIdYamlPath(nullable(String.class), nullable(String.class), nullable(String.class));
    File yamlFile = null;
    yamlFile = new File(resourcePath + PATH_DELIMITER + yamlFileName);

    assertThat(yamlFile).isNotNull();
    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<InfrastructureDefinitionYaml> changeContext = getChangeContext(yamlString);
    InfrastructureDefinitionYaml yaml =
        (InfrastructureDefinitionYaml) getYaml(yamlString, InfrastructureDefinitionYaml.class);
    changeContext.setYaml(yaml);

    handler.upsertFromYaml(changeContext, Arrays.asList(changeContext));

    verify(infrastructureDefinitionService).save(captor.capture(), any(boolean.class));
    InfrastructureDefinition savedDefinition = captor.getValue();

    doReturn(savedDefinition)
        .when(mockYamlHelper)
        .getInfraDefinitionIdByAppIdYamlPath(nullable(String.class), nullable(String.class), nullable(String.class));

    handler.upsertFromYaml(changeContext, Arrays.asList(changeContext));

    assertThat(savedDefinition).isNotNull();
    assertThat(cloudProviderType).isEqualTo(savedDefinition.getCloudProviderType());
    assertThat(deploymentType).isEqualTo(savedDefinition.getDeploymentType());
    assertThat(cloudProviderInfrastructureType).isEqualTo(savedDefinition.getInfrastructure().getInfrastructureType());

    yaml = handler.toYaml(savedDefinition, APP_ID);

    assertThat(yaml).isNotNull();
    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlString);

    doReturn(savedDefinition)
        .when(infrastructureDefinitionService)
        .getInfraDefByName(nullable(String.class), nullable(String.class), nullable(String.class));

    InfrastructureDefinition retrievedDefinition = handler.get(ACCOUNT_ID, yamlFilePath);

    assertThat(retrievedDefinition).isNotNull();
    assertThat(retrievedDefinition.getUuid()).isEqualTo(savedDefinition.getUuid());

    doReturn(savedDefinition)
        .when(mockYamlHelper)
        .getInfraDefinitionIdByAppIdYamlPath(nullable(String.class), nullable(String.class), nullable(String.class));

    handler.delete(changeContext);

    verify(infrastructureDefinitionService).deleteByYamlGit(nullable(String.class), nullable(String.class));

    reset(infrastructureDefinitionService);
  }

  private ChangeContext<InfrastructureDefinitionYaml> getChangeContext(String validYamlContent) {
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(ACCOUNT_ID)
                                      .withFilePath(yamlFilePath)
                                      .withFileContent(validYamlContent)
                                      .build();

    ChangeContext<InfrastructureDefinitionYaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.INFRA_DEFINITION);
    changeContext.setYamlSyncHandler(handler);
    return changeContext;
  }
}
