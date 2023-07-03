/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.SweepingOutputInstance.builder;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.provision.TerraformConstants.ENCRYPTED_BACKEND_CONFIGS_KEY;
import static io.harness.provision.TerraformConstants.ENCRYPTED_VARIABLES_KEY;
import static io.harness.provision.TerraformConstants.VARIABLES_KEY;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.APPLY;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.DESTROY;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.handleDefaultWorkspace;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.isSecretManagerRequired;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.parseTerragruntOutputs;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.resolveTargets;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.validateTerragruntVariables;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.FileMetadata;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;

import software.wings.api.terraform.TfVarGitSource;
import software.wings.api.terragrunt.TerragruntApplyMarkerParam;
import software.wings.api.terragrunt.TerragruntExecutionData;
import software.wings.api.terragrunt.TerragruntOutputVariables;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.beans.infrastructure.TerraformConfig;
import software.wings.beans.infrastructure.instance.TerragruntConfig;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.sm.states.provision.TerraformPlanHelper;
import software.wings.utils.GitUtilsManager;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class TerragruntStateHelperTest extends CategoryTest {
  @Mock InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock Query query;
  @Mock private DelegateService delegateService;
  @Mock private ActivityService activityService;
  @Mock private GitUtilsManager gitUtilsManager;
  @Mock MainConfiguration configuration;
  @Mock private FileService fileService;
  @Mock private SecretManager secretManager;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private ExecutionContextImpl executionContext;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private GitConfigHelperService gitConfigHelperService;
  @Mock private TerraformPlanHelper terraformPlanHelper;
  @Mock private SecretManagerConfigService secretManagerConfigService;
  @Spy private GitFileConfigHelperService gitFileConfigHelperService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ManagerExecutionLogCallback managerExecutionLogCallback;
  @InjectMocks @Inject private TerragruntStateHelper terragruntStateHelper;

  private static final String PROVISIONER_ID = "PROVISIONER_ID";
  private static final String PATH_TO_MODULE = "aws-module";
  private static final String ENTITY_ID = "ENTITY_ID";
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  private final Answer<String> answer = invocation -> invocation.getArgument(0, String.class) + "-rendered";

  String multiModuleOutput = "{\n"
      + "  \"regiona\": {\n"
      + "    \"sensitive\": false,\n"
      + "    \"type\": \"string\",\n"
      + "    \"value\": \"us-east-1\"\n"
      + "  }\n"
      + "}\n"
      + "{\n"
      + "  \"regionb\": {\n"
      + "    \"sensitive\": false,\n"
      + "    \"type\": \"string\",\n"
      + "    \"value\": \"us-east-2\"\n"
      + "  }\n"
      + "}\n";

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    doReturn(SweepingOutputInquiry.builder()).when(executionContext).prepareSweepingOutputInquiryBuilder();
    doReturn(builder()).when(executionContext).prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testParseMultipleModuleOutputs() throws IOException {
    assertThat(parseTerragruntOutputs(null).size()).isEqualTo(0);
    assertThat(parseTerragruntOutputs("").size()).isEqualTo(0);
    assertThat(parseTerragruntOutputs("  ").size()).isEqualTo(0);

    // specific module
    InputStream in = getClass().getResourceAsStream("terragrunt_output.json");
    String output = IOUtils.toString(in, StandardCharsets.UTF_8);
    assertThat(parseTerragruntOutputs(output).size()).isEqualTo(2);

    // multi module
    assertThat(parseTerragruntOutputs(multiModuleOutput).size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMarkApplyExecutionCompleted() {
    ArgumentCaptor<SweepingOutputInstance> sweepingOutputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);
    terragruntStateHelper.markApplyExecutionCompleted(executionContext, PROVISIONER_ID, PATH_TO_MODULE);
    verify(sweepingOutputService).save(sweepingOutputInstanceArgumentCaptor.capture());
    SweepingOutputInstance sweepingOutputInstance = sweepingOutputInstanceArgumentCaptor.getValue();
    TerragruntApplyMarkerParam applyMarkerParam = (TerragruntApplyMarkerParam) sweepingOutputInstance.getValue();

    assertThat(sweepingOutputInstance.getName())
        .isEqualTo(format("tfApplyCompleted_%s_%s", PROVISIONER_ID, PATH_TO_MODULE));
    assertThat(applyMarkerParam.getPathToModule()).isEqualTo(PATH_TO_MODULE);
    assertThat(applyMarkerParam.getProvisionerId()).isEqualTo(PROVISIONER_ID);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testIsSecretManagerRequired() {
    // run plan only
    assertThat(isSecretManagerRequired(true, false, false, false, APPLY)).isFalse();
    // run and export plan
    assertThat(isSecretManagerRequired(true, true, false, false, APPLY)).isTrue();
    // inherit plan
    assertThat(isSecretManagerRequired(false, false, true, false, APPLY)).isTrue();
    // runPlan and export in Destroy step
    assertThat(isSecretManagerRequired(true, false, false, false, DESTROY)).isTrue();
    // run-all
    assertThat(isSecretManagerRequired(false, false, true, true, APPLY)).isFalse();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPopulateAndGetGitConfig() {
    TerragruntInfrastructureProvisioner provisioner =
        TerragruntInfrastructureProvisioner.builder().sourceRepoBranch("master").commitId("commitId").build();
    GitConfig gitConfig = GitConfig.builder().build();

    doReturn(provisioner.getSourceRepoBranch())
        .when(executionContext)
        .renderExpression(provisioner.getSourceRepoBranch());
    doReturn(provisioner.getCommitId()).when(executionContext).renderExpression(provisioner.getCommitId());
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(any());
    GitConfig gitConfigOutput = terragruntStateHelper.populateAndGetGitConfig(executionContext, provisioner);

    assertThat(gitConfigOutput.getBranch()).isEqualTo(provisioner.getSourceRepoBranch());
    assertThat(gitConfigOutput.getReference()).isEqualTo(provisioner.getCommitId());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testResolveTargest() {
    List<String> targets = Arrays.asList("target1", "target2");
    doAnswer(invocation -> invocation.getArgument(0, String.class) + "-rendered")
        .when(executionContext)
        .renderExpression(anyString());
    assertThat(resolveTargets(targets, executionContext)).contains("target1-rendered", "target2-rendered");
    assertThat(resolveTargets(Collections.emptyList(), executionContext)).isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleDefaultWorkspace() {
    assertThat(handleDefaultWorkspace("default")).isNull();
    assertThat(handleDefaultWorkspace("new-workspace")).isEqualTo("new-workspace");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExtractData() {
    FileMetadata fileMetadata =
        FileMetadata.builder()
            .metadata(ImmutableMap.<String, Object>builder()
                          .put("variables", ImmutableMap.of("region", "us-east", "vpc_id", "vpc-id"))
                          .put("environment_variables", ImmutableMap.of("TF_LOG", "TRACE"))
                          .build())
            .build();
    Class<List<NameValuePair>> listClass = (Class<List<NameValuePair>>) (Class) List.class;
    ArgumentCaptor<List<NameValuePair>> argumentCaptor = ArgumentCaptor.forClass(listClass);
    terragruntStateHelper.extractData(fileMetadata, VARIABLES_KEY);
    verify(infrastructureProvisionerService).extractUnresolvedTextVariables(argumentCaptor.capture());
    List<NameValuePair> variablesNameValuePairs = argumentCaptor.getValue();
    assertThat(variablesNameValuePairs.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExtractEncryptedData() {
    FileMetadata fileMetadata =
        FileMetadata.builder()
            .metadata(
                ImmutableMap.<String, Object>builder().put("encrypted_backend_configs", ImmutableMap.of()).build())
            .build();
    terragruntStateHelper.extractEncryptedData(executionContext, fileMetadata, ENCRYPTED_BACKEND_CONFIGS_KEY);
    verify(infrastructureProvisionerService, times(0)).extractEncryptedTextVariables(any(), any(), any());

    fileMetadata.setMetadata(
        ImmutableMap.<String, Object>builder()
            .put("encrypted_backend_configs", ImmutableMap.of("region", "us-east", "vpc_id", "vpc-id"))
            .build());
    terragruntStateHelper.extractEncryptedData(executionContext, fileMetadata, ENCRYPTED_BACKEND_CONFIGS_KEY);
    verify(infrastructureProvisionerService, times(1)).extractEncryptedTextVariables(any(), any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExtractBackendConfigs() {
    FileMetadata fileMetadata =
        FileMetadata.builder()
            .metadata(ImmutableMap.<String, Object>builder().put("backend_configs", ImmutableMap.of()).build())
            .build();
    terragruntStateHelper.extractBackendConfigs(fileMetadata);
    verify(infrastructureProvisionerService, times(0)).extractUnresolvedTextVariables(any());

    fileMetadata.setMetadata(
        ImmutableMap.<String, Object>builder()
            .put("backend_configs", ImmutableMap.of("access_key", "access-key", "secret_key", "secret-key"))
            .build());

    terragruntStateHelper.extractBackendConfigs(fileMetadata);
    verify(infrastructureProvisionerService, times(1)).extractUnresolvedTextVariables(any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCollectVariables() {
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("key").value("value").valueType("TEXT").build(),
            NameValuePair.builder().name("password").value("password").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType(null).build());
    Map<String, Object> others = new HashMap<>();
    terragruntStateHelper.collectVariables(others, nameValuePairList, VARIABLES_KEY, ENCRYPTED_VARIABLES_KEY, true);
    assertThat(others.keySet()).contains(VARIABLES_KEY, ENCRYPTED_VARIABLES_KEY);
    Map<String, String> variables = (Map<String, String>) others.get(VARIABLES_KEY);
    assertThat(variables.keySet()).contains("key", "noValueType");
    Map<String, String> encryptedVariables = (Map<String, String>) others.get(ENCRYPTED_VARIABLES_KEY);
    assertThat(encryptedVariables.keySet()).contains("password");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveOutputs() {
    Map<String, Object> outputValues = new HashMap<>();
    outputValues.put("output1", "valuea1");
    outputValues.put("output2", "valuea2");
    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW);
    terragruntStateHelper.saveOutputs(executionContext, outputValues);

    ArgumentCaptor<SweepingOutputInstance> sweepingOutputCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(1)).save(sweepingOutputCaptor.capture());
    verify(sweepingOutputService, never()).deleteById(anyString(), anyString());
    assertThat(((TerragruntOutputVariables) sweepingOutputCaptor.getValue().getValue()).keySet())
        .contains("output1", "output2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveOutputsWithExistingOutput() {
    Map<String, Object> outputValues = new HashMap<>();
    outputValues.put("output1", "valuea1");
    outputValues.put("output2", "valuea2");
    TerragruntOutputVariables existingOutputVariables = new TerragruntOutputVariables();
    existingOutputVariables.put("existing", "value3");
    SweepingOutputInstance existingVariablesOutputs = SweepingOutputInstance.builder()
                                                          .name(TerragruntOutputVariables.SWEEPING_OUTPUT_NAME)
                                                          .value(existingOutputVariables)
                                                          .uuid("UUID")
                                                          .build();

    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW);

    doReturn(existingVariablesOutputs).when(sweepingOutputService).find(any());
    terragruntStateHelper.saveOutputs(executionContext, outputValues);

    ArgumentCaptor<SweepingOutputInstance> sweepingOutputCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(1)).save(sweepingOutputCaptor.capture());
    verify(sweepingOutputService, times(1)).deleteById(any(), anyString());
    assertThat(((TerragruntOutputVariables) sweepingOutputCaptor.getValue().getValue()).keySet())
        .contains("output1", "output2", "existing");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveTerragruntConfig() {
    TfVarGitSource tfVarGitSource = TfVarGitSource.builder().gitFileConfig(GitFileConfig.builder().build()).build();
    TerragruntExecutionData executionData = TerragruntExecutionData.builder()
                                                .executionStatus(ExecutionStatus.SUCCESS)
                                                .workspace("workspace")
                                                .targets(Collections.emptyList())
                                                .tfVarFiles(asList("file-1", "file-2"))
                                                .targets(asList("target1", "target2"))
                                                .commandExecuted(APPLY)
                                                .tfVarSource(tfVarGitSource)
                                                .pathToModule("aws-module")
                                                .runAll(true)
                                                .build();
    terragruntStateHelper.saveTerragruntConfig(executionContext, "sourceRepoSettingId", executionData, ENTITY_ID);

    ArgumentCaptor<TerragruntConfig> configArgumentCaptor = ArgumentCaptor.forClass(TerragruntConfig.class);
    verify(wingsPersistence).save(configArgumentCaptor.capture());
    TerragruntConfig terragruntConfig = configArgumentCaptor.getValue();
    assertThat(terragruntConfig.getTfVarGitFileConfig()).isEqualTo(tfVarGitSource.getGitFileConfig());
    assertThat(terragruntConfig.getPathToModule()).isEqualTo(executionData.getPathToModule());
    assertThat(terragruntConfig.getTerragruntCommand()).isEqualTo(executionData.getCommandExecuted());
    assertThat(terragruntConfig.isRunAll()).isEqualTo(executionData.isRunAll());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteTerragruntConfig() {
    doReturn(query).when(wingsPersistence).createQuery(any());
    doReturn(query).when(query).filter(any(), any());
    terragruntStateHelper.deleteTerragruntConfig(ENTITY_ID);

    verify(wingsPersistence, times(1)).delete(any(Query.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteTerragruntConfiguUsingOekflowExecutionId() {
    doReturn(query).when(wingsPersistence).createQuery(any());
    doReturn(query).when(query).filter(any(), any());
    terragruntStateHelper.deleteTerragruntConfiguUsingOekflowExecutionId(executionContext, ENTITY_ID);
    verify(query, times(2)).filter(any(), any());
    verify(wingsPersistence, times(1)).delete(any(Query.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulWorkflowExecutionUrl() {
    TerragruntConfig terragruntConfig = TerragruntConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .appId(APP_ID)
                                            .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                            .build();
    PortalConfig portal = new PortalConfig();
    portal.setUrl("app.harness.io");
    doReturn(portal).when(configuration).getPortal();
    StringBuilder workflowExecutionUrl =
        terragruntStateHelper.getLastSuccessfulWorkflowExecutionUrl(terragruntConfig, executionContext);
    assertThat(workflowExecutionUrl.toString())
        .isEqualTo("app.harness.io/#/account/ACCOUNT_ID/app/APP_ID/env/null/executions/WORKFLOW_EXECUTION_ID/details");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetSavedTerraformConfig() {
    doReturn(query).when(wingsPersistence).createQuery(any());
    doReturn(query).when(query).filter(any(), any());
    doReturn(query).when(query).order(ArgumentMatchers.<Sort>any());

    terragruntStateHelper.getSavedTerraformConfig(APP_ID, ENTITY_ID);
    verify(wingsPersistence).createQuery(TerraformConfig.class);
    verify(query).order(ArgumentMatchers.<Sort>any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetGitConfigAndPopulate() {
    TerragruntConfig terragruntConfig =
        TerragruntConfig.builder().sourceRepoReference("sourceRepoReference").sourceRepoSettingId("settingId").build();
    doReturn(GitConfig.builder().build()).when(gitUtilsManager).getGitConfig(any());
    GitConfig gitConfig = terragruntStateHelper.getGitConfigAndPopulate(terragruntConfig, "branch");
    verify(gitUtilsManager, times(1)).getGitConfig(terragruntConfig.getSourceRepoSettingId());
    assertThat(gitConfig.getBranch()).isEqualTo("branch");
    assertThat(gitConfig.getReference()).isEqualTo(terragruntConfig.getSourceRepoReference());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidateTerragruntVariables() {
    List<NameValuePair> variablesContainDuplicate =
        asList(NameValuePair.builder().name("key").value("value").valueType("TEXT").build(),
            NameValuePair.builder().name("key").value("value").valueType("TEXT").build());
    List<NameValuePair> backendConfig =
        singletonList(NameValuePair.builder().name("key").value("value").valueType("TEXT").build());
    List<NameValuePair> envVar =
        singletonList(NameValuePair.builder().name("key").value("value").valueType("TEXT").build());
    assertThatThrownBy(() -> validateTerragruntVariables(variablesContainDuplicate, backendConfig, envVar))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("variable names should be unique");

    List<NameValuePair> variablesNameContainingExp =
        asList(NameValuePair.builder().name("key").value("value").valueType("TEXT").build(),
            NameValuePair.builder().name("${workflow.variables.var}").value("value").valueType("TEXT").build());
    assertThatThrownBy(() -> validateTerragruntVariables(variablesNameContainingExp, backendConfig, envVar))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The following characters are not allowed in terragrunt");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetSecretManagerContainingTfPlan() {
    doReturn(true).when(featureFlagService).isEnabled(any(), any());
    when(secretManagerConfigService.getSecretManager(any(), any(), anyBoolean()))
        .thenReturn(
            GcpKmsConfig.builder().accountId(GLOBAL_ACCOUNT_ID).credentials("credential".toCharArray()).build());
    GcpKmsConfig secretManagerConfig =
        (GcpKmsConfig) terragruntStateHelper.getSecretManagerContainingTfPlan("smId", ACCOUNT_ID);
    assertThat(secretManagerConfig.getCredentials()).isEqualTo(SECRET_MASK.toCharArray());
  }
}
