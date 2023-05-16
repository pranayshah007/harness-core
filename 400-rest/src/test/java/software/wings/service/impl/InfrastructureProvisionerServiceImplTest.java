/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.VALIDATE_PROVISIONER_EXPRESSION;
import static io.harness.rule.OwnerRule.AKHIL_PANDEY;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.NAVNEET;
import static io.harness.rule.OwnerRule.RIHAZ;
import static io.harness.rule.OwnerRule.SAINATH;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.CloudFormationSourceType.TEMPLATE_BODY;
import static software.wings.beans.CloudFormationSourceType.TEMPLATE_URL;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.PROVISIONER_NAME;
import static software.wings.utils.WingsTestConstants.S3_URI;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.TerraformExecutionData;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInstanceFilter;
import software.wings.beans.BlueprintProperty;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.EntityType;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingBlueprint;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.InfrastructureProvisionerDetails;
import software.wings.beans.KmsConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.TerraformInputVariablesTaskResponse;
import software.wings.beans.TerraformSourceType;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner;
import software.wings.dl.WingsPersistence;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.aws.delegate.AwsS3HelperServiceDelegateImpl;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.aws.manager.AwsCFHelperServiceManager;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.utils.GitUtilsManager;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.DBCursor;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class InfrastructureProvisionerServiceImplTest extends WingsBaseTest {
  @Mock WingsPersistence mockWingsPersistence;
  @Mock ExecutionContext executionContext;
  @Mock Query query;
  @Mock DBCursor dbCursor;
  @Mock MorphiaIterator infrastructureMappings;
  @Mock InfrastructureMappingService infrastructureMappingService;
  @Mock InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock AwsCFHelperServiceManager awsCFHelperServiceManager;
  @Mock ServiceResourceService serviceResourceService;
  @Mock SettingsService settingService;
  @Mock ResourceLookupService resourceLookupService;
  @Mock AppService appService;
  @Mock GitFileConfigHelperService gitFileConfigHelperService;
  @Mock GitUtilsManager gitUtilsManager;
  @Mock GitConfigHelperService gitConfigHelperService;
  @Mock DelegateService delegateService;
  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock SecretManager secretManager;
  @Mock FeatureFlagService featureFlagService;
  @Inject @InjectMocks InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject @InjectMocks InfrastructureProvisionerServiceImpl infrastructureProvisionerServiceImpl;
  @Inject private HPersistence persistence;
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsS3HelperServiceDelegateImpl awsS3HelperServiceDelegate;

  private String blankString = "     ";
  private static String SECRET_MANAGER_ID = "SECRET_MANAGER_ID";

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRegenerateInfrastructureMappings() throws Exception {
    InfrastructureProvisioner infrastructureProvisioner =
        CloudFormationInfrastructureProvisioner.builder().appId(APP_ID).uuid(ID_KEY).build();
    doReturn(infrastructureProvisioner)
        .when(mockWingsPersistence)
        .getWithAppId(eq(InfrastructureProvisioner.class), anyString(), anyString());
    doReturn(query).when(mockWingsPersistence).createQuery(eq(InfrastructureMapping.class));
    doReturn(query).doReturn(query).when(query).filter(anyString(), any());
    doReturn(infrastructureMappings).when(query).fetch();
    doReturn(new HashMap<>()).when(executionContext).asMap();

    doReturn(true).doReturn(true).doReturn(false).when(infrastructureMappings).hasNext();
    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn(WORKFLOW_EXECUTION_ID).when(executionContext).getWorkflowExecutionId();
    doReturn(PhaseElement.builder()
                 .infraDefinitionId(INFRA_DEFINITION_ID)
                 .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                 .build())
        .when(executionContext)
        .getContextElement(ContextElementType.PARAM, ExecutionContextImpl.PHASE_PARAM);

    InfrastructureMapping infrastructureMapping = anAwsInfrastructureMapping()
                                                      .withUuid(INFRA_MAPPING_ID)
                                                      .withAppId(APP_ID)
                                                      .withProvisionerId(ID_KEY)
                                                      .withServiceId(SERVICE_ID)
                                                      .withInfraMappingType(InfrastructureMappingType.AWS_SSH.name())
                                                      .build();
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .appId(APP_ID)
            .provisionerId(PROVISIONER_ID)
            .infrastructure(AwsInstanceInfrastructure.builder()
                                .expressions(ImmutableMap.of("region", "${cloudformation.myregion}", "vpcIds",
                                    "${cloudformation.myvpcs}", "tags", "${cloudformation.mytags}"))
                                .build())
            .build();
    doReturn(infrastructureDefinition).when(infrastructureDefinitionService).get(APP_ID, INFRA_DEFINITION_ID);
    doReturn(infrastructureMapping)
        .when(infrastructureDefinitionService)
        .saveInfrastructureMapping(SERVICE_ID, infrastructureDefinition, WORKFLOW_EXECUTION_ID);
    doReturn(infrastructureMapping).when(infrastructureMappings).next();
    doReturn(dbCursor).when(infrastructureMappings).getCursor();

    Map<String, Object> tagMap = new HashMap<>();
    tagMap.put("name", "mockName");
    Map<String, Object> objectMap = new HashMap<>();
    objectMap.put("myregion", "us-east-1");
    objectMap.put("myvpcs", "vpc1,vpc2,vpc3");
    objectMap.put("mytags", "name:mockName");

    doReturn(infrastructureMapping).when(infrastructureMappingService).update(any(), anyString());

    PageResponse<Service> response = new PageResponse<>();
    Service service = Service.builder().name("service1").uuid(SERVICE_ID).build();
    response.setResponse(singletonList(service));
    doReturn(response).when(serviceResourceService).list(any(), anyBoolean(), anyBoolean(), anyBoolean(), any());

    infrastructureProvisionerService.regenerateInfrastructureMappings(ID_KEY, executionContext, objectMap);

    ArgumentCaptor<InfrastructureMapping> captor = ArgumentCaptor.forClass(InfrastructureMapping.class);
    verify(infrastructureMappingService)
        .saveInfrastructureMappingToSweepingOutput(
            eq(APP_ID), eq(WORKFLOW_EXECUTION_ID), any(PhaseElement.class), eq(INFRA_MAPPING_ID));

    verify(infrastructureDefinitionService, times(1))
        .saveInfrastructureMapping(SERVICE_ID, infrastructureDefinition, WORKFLOW_EXECUTION_ID);
    AwsInstanceFilter awsInstanceFilter =
        ((AwsInstanceInfrastructure) infrastructureDefinition.getInfrastructure()).getAwsInstanceFilter();
    assertThat(awsInstanceFilter).isNotNull();
    assertThat(((AwsInstanceInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion())
        .isEqualTo("us-east-1");

    assertThat(awsInstanceFilter.getVpcIds()).isNotNull();
    assertThat(awsInstanceFilter.getVpcIds()).hasSize(3);
    assertThat(awsInstanceFilter.getVpcIds().contains("vpc1")).isTrue();
    assertThat(awsInstanceFilter.getVpcIds().contains("vpc2")).isTrue();
    assertThat(awsInstanceFilter.getVpcIds().contains("vpc3")).isTrue();

    assertThat(awsInstanceFilter.getTags()).isNotNull();
    assertThat(awsInstanceFilter.getTags()).hasSize(1);
    assertThat(awsInstanceFilter.getTags().get(0).getKey()).isEqualTo("name");
    assertThat(awsInstanceFilter.getTags().get(0).getValue()).isEqualTo("mockName");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetCFTemplateParamKeys() {
    String defaultString = "default";

    doReturn(asList())
        .when(awsCFHelperServiceManager)
        .getParamsData(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

    infrastructureProvisionerService.getCFTemplateParamKeys("GIT", defaultString, defaultString, defaultString,
        defaultString, defaultString, defaultString, defaultString, defaultString, true, null);
    assertThatThrownBy(
        ()
            -> infrastructureProvisionerService.getCFTemplateParamKeys("GIT", defaultString, defaultString,
                defaultString, defaultString, "", defaultString, defaultString, defaultString, true, null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        ()
            -> infrastructureProvisionerService.getCFTemplateParamKeys("GIT", defaultString, defaultString,
                defaultString, defaultString, defaultString, "", defaultString, "", true, null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        ()
            -> infrastructureProvisionerService.getCFTemplateParamKeys("TEMPLATE_BODY", defaultString, defaultString,
                "", defaultString, defaultString, defaultString, defaultString, defaultString, true, null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        ()
            -> infrastructureProvisionerService.getCFTemplateParamKeys("TEMPLATE_URL", defaultString, defaultString, "",
                defaultString, defaultString, defaultString, defaultString, defaultString, true, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldValidateInfrastructureProvisioner() {
    TerraformInfrastructureProvisioner terraformProvisioner = TerraformInfrastructureProvisioner.builder()
                                                                  .accountId(ACCOUNT_ID)
                                                                  .appId(APP_ID)
                                                                  .name("tf-test")
                                                                  .sourceRepoBranch("master")
                                                                  .path("module/main.tf")
                                                                  .sourceRepoSettingId(SETTING_ID)
                                                                  .build();
    InfrastructureProvisionerServiceImpl provisionerService = infrastructureProvisionerServiceImpl;
    doReturn(GitConfig.builder().build()).when(gitUtilsManager).getGitConfig(SETTING_ID);

    provisionerService.validateProvisioner(terraformProvisioner);

    shouldValidateRepoBranchAndCommitId(terraformProvisioner, provisionerService);
    provisionerService.validateProvisioner(terraformProvisioner);

    shouldValidatePath(terraformProvisioner, provisionerService);
    provisionerService.validateProvisioner(terraformProvisioner);

    shouldValidateSourceRepo(terraformProvisioner, provisionerService);
    provisionerService.validateProvisioner(terraformProvisioner);

    shouldVariablesValidation(terraformProvisioner, provisionerService);
    provisionerService.validateProvisioner(terraformProvisioner);

    shouldBackendConfigValidation(terraformProvisioner, provisionerService);
    provisionerService.validateProvisioner(terraformProvisioner);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void shouldValidateTerragruntProvisioner() {
    TerragruntInfrastructureProvisioner terragruntProvisioner = TerragruntInfrastructureProvisioner.builder()
                                                                    .accountId(ACCOUNT_ID)
                                                                    .appId(APP_ID)
                                                                    .name("terragrunt-test")
                                                                    .sourceRepoBranch("master")
                                                                    .path("module/terragrunt.hcl")
                                                                    .sourceRepoSettingId(SETTING_ID)
                                                                    .build();
    InfrastructureProvisionerServiceImpl provisionerService = infrastructureProvisionerServiceImpl;
    doReturn(GitConfig.builder().build()).when(gitUtilsManager).getGitConfig(SETTING_ID);

    provisionerService.validateProvisioner(terragruntProvisioner);

    shouldValidateTerragruntRepoBranchAndCommitId(terragruntProvisioner, provisionerService);
    provisionerService.validateProvisioner(terragruntProvisioner);

    shouldValidateTerragruntPath(terragruntProvisioner, provisionerService);
    provisionerService.validateProvisioner(terragruntProvisioner);

    shouldValidateTerragruntSourceRepo(terragruntProvisioner, provisionerService);
    provisionerService.validateProvisioner(terragruntProvisioner);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void shouldValidateTerragruntProvisionerForSecretManager() {
    TerragruntInfrastructureProvisioner terragruntProvisioner = TerragruntInfrastructureProvisioner.builder()
                                                                    .accountId(ACCOUNT_ID)
                                                                    .appId(APP_ID)
                                                                    .name("terragrunt-test")
                                                                    .sourceRepoBranch("master")
                                                                    .path("module/terragrunt.hcl")
                                                                    .secretManagerId("secretManagerId")
                                                                    .sourceRepoSettingId(SETTING_ID)
                                                                    .build();
    InfrastructureProvisionerServiceImpl provisionerService = infrastructureProvisionerServiceImpl;
    doReturn(GitConfig.builder().build()).when(gitUtilsManager).getGitConfig(SETTING_ID);
    shouldValidateTerraGroupProvisionerSecretManager(terragruntProvisioner, provisionerService);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void shouldValidateTerraformProvisionerForSecretManager() {
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .appId(APP_ID)
                                                         .name("terragrunt-test")
                                                         .sourceRepoBranch("master")
                                                         .path("module/terragrunt.hcl")
                                                         .kmsId("secretManagerId")
                                                         .sourceRepoSettingId(SETTING_ID)
                                                         .build();
    InfrastructureProvisionerServiceImpl provisionerService = infrastructureProvisionerServiceImpl;
    doReturn(GitConfig.builder().build()).when(gitUtilsManager).getGitConfig(SETTING_ID);
    shouldValidateTerraGroupProvisionerSecretManager(provisioner, provisionerService);
  }

  private void shouldValidateTerraGroupProvisionerSecretManager(
      InfrastructureProvisioner provisioner, InfrastructureProvisionerServiceImpl provisionerService) {
    doReturn(KmsConfig.builder().build()).doReturn(null).when(secretManager).getSecretManager(any(), any());
    assertThatCode(() -> provisionerService.validateProvisioner(provisioner)).doesNotThrowAnyException();

    assertThatThrownBy(() -> provisionerService.validateProvisioner(provisioner))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No secret manger found");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void shouldValidateCloudFormationInfrastructureProvisioner() {
    GitFileConfig gitFileConfig = GitFileConfig.builder().build();
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .appId(APP_ID)
                                                              .uuid(ID_KEY)
                                                              .sourceType("GIT")
                                                              .gitFileConfig(gitFileConfig)
                                                              .build();
    infrastructureProvisionerServiceImpl.validateProvisioner(provisioner);
    verify(gitFileConfigHelperService).validate(gitFileConfig);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void shouldntValidateCloudFormationInfrastructureProvisioner() {
    GitFileConfig gitFileConfig = GitFileConfig.builder().build();
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .appId(APP_ID)
                                                              .uuid(ID_KEY)
                                                              .gitFileConfig(gitFileConfig)
                                                              .build();
    infrastructureProvisionerServiceImpl.validateProvisioner(provisioner);
    verify(gitFileConfigHelperService, times(0)).validate(gitFileConfig);
  }

  private void shouldBackendConfigValidation(TerraformInfrastructureProvisioner terraformProvisioner,
      InfrastructureProvisionerServiceImpl provisionerService) {
    terraformProvisioner.setBackendConfigs(
        asList(NameValuePair.builder().name("access.key").valueType(ServiceVariableType.TEXT.toString()).build(),
            NameValuePair.builder().name("secret_key").valueType(ServiceVariableType.TEXT.toString()).build()));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terraformProvisioner));

    terraformProvisioner.setBackendConfigs(
        asList(NameValuePair.builder().name("$access_key").valueType(ServiceVariableType.TEXT.toString()).build(),
            NameValuePair.builder().name("secret_key").valueType(ServiceVariableType.TEXT.toString()).build()));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terraformProvisioner));

    terraformProvisioner.setBackendConfigs(null);
    provisionerService.validateProvisioner(terraformProvisioner);

    terraformProvisioner.setBackendConfigs(Collections.emptyList());
    provisionerService.validateProvisioner(terraformProvisioner);

    terraformProvisioner.setBackendConfigs(
        asList(NameValuePair.builder().name("access_key").valueType(ServiceVariableType.TEXT.toString()).build(),
            NameValuePair.builder().name("secret_key").valueType(ServiceVariableType.TEXT.toString()).build()));
    provisionerService.validateProvisioner(terraformProvisioner);
  }

  private void shouldVariablesValidation(TerraformInfrastructureProvisioner terraformProvisioner,
      InfrastructureProvisionerServiceImpl provisionerService) {
    terraformProvisioner.setVariables(
        asList(NameValuePair.builder().name("access.key").valueType(ServiceVariableType.TEXT.toString()).build(),
            NameValuePair.builder().name("secret_key").valueType(ServiceVariableType.TEXT.toString()).build()));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terraformProvisioner));

    terraformProvisioner.setVariables(
        asList(NameValuePair.builder().name("$access_key").valueType(ServiceVariableType.TEXT.toString()).build(),
            NameValuePair.builder().name("secret_key").valueType(ServiceVariableType.TEXT.toString()).build()));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terraformProvisioner));

    terraformProvisioner.setVariables(null);
    provisionerService.validateProvisioner(terraformProvisioner);

    terraformProvisioner.setVariables(Collections.emptyList());
    provisionerService.validateProvisioner(terraformProvisioner);

    terraformProvisioner.setVariables(
        asList(NameValuePair.builder().name("access_key").valueType(ServiceVariableType.TEXT.toString()).build(),
            NameValuePair.builder().name("secret_key").valueType(ServiceVariableType.TEXT.toString()).build()));
    provisionerService.validateProvisioner(terraformProvisioner);
  }

  private void shouldValidateSourceRepo(TerraformInfrastructureProvisioner terraformProvisioner,
      InfrastructureProvisionerServiceImpl provisionerService) {
    terraformProvisioner.setSourceRepoSettingId("");
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terraformProvisioner));
    terraformProvisioner.setSourceRepoSettingId(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terraformProvisioner));
    terraformProvisioner.setSourceRepoSettingId(SETTING_ID);
  }

  private void shouldValidateTerragruntSourceRepo(TerragruntInfrastructureProvisioner terragruntProvisioner,
      InfrastructureProvisionerServiceImpl provisionerService) {
    terragruntProvisioner.setSourceRepoSettingId("");
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terragruntProvisioner));
    terragruntProvisioner.setSourceRepoSettingId(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terragruntProvisioner));
    terragruntProvisioner.setSourceRepoSettingId(SETTING_ID);
  }

  private void shouldValidatePath(TerraformInfrastructureProvisioner terraformProvisioner,
      InfrastructureProvisionerServiceImpl provisionerService) {
    terraformProvisioner.setPath(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terraformProvisioner));
    terraformProvisioner.setPath("module/main.tf");
  }

  private void shouldValidateTerragruntPath(TerragruntInfrastructureProvisioner terragruntProvisioner,
      InfrastructureProvisionerServiceImpl provisionerService) {
    terragruntProvisioner.setPath(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terragruntProvisioner));
    terragruntProvisioner.setPath("module/terragrunt.hcl");
  }

  private void shouldValidateRepoBranchAndCommitId(TerraformInfrastructureProvisioner terraformProvisioner,
      InfrastructureProvisionerServiceImpl provisionerService) {
    terraformProvisioner.setSourceRepoBranch("");
    terraformProvisioner.setCommitId("");
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terraformProvisioner));
    terraformProvisioner.setSourceRepoBranch(null);
    terraformProvisioner.setCommitId(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terraformProvisioner));
    terraformProvisioner.setSourceRepoBranch("master");
  }

  private void shouldValidateTerragruntRepoBranchAndCommitId(TerragruntInfrastructureProvisioner terragruntProvisioner,
      InfrastructureProvisionerServiceImpl provisionerService) {
    terragruntProvisioner.setSourceRepoBranch("");
    terragruntProvisioner.setCommitId("");
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terragruntProvisioner));
    terragruntProvisioner.setSourceRepoBranch(null);
    terragruntProvisioner.setCommitId(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.validateProvisioner(terragruntProvisioner));
    terragruntProvisioner.setSourceRepoBranch("master");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void nonProvisionerExpressionsResolutionShouldNotFailOnNonResolution() {
    String workflowVariable = "${workflowVariables.var1}";
    Map<String, Object> contextMap = null;
    List<NameValuePair> properties = new ArrayList<>();
    properties.add(NameValuePair.builder().value(workflowVariable).build());
    ManagerExpressionEvaluator evaluator = mock(ManagerExpressionEvaluator.class);
    Reflect.on(infrastructureProvisionerService).set("evaluator", evaluator);
    when(evaluator.evaluate(workflowVariable, contextMap)).thenReturn(null);

    ((InfrastructureProvisionerServiceImpl) infrastructureProvisionerService)
        .getPropertyNameEvaluatedMap(
            properties, contextMap, TerraformInfrastructureProvisioner.INFRASTRUCTURE_PROVISIONER_TYPE_KEY);

    verify(evaluator, times(1)).evaluate(workflowVariable, contextMap);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void provisionerExpressionsResolutionShouldFailOnNonResolution() {
    String provisionerVariable = "${terraform.var1}";
    Map<String, Object> contextMap = null;
    List<NameValuePair> properties = new ArrayList<>();
    properties.add(NameValuePair.builder().value(provisionerVariable).build());
    ManagerExpressionEvaluator evaluator = mock(ManagerExpressionEvaluator.class);
    Reflect.on(infrastructureProvisionerService).set("evaluator", evaluator);
    when(evaluator.evaluate(provisionerVariable, contextMap)).thenReturn(null);

    ((InfrastructureProvisionerServiceImpl) infrastructureProvisionerService)
        .getPropertyNameEvaluatedMap(properties, contextMap, TerraformInfrastructureProvisioner.VARIABLE_KEY);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldGetIdToServiceMapping() {
    PageRequest<Service> servicePageRequest = new PageRequest<>();
    servicePageRequest.addFilter(Service.APP_ID, Operator.EQ, APP_ID);
    Set<String> serviceIds = Sets.newHashSet(asList("id1", "id2"));
    servicePageRequest.addFilter(ServiceKeys.uuid, Operator.IN, serviceIds.toArray());
    PageResponse<Service> services = new PageResponse<>();
    Service service1 = Service.builder().name("service1").uuid("id1").build();
    Service service2 = Service.builder().name("service2").uuid("id2").build();
    services.setResponse(asList(service1, service2));
    when(serviceResourceService.list(servicePageRequest, false, false, false, null)).thenReturn(services);

    Map<String, Service> idToServiceMapping = ((InfrastructureProvisionerServiceImpl) infrastructureProvisionerService)
                                                  .getIdToServiceMapping(APP_ID, serviceIds);

    assertThat(idToServiceMapping).hasSize(2);
    assertThat(idToServiceMapping.get("id1")).isEqualTo(service1);
    assertThat(idToServiceMapping.get("id2")).isEqualTo(service2);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldGetIdToServiceMappingForEmptyServiceIds() {
    Map<String, Service> idToServiceMapping = ((InfrastructureProvisionerServiceImpl) infrastructureProvisionerService)
                                                  .getIdToServiceMapping(APP_ID, Collections.emptySet());

    assertThat(idToServiceMapping).isEmpty();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldGetIdToSettingAttributeMapping() {
    PageRequest<SettingAttribute> settingAttributePageRequest = new PageRequest<>();
    settingAttributePageRequest.addFilter(SettingAttribute.ACCOUNT_ID_KEY, Operator.EQ, ACCOUNT_ID);
    settingAttributePageRequest.addFilter(
        SettingAttributeKeys.value_type, Operator.EQ, SettingVariableTypes.GIT.name());
    Set<String> settingAttributeIds = Sets.newHashSet(asList("id1", "id2"));
    settingAttributePageRequest.addFilter(SettingAttributeKeys.uuid, Operator.IN, settingAttributeIds.toArray());
    PageResponse<SettingAttribute> settingAttributePageResponse = new PageResponse<>();
    SettingAttribute settingAttribute1 = aSettingAttribute().withUuid("id1").build();
    SettingAttribute settingAttribute2 = aSettingAttribute().withUuid("id2").build();
    settingAttributePageResponse.setResponse(asList(settingAttribute1, settingAttribute2));
    when(settingService.list(settingAttributePageRequest, null, null, false)).thenReturn(settingAttributePageResponse);

    Map<String, SettingAttribute> idToSettingAttributeMapping =
        ((InfrastructureProvisionerServiceImpl) infrastructureProvisionerService)
            .getIdToSettingAttributeMapping(ACCOUNT_ID, settingAttributeIds);

    assertThat(idToSettingAttributeMapping).hasSize(2);
    assertThat(idToSettingAttributeMapping.get("id1")).isEqualTo(settingAttribute1);
    assertThat(idToSettingAttributeMapping.get("id2")).isEqualTo(settingAttribute2);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldGetIdToSettingAttributeMappingForEmptySettingAttributeIds() {
    Map<String, SettingAttribute> idToSettingAttributeMapping =
        ((InfrastructureProvisionerServiceImpl) infrastructureProvisionerService)
            .getIdToSettingAttributeMapping(ACCOUNT_ID, Collections.emptySet());

    assertThat(idToSettingAttributeMapping).isEmpty();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldListDetails() {
    InfrastructureProvisionerServiceImpl ipService = spy(new InfrastructureProvisionerServiceImpl());
    Reflect.on(ipService).set("resourceLookupService", resourceLookupService);
    Reflect.on(ipService).set("appService", appService);
    PageRequest<InfrastructureProvisioner> infraProvisionerPageRequest = new PageRequest<>();
    PageResponse<InfrastructureProvisioner> infraProvisionerPageResponse = new PageResponse<>();
    TerraformInfrastructureProvisioner provisioner =
        TerraformInfrastructureProvisioner.builder()
            .sourceRepoSettingId("settingId")
            .mappingBlueprints(singletonList(InfrastructureMappingBlueprint.builder().serviceId("serviceId").build()))
            .build();
    infraProvisionerPageResponse.setResponse(singletonList(provisioner));
    doReturn(infraProvisionerPageResponse)
        .when(resourceLookupService)
        .listWithTagFilters(infraProvisionerPageRequest, null, EntityType.PROVISIONER, true, false);
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
    HashSet<String> settingAttributeIds = new HashSet<>(singletonList("settingId"));
    Map<String, SettingAttribute> idToSettingAttributeMapping = new HashMap<>();
    doReturn(idToSettingAttributeMapping)
        .when(ipService)
        .getIdToSettingAttributeMapping(ACCOUNT_ID, settingAttributeIds);
    HashSet<String> servicesIds = new HashSet<>(singletonList("serviceId"));
    Map<String, Service> idToServiceMapping = new HashMap<>();
    doReturn(idToServiceMapping).when(ipService).getIdToServiceMapping(APP_ID, servicesIds);
    InfrastructureProvisionerDetails infrastructureProvisionerDetails =
        InfrastructureProvisionerDetails.builder().build();
    doReturn(infrastructureProvisionerDetails).when(ipService).details(provisioner, idToSettingAttributeMapping);

    PageResponse<InfrastructureProvisionerDetails> infraProvisionerDetailsPageResponse =
        ipService.listDetails(infraProvisionerPageRequest, true, null, APP_ID);

    assertThat(infraProvisionerDetailsPageResponse.getResponse()).hasSize(1);
    assertThat(infraProvisionerDetailsPageResponse.getResponse().get(0)).isEqualTo(infrastructureProvisionerDetails);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void detailsTest() {
    InfrastructureProvisionerServiceImpl ipService = spy(new InfrastructureProvisionerServiceImpl());
    FeatureFlagService mockFeatureFlagService = mock(FeatureFlagService.class);
    GitConfigHelperService spyGitConfigHelperService = spy(new GitConfigHelperService());
    Reflect.on(ipService).set("featureFlagService", mockFeatureFlagService);
    Reflect.on(ipService).set("gitConfigHelperService", spyGitConfigHelperService);

    Map<String, SettingAttribute> idToSettingAttributeMapping = new HashMap<>();

    idToSettingAttributeMapping.put("settingId",
        aSettingAttribute()
            .withValue(GitConfig.builder().urlType(GitConfig.UrlType.ACCOUNT).repoUrl("http://a.com/b").build())
            .build());

    idToSettingAttributeMapping.put("settingId3",
        aSettingAttribute()
            .withValue(GitConfig.builder().urlType(GitConfig.UrlType.REPO).repoUrl("http://a.com/b/z").build())
            .build());

    idToSettingAttributeMapping.put("settingId4",
        aSettingAttribute()
            .withValue(GitConfig.builder().urlType(GitConfig.UrlType.REPO).repoUrl("http://a.com/b/z").build())
            .build());

    InfrastructureProvisionerDetails details1 = ipService.details(
        TerraformInfrastructureProvisioner.builder().sourceRepoSettingId("settingId").repoName("c").build(),
        idToSettingAttributeMapping);

    InfrastructureProvisionerDetails details2 = ipService.details(
        TerraformInfrastructureProvisioner.builder().sourceRepoSettingId("settingId").repoName("d").build(),
        idToSettingAttributeMapping);

    InfrastructureProvisionerDetails details3 =
        ipService.details(TerraformInfrastructureProvisioner.builder().sourceRepoSettingId("settingId3").build(),
            idToSettingAttributeMapping);

    InfrastructureProvisionerDetails details4 =
        ipService.details(TerragruntInfrastructureProvisioner.builder().sourceRepoSettingId("settingId4").build(),
            idToSettingAttributeMapping);

    InfrastructureProvisionerDetails details5 = ipService.details(
        TerragruntInfrastructureProvisioner.builder().sourceRepoSettingId("settingId").repoName("e").build(),
        idToSettingAttributeMapping);

    assertThat(details1.getRepository()).isEqualTo("http://a.com/b/c");
    assertThat(details2.getRepository()).isEqualTo("http://a.com/b/d");
    assertThat(details3.getRepository()).isEqualTo("http://a.com/b/z");
    assertThat(details4.getRepository()).isEqualTo("http://a.com/b/z");
    assertThat(details5.getRepository()).isEqualTo("http://a.com/b/e");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldListDetailsForEmptyInfraMappingBlueprints() {
    InfrastructureProvisionerServiceImpl ipService = spy(new InfrastructureProvisionerServiceImpl());
    Reflect.on(ipService).set("resourceLookupService", resourceLookupService);
    Reflect.on(ipService).set("appService", appService);
    PageRequest<InfrastructureProvisioner> infraProvisionerPageRequest = new PageRequest<>();
    PageResponse<InfrastructureProvisioner> infraProvisionerPageResponse = new PageResponse<>();
    TerraformInfrastructureProvisioner provisioner =
        TerraformInfrastructureProvisioner.builder().sourceRepoSettingId("settingId").build();
    infraProvisionerPageResponse.setResponse(singletonList(provisioner));
    doReturn(infraProvisionerPageResponse)
        .when(resourceLookupService)
        .listWithTagFilters(infraProvisionerPageRequest, null, EntityType.PROVISIONER, true, false);
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
    HashSet<String> settingAttributeIds = new HashSet<>(singletonList("settingId"));
    Map<String, SettingAttribute> idToSettingAttributeMapping = new HashMap<>();
    doReturn(idToSettingAttributeMapping)
        .when(ipService)
        .getIdToSettingAttributeMapping(ACCOUNT_ID, settingAttributeIds);
    HashSet<String> servicesIds = new HashSet<>();
    Map<String, Service> idToServiceMapping = new HashMap<>();
    doReturn(idToServiceMapping).when(ipService).getIdToServiceMapping(APP_ID, servicesIds);
    InfrastructureProvisionerDetails infrastructureProvisionerDetails =
        InfrastructureProvisionerDetails.builder().build();
    doReturn(infrastructureProvisionerDetails).when(ipService).details(provisioner, idToSettingAttributeMapping);

    PageResponse<InfrastructureProvisionerDetails> infraProvisionerDetailsPageResponse =
        ipService.listDetails(infraProvisionerPageRequest, true, null, APP_ID);

    assertThat(infraProvisionerDetailsPageResponse.getResponse()).hasSize(1);
    assertThat(infraProvisionerDetailsPageResponse.getResponse().get(0)).isEqualTo(infrastructureProvisionerDetails);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void shouldFilterDeletedServices() {
    InfrastructureProvisionerServiceImpl ipService = spy(new InfrastructureProvisionerServiceImpl());
    WingsPersistence mockWingsPersistence = mock(WingsPersistence.class);
    Reflect.on(ipService).set("wingsPersistence", mockWingsPersistence);
    FeatureFlagService mockFeatureFlagService = mock(FeatureFlagService.class);
    String SVC_ID_00 = "svc-00";
    String SVC_ID_01 = "svc-01";
    InfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().build();
    doReturn(provisioner).when(mockWingsPersistence).getWithAppId(any(), anyString(), anyString());
    Map<String, Service> map = new HashMap<>();
    map.put("svc-00", Service.builder().uuid(SVC_ID_00).name("name-00").build());
    doReturn(map).when(ipService).getIdToServiceMapping(anyString(), anySet());
    InfrastructureProvisioner returned = ipService.get(APP_ID, PROVISIONER_ID);
    assertThat(returned).isNotNull();
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void testRestrictDuplicateVariables() {
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().build();
    NameValuePair var1 = NameValuePair.builder().name("var1").build();
    NameValuePair var2 = NameValuePair.builder().name("var2").build();
    NameValuePair var3 = NameValuePair.builder().name("var3").build();
    NameValuePair duplicateVar1 = NameValuePair.builder().name("var1").build();
    NameValuePair duplicateVar2 = NameValuePair.builder().name("var1").build();

    // variable list is NULL
    infrastructureProvisionerServiceImpl.restrictDuplicateVariables(provisioner);

    // variable list is empty
    provisioner.setVariables(emptyList());
    infrastructureProvisionerServiceImpl.restrictDuplicateVariables(provisioner);

    // all variable are same
    provisioner.setVariables(Arrays.asList(var1, duplicateVar1, duplicateVar2));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureProvisionerServiceImpl.restrictDuplicateVariables(provisioner))
        .withMessage("variable names should be unique, duplicate variable(s) found: [var1]");

    // some variable are same
    provisioner.setVariables(Arrays.asList(var1, duplicateVar1, var2, var3, var2));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureProvisionerServiceImpl.restrictDuplicateVariables(provisioner))
        .withMessage("variable names should be unique, duplicate variable(s) found: [var2, var1]");

    // all variable are distinct
    provisioner.setVariables(Arrays.asList(var1, var2, var3));
    infrastructureProvisionerServiceImpl.restrictDuplicateVariables(provisioner);

    assertThat(provisioner.getVariables()).hasSize(3);
    assertThat(provisioner.getVariables().get(0)).isEqualTo(var1);
    assertThat(provisioner.getVariables().get(1)).isEqualTo(var2);
    assertThat(provisioner.getVariables().get(2)).isEqualTo(var3);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void checkForDuplicate() {
    InfrastructureProvisionerServiceImpl provisionerService = spy(InfrastructureProvisionerServiceImpl.class);
    Reflect.on(provisionerService).set("wingsPersistence", persistence);
    testSaveProvisionerWithNewName(provisionerService);
    testSaveProvisionerWithExistingName(provisionerService);
    testUpdateProvisionerWithNewName(provisionerService);
    testUpdateProvisionerWithExistingName(provisionerService);
    testSaveProvisionerWithExistingNameInNewApp(provisionerService);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testTrimInfrastructureProvisionerVariables() {
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().build();
    NameValuePair var1 = NameValuePair.builder().name("var1").build();
    NameValuePair varWithNoTrailingSpaces = NameValuePair.builder().name("var2").build();
    NameValuePair varWithEmptyString = NameValuePair.builder().name(StringUtils.EMPTY).build();
    NameValuePair varWithTrailingSpaces = NameValuePair.builder().name("var2    ").build();
    NameValuePair varWithLeadinggSpaces = NameValuePair.builder().name(" var2 ").build();
    NameValuePair varWithOnlySpaces = NameValuePair.builder().name("  ").build();

    provisioner.setVariables(Arrays.asList(var1, varWithTrailingSpaces, varWithLeadinggSpaces, varWithOnlySpaces));
    infrastructureProvisionerServiceImpl.trimInfrastructureProvisionerVariables(provisioner);

    assertThat(provisioner.getVariables())
        .contains(var1, varWithNoTrailingSpaces, varWithNoTrailingSpaces, varWithEmptyString);

    // empty variable list
    provisioner.setVariables(emptyList());
    infrastructureProvisionerServiceImpl.trimInfrastructureProvisionerVariables(provisioner);
    assertThat(provisioner.getVariables()).isEqualTo(emptyList());
  }

  private void testUpdateProvisionerWithExistingName(InfrastructureProvisionerServiceImpl provisionerService) {
    InfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                .appId(APP_ID)
                                                .name(PROVISIONER_NAME)
                                                .uuid(PROVISIONER_ID + "_1")
                                                .build();
    InfrastructureProvisioner existingProvisioner = TerraformInfrastructureProvisioner.builder()
                                                        .appId(provisioner.getAppId())
                                                        .name(provisioner.getName())
                                                        .uuid(PROVISIONER_ID + "_2")
                                                        .build();

    persistence.save(existingProvisioner);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.checkForDuplicate(provisioner))
        .withMessageContaining("Provisioner with name");

    persistence.delete(provisioner);
  }

  private void testUpdateProvisionerWithNewName(InfrastructureProvisionerServiceImpl provisionerService) {
    InfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                .appId(APP_ID)
                                                .name("harness-test")
                                                .uuid(PROVISIONER_ID + "_1")
                                                .build();
    InfrastructureProvisioner existingProvisioner = TerraformInfrastructureProvisioner.builder()
                                                        .appId(provisioner.getAppId())
                                                        .name("some-other-name")
                                                        .uuid(PROVISIONER_ID + "_2")
                                                        .build();
    persistence.save(existingProvisioner);
    provisionerService.checkForDuplicate(provisioner);
    persistence.delete(existingProvisioner);
  }

  private void testSaveProvisionerWithExistingName(InfrastructureProvisionerServiceImpl provisionerService) {
    InfrastructureProvisioner provisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).name("yet-another-common-name").build();
    InfrastructureProvisioner existingProvisioner = TerraformInfrastructureProvisioner.builder()
                                                        .appId(provisioner.getAppId())
                                                        .name(provisioner.getName())
                                                        .uuid(PROVISIONER_ID)
                                                        .build();

    persistence.save(existingProvisioner);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> provisionerService.checkForDuplicate(provisioner))
        .withMessageContaining("Provisioner with name");
    persistence.delete(existingProvisioner);
  }

  private void testSaveProvisionerWithNewName(InfrastructureProvisionerServiceImpl provisionerService) {
    InfrastructureProvisioner provisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).name("some-random-name").build();
    provisionerService.checkForDuplicate(provisioner);
  }

  private void testSaveProvisionerWithExistingNameInNewApp(InfrastructureProvisionerServiceImpl provisionerService) {
    InfrastructureProvisioner provisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).name("foo-bar").build();
    InfrastructureProvisioner existingProvisioner = TerraformInfrastructureProvisioner.builder()
                                                        .appId(provisioner.getAppId() + "_1")
                                                        .name(provisioner.getName())
                                                        .uuid(PROVISIONER_ID)
                                                        .build();
    persistence.save(existingProvisioner);
    provisionerService.checkForDuplicate(provisioner);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testNPEInResolveExpressions() {
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .infrastructure(AwsEcsInfrastructure.builder().expressions(ImmutableMap.of("key", "value")).build())
            .build();

    String provisionerVariable = "${shellScriptProvisioner.value}";
    Map<String, Object> contextMap = null;
    List<NameValuePair> properties = new ArrayList<>();
    properties.add(NameValuePair.builder().value(provisionerVariable).build());
    ManagerExpressionEvaluator evaluator = mock(ManagerExpressionEvaluator.class);
    Reflect.on(infrastructureProvisionerService).set("evaluator", evaluator);
    when(evaluator.evaluate(provisionerVariable, contextMap)).thenReturn("VAL");

    Map<String, Object> resolveExpressions = infrastructureProvisionerService.resolveExpressions(
        infrastructureDefinition, contextMap, ShellScriptInfrastructureProvisioner.builder().build());
    assertThat(resolveExpressions.get("key")).isEqualTo("VAL");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void getTerraformVariablesExceptionTest() {
    SettingAttribute attribute = aSettingAttribute().build();
    doReturn(attribute).when(settingService).get(SETTING_ID);
    assertThatThrownBy(()
                           -> infrastructureProvisionerService.getTerraformVariables(APP_ID, SETTING_ID, ".",
                               ACCOUNT_ID, "branch", null, "repo", TerraformSourceType.GIT, null, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void getTerraformVariablesBranchCommitIdTest() {
    SettingAttribute attribute = aSettingAttribute().build();
    doReturn(attribute).when(settingService).get(SETTING_ID);
    assertThatThrownBy(()
                           -> infrastructureProvisionerService.getTerraformVariables(APP_ID, SETTING_ID, ".",
                               ACCOUNT_ID, null, null, "repo", TerraformSourceType.GIT, null, null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> infrastructureProvisionerService.getTerraformVariables(APP_ID, SETTING_ID, ".",
                               ACCOUNT_ID, null, null, "repo", TerraformSourceType.GIT, null, null))
        .hasMessage("Either sourceRepoBranch or commitId should be specified");

    assertThatThrownBy(()
                           -> infrastructureProvisionerService.getTerraformVariables(APP_ID, SETTING_ID, ".",
                               ACCOUNT_ID, "branch", "commitId", "repo", TerraformSourceType.GIT, null, null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> infrastructureProvisionerService.getTerraformVariables(APP_ID, SETTING_ID, ".",
                               ACCOUNT_ID, "branch", "commitId", "repo", TerraformSourceType.GIT, null, null))
        .hasMessage("Cannot specify both sourceRepoBranch and commitId");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void getTerraformVariablesTest() throws Exception {
    GitConfig gitConfig = GitConfig.builder().build();
    SettingAttribute attribute = aSettingAttribute().withValue(gitConfig).build();
    doReturn(attribute).when(settingService).get(SETTING_ID);

    String repoName = "repoName";
    ArgumentCaptor<GitConfig> gitConfigArgumentCaptor = ArgumentCaptor.forClass(GitConfig.class);
    ArgumentCaptor<String> repoNameArgumentCaptor = ArgumentCaptor.forClass(String.class);

    List<NameValuePair> expectedVariables = singletonList(NameValuePair.builder().build());
    doNothing()
        .when(gitConfigHelperService)
        .convertToRepoGitConfig(gitConfigArgumentCaptor.capture(), repoNameArgumentCaptor.capture());
    TerraformInputVariablesTaskResponse response =
        TerraformInputVariablesTaskResponse.builder()
            .terraformExecutionData(TerraformExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).build())
            .variablesList(expectedVariables)
            .build();
    doReturn(response).when(delegateService).executeTaskV2(any(DelegateTask.class));
    List<NameValuePair> variables = infrastructureProvisionerService.getTerraformVariables(
        APP_ID, SETTING_ID, ".", ACCOUNT_ID, "branch", null, repoName, TerraformSourceType.GIT, null, null);

    assertThat(gitConfigArgumentCaptor.getValue()).isEqualTo(gitConfig);
    assertThat(repoNameArgumentCaptor.getValue()).isEqualTo(repoName);
    assertThat(variables).isEqualTo(expectedVariables);
    verify(delegateService).executeTaskV2(any(DelegateTask.class));

    response.getTerraformExecutionData().setExecutionStatus(ExecutionStatus.FAILED);
    response.getTerraformExecutionData().setErrorMessage("error");
    assertThatThrownBy(()
                           -> infrastructureProvisionerService.getTerraformVariables(APP_ID, SETTING_ID, ".",
                               ACCOUNT_ID, "branch", null, repoName, TerraformSourceType.GIT, null, null))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void getS3TerraformVariablesTest() throws Exception {
    AwsConfig awsConfig = AwsConfig.builder()
                              .accountId("ACCT_ID")
                              .accessKey("accessKeyId".toCharArray())
                              .secretKey("secretAccessKey".toCharArray())
                              .defaultRegion("us-east-1")
                              .build();
    SettingAttribute attribute = aSettingAttribute().withValue(awsConfig).build();
    doReturn(attribute).when(settingService).get(SETTING_ID);

    List<EncryptedDataDetail> awsConfigEncryptionDetails = new ArrayList<>();
    List<NameValuePair> expectedVariables = singletonList(NameValuePair.builder().build());
    TerraformInputVariablesTaskResponse response =
        TerraformInputVariablesTaskResponse.builder()
            .terraformExecutionData(TerraformExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).build())
            .variablesList(expectedVariables)
            .build();

    doReturn(response).when(delegateService).executeTaskV2(any(DelegateTask.class));
    String s3SourceURI = "s3://iis-website-quickstart/terraform-manifest/variablesAndNullResources/";

    List<NameValuePair> variables = infrastructureProvisionerService.getTerraformVariables(
        APP_ID, null, ".", ACCOUNT_ID, null, null, null, TerraformSourceType.S3, s3SourceURI, SETTING_ID);
    Mockito.verify(settingService, times(1)).get(any());
    Mockito.verify(secretManager, times(1)).getEncryptionDetails(eq(awsConfig), eq(APP_ID), any());
    assertThat(variables).isEqualTo(expectedVariables);
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void getS3TerraformTargetsTest() throws Exception {
    String awsConfigId = "SETTING_ID";

    InfrastructureProvisioner infrastructureProvisioner = TerraformInfrastructureProvisioner.builder()
                                                              .appId(APP_ID)
                                                              .uuid(ID_KEY)
                                                              .terraformSourceType(TerraformSourceType.S3)
                                                              .s3URI(S3_URI)
                                                              .awsConfigId(awsConfigId)
                                                              .build();
    doReturn(infrastructureProvisioner)
        .when(mockWingsPersistence)
        .getWithAppId(eq(InfrastructureProvisioner.class), anyString(), anyString());

    AwsConfig awsConfig = AwsConfig.builder()
                              .accountId("ACCT_ID")
                              .accessKey("accessKeyId".toCharArray())
                              .secretKey("secretAccessKey".toCharArray())
                              .defaultRegion("us-east-1")
                              .build();
    SettingAttribute attribute = aSettingAttribute().withValue(awsConfig).build();
    doReturn(attribute).when(settingService).get(SETTING_ID);

    List<EncryptedDataDetail> awsConfigEncryptionDetails = new ArrayList<>();
    TerraformExecutionData response = TerraformExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).build();

    doReturn(response).when(delegateService).executeTaskV2(any(DelegateTask.class));

    infrastructureProvisionerService.getTerraformTargets(APP_ID, ACCOUNT_ID, SETTING_ID);

    Mockito.verify(settingService, times(1)).get(awsConfigId);
    Mockito.verify(secretManager, times(1)).getEncryptionDetails(eq(awsConfig), eq(APP_ID), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testBlankTempateFilePathpathNotAllowedInCfInfrastructureProvisioner() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .appId(APP_ID)
                                                              .uuid(ID_KEY)
                                                              .sourceType(TEMPLATE_URL.name())
                                                              .templateFilePath(StringUtils.SPACE)
                                                              .build();
    assertThatThrownBy(() -> infrastructureProvisionerServiceImpl.validateProvisioner(provisioner))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> infrastructureProvisionerServiceImpl.validateProvisioner(provisioner))
        .hasMessage("Template File Path can not be empty");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testBlankTemplateBodyNotAllowedInCfInfrastructureProvisioner() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .appId(APP_ID)
                                                              .uuid(ID_KEY)
                                                              .sourceType(TEMPLATE_BODY.name())
                                                              .templateBody(StringUtils.SPACE)
                                                              .build();
    assertThatThrownBy(() -> infrastructureProvisionerServiceImpl.validateProvisioner(provisioner))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> infrastructureProvisionerServiceImpl.validateProvisioner(provisioner))
        .hasMessage("Template Body can not be empty");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testBlankScriptBodyNotAllowedInShellScriptInfrastructureProvisioner() {
    ShellScriptInfrastructureProvisioner shellScriptInfrastructureProvisioner =
        ShellScriptInfrastructureProvisioner.builder().appId(APP_ID).uuid(ID_KEY).scriptBody(StringUtils.SPACE).build();
    assertThatThrownBy(
        () -> infrastructureProvisionerServiceImpl.validateProvisioner(shellScriptInfrastructureProvisioner))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        () -> infrastructureProvisionerServiceImpl.validateProvisioner(shellScriptInfrastructureProvisioner))
        .hasMessage("Script Body can not be empty");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExtractEncryptedTextVariablesUpdateSecretsRunTimeusage() {
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("access_token").value("access-token").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("region").value("us-east-1").valueType("TEXT").build());

    Optional<EncryptedDataDetail> encryptedDataDetailOptional =
        Optional.of(EncryptedDataDetail.builder().fieldName("fieldName").build());
    doReturn(encryptedDataDetailOptional).when(secretManager).encryptedDataDetails(any(), any(), any(), any());

    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(any());
    Map<String, EncryptedDataDetail> encryptedTextVariables =
        infrastructureProvisionerService.extractEncryptedTextVariables(
            nameValuePairList, APP_ID, WORKFLOW_EXECUTION_ID);
    verify(secretManager, times(1)).encryptedDataDetails(any(), any(), any(), any());
    assertThat(encryptedTextVariables.size()).isOne();
    assertThat(encryptedTextVariables.get("access_token").getFieldName()).isEqualTo("fieldName");
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testAddProvisionerKeys() {
    // property value null
    List<BlueprintProperty> properties = new ArrayList<>();
    InfrastructureProvisioner infrastructureProvisioner = ShellScriptInfrastructureProvisioner.builder().build();
    BlueprintProperty property = BlueprintProperty.builder().value(null).build();
    properties.add(property);
    infrastructureProvisionerServiceImpl.addProvisionerKeys(properties, infrastructureProvisioner);
    assertThat(property.getValue()).isNull();

    // property value not null
    properties = new ArrayList<>();
    property = BlueprintProperty.builder().value("test").build();
    properties.add(property);
    infrastructureProvisionerServiceImpl.addProvisionerKeys(properties, infrastructureProvisioner);
    assertThat(property.getValue()).isEqualTo("${shellScriptProvisioner.test}");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testAreExpressionsValid() {
    doReturn(true).when(featureFlagService).isEnabled(eq(VALIDATE_PROVISIONER_EXPRESSION), any());
    InfrastructureProvisioner provisioner = Mockito.mock(InfrastructureProvisioner.class);
    String variableKey = TerraformInfrastructureProvisioner.VARIABLE_KEY;
    doReturn(variableKey).when(provisioner).variableKey();
    assertThat(infrastructureProvisionerServiceImpl.areExpressionsValid(provisioner, null)).isTrue();
    Map<String, Object> expressions = new HashMap<>();
    assertThat(infrastructureProvisionerServiceImpl.areExpressionsValid(provisioner, expressions)).isTrue();
    expressions.put("Key1", variableKey);
    expressions.put("Key2", provisioner);
    assertThat(infrastructureProvisionerServiceImpl.areExpressionsValid(provisioner, expressions)).isTrue();
    expressions.put("Key3", "${" + variableKey);
    assertThat(infrastructureProvisionerServiceImpl.areExpressionsValid(provisioner, expressions)).isTrue();
    expressions.put("Key4", "${" + variableKey + ".hello");
    assertThat(infrastructureProvisionerServiceImpl.areExpressionsValid(provisioner, expressions)).isTrue();
    expressions.put("Key5", "RANDOM-${" + variableKey + ".hello}");
    assertThat(infrastructureProvisionerServiceImpl.areExpressionsValid(provisioner, expressions)).isFalse();
    expressions.clear();
    expressions.put("Key5", "RANDOM-${ABC.hello");
    assertThat(infrastructureProvisionerServiceImpl.areExpressionsValid(provisioner, expressions)).isTrue();
    expressions.put("Key5", "RANDOM-${ABC.hello}");
    assertThat(infrastructureProvisionerServiceImpl.areExpressionsValid(provisioner, expressions)).isFalse();
    doReturn(false).when(featureFlagService).isEnabled(eq(VALIDATE_PROVISIONER_EXPRESSION), any());
    assertThat(infrastructureProvisionerServiceImpl.areExpressionsValid(provisioner, expressions)).isTrue();
  }

  @Test
  @Owner(developers = NAVNEET)
  @Category(UnitTests.class)
  public void testExtractTextVariablesWithDuplicateKey() {
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("someKey").value("someValue").valueType("TEXT").build(),
            NameValuePair.builder().name("someKey").value("someOtherValue").valueType("TEXT").build());

    when(executionContext.renderExpression(eq("someValue"))).thenReturn("someValue");
    when(executionContext.renderExpression(eq("someOtherValue"))).thenReturn("someOtherValue");

    assertThatThrownBy(() -> infrastructureProvisionerService.extractTextVariables(nameValuePairList, executionContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Duplicate key: someKey");
  }

  @Test
  @Owner(developers = NAVNEET)
  @Category(UnitTests.class)
  public void testExtractEncryptedTextVariablesWithDuplicateKey() {
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("someKey").value("someValue").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("someKey").value("someOtherValue").valueType("ENCRYPTED_TEXT").build());
    Optional<EncryptedDataDetail> encryptedDataDetailOptional =
        Optional.of(EncryptedDataDetail.builder().fieldName("fieldName").build());

    when(secretManager.encryptedDataDetails(any(), any(), any(), any())).thenReturn(encryptedDataDetailOptional);
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);

    assertThatThrownBy(()
                           -> infrastructureProvisionerService.extractEncryptedTextVariables(
                               nameValuePairList, APP_ID, WORKFLOW_EXECUTION_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Duplicate encrypted key: someKey");
  }
}
