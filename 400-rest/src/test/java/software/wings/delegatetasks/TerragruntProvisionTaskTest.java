/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.git.model.GitRepositoryType.TERRAGRUNT;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.APPLY;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.DESTROY;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommandUnit.Apply;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommandUnit.Destroy;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.COMMIT_REFERENCE;
import static software.wings.utils.WingsTestConstants.ENTITY_ID;
import static software.wings.utils.WingsTestConstants.SOURCE_REPO_SETTINGS_ID;
import static software.wings.utils.WingsTestConstants.WORKSPACE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliResponse;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.TaskData;
import io.harness.filesystem.FileIo;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.terragrunt.PlanJsonLogOutputStream;
import io.harness.terragrunt.TerragruntCliCommandRequestParams;
import io.harness.terragrunt.TerragruntClient;
import io.harness.terragrunt.TerragruntDelegateTaskOutput;

import software.wings.api.terragrunt.TerragruntExecutionData;
import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.KmsConfig;
import software.wings.beans.delegation.TerragruntProvisionParameters;
import software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand;
import software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommandUnit;
import software.wings.service.impl.yaml.GitClientHelper;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.yaml.GitClient;
import software.wings.utils.WingsTestConstants;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class TerragruntProvisionTaskTest extends CategoryTest {
  @Mock private GitClient gitClient;
  @Mock private GitClientHelper gitClientHelper;
  @Mock private EncryptionService encryptionService;
  @Mock private DelegateFileManager delegateFileManager;
  @Mock private EncryptDecryptHelper encryptDecryptHelper;
  @Mock private TerragruntProvisionTaskHelper terragruntProvisionTaskHelper;
  @Mock private TerragruntClient terragruntClient;
  @Mock private DelegateLogService delegateLogService;
  @Mock private TerragruntRunAllTaskHandler terragruntRunAllTaskHandler;
  @Mock private TerragruntApplyDestroyTaskHandler terragruntApplyDestroyTaskHandler;

  @InjectMocks
  TerragruntProvisionTask terragruntProvisionTask =
      new TerragruntProvisionTask(DelegateTaskPackage.builder()
                                      .delegateId(WingsTestConstants.DELEGATE_ID)
                                      .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                                      .build(),
          null, delegateTaskResponse -> {}, () -> true);

  private static final String GIT_BRANCH = "test/git_branch";
  private static final String GIT_REPO_DIRECTORY = "repository/terragruntTest";
  private static final String WORKSPACE_COMMAND_OUTPUT = "* w1\n w2\n w3";
  private static final String PATH_TO_MODULE = "scriptPath";
  private static final String RUN_PLAN_ONLY = "runPlanOnly";
  private static final String EXPORT_PLAN_TO_APPLY_STEP = "exportPlanToApplyStep";
  private static final String SAVE_TERRAGRUNT_JSON = "saveTerragruntJson";
  private static final String SKIP_REFRESH = "skipRefresh";
  private static final String RUN_ALL = "runAll";
  private static final String USE_AUTO_APPROVED_FLAG = "useAutoApproveFlag";

  private GitConfig gitConfig;
  private Map<String, EncryptedDataDetail> encryptedBackendConfigs;
  private Map<String, EncryptedDataDetail> encryptedEnvironmentVariables;
  private EncryptedDataDetail encryptedDataDetail;
  private List<EncryptedDataDetail> sourceRepoEncryptionDetails;
  private final EncryptedRecordData encryptedPlanContent =
      EncryptedRecordData.builder().name("planName").encryptedValue("encryptedPlan".toCharArray()).build();
  private final byte[] planContent = "terraformPlanContent".getBytes();
  private final CliResponse cliResponseSuccess = CliResponse.builder().commandExecutionStatus(SUCCESS).build();

  @Before
  public void setUp() throws Exception {
    initMocks(this);

    gitConfig = GitConfig.builder().branch(GIT_BRANCH).gitRepoType(TERRAGRUNT).build();
    gitConfig.setReference(COMMIT_REFERENCE);

    encryptedBackendConfigs = new HashMap<>();
    encryptedDataDetail = EncryptedDataDetail.builder()
                              .encryptedData(EncryptedRecordData.builder().uuid(WingsTestConstants.UUID).build())
                              .build();
    encryptedBackendConfigs.put("var2", encryptedDataDetail);

    encryptedEnvironmentVariables = new HashMap<>();
    encryptedEnvironmentVariables.put("key", encryptedDataDetail);

    sourceRepoEncryptionDetails = new ArrayList<>();
    sourceRepoEncryptionDetails.add(EncryptedDataDetail.builder().build());

    doReturn(GIT_REPO_DIRECTORY).when(gitClientHelper).getRepoDirectory(any(GitOperationContext.class));

    doReturn("latestCommit")
        .when(terragruntProvisionTaskHelper)
        .getLatestCommitSHAFromLocalRepo(any(GitOperationContext.class));

    doReturn(new char[] {'v', 'a', 'l', '2'}).when(encryptionService).getDecryptedValue(encryptedDataDetail, false);
    doReturn(planContent).when(encryptDecryptHelper).getDecryptedContent(any(), any());
    doReturn(true).when(encryptDecryptHelper).deleteEncryptedRecord(any(), any());

    when(delegateFileManager.upload(any(DelegateFile.class), any(InputStream.class))).thenReturn(new DelegateFile());
    doReturn(CliResponse.builder().commandExecutionStatus(SUCCESS).output(WORKSPACE_COMMAND_OUTPUT).build())
        .when(terragruntClient)
        .workspaceList(anyString(), anyLong());
    doReturn(cliResponseSuccess).when(terragruntClient).init(any(), any());
    doReturn(cliResponseSuccess).when(terragruntClient).version(any(TerragruntCliCommandRequestParams.class), any());
    doReturn(cliResponseSuccess).when(terragruntClient).refresh(any(), any(), any(), any(), any());
    doReturn(cliResponseSuccess).when(terragruntClient).workspace(any(), any(), any(), any());
    URL url = this.getClass().getResource("/terragrunt/terragrunt-info.json");
    String terragruntInfoJson = Resources.toString(url, Charsets.UTF_8);
    doReturn(CliResponse.builder().commandExecutionStatus(SUCCESS).output(terragruntInfoJson).build())
        .when(terragruntClient)
        .terragruntInfo(any(), any());
    doReturn(TerragruntDelegateTaskOutput.builder()
                 .cliResponse(cliResponseSuccess)
                 .planJsonLogOutputStream(new PlanJsonLogOutputStream())
                 .build())
        .when(terragruntApplyDestroyTaskHandler)
        .executeApplyTask(any(TerragruntProvisionParameters.class), any(TerragruntCliCommandRequestParams.class),
            any(DelegateLogService.class), anyString(), anyString());

    doCallRealMethod().when(terragruntProvisionTaskHelper).shouldSkipRefresh(any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetWorkspacesList() throws InterruptedException, IOException, TimeoutException {
    assertThat(Arrays.asList("w1", "w2", "w3").equals(terragruntProvisionTask.getWorkspacesList("scriptDir", 500L)))
        .isTrue();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllApply() throws IOException, TimeoutException, InterruptedException {
    setupForApply();
    // regular run-all apply
    Map<String, Boolean> featuresMap = new HashMap<>();
    featuresMap.put(RUN_PLAN_ONLY, false);
    featuresMap.put(EXPORT_PLAN_TO_APPLY_STEP, false);
    featuresMap.put(SAVE_TERRAGRUNT_JSON, false);
    featuresMap.put(SKIP_REFRESH, false);
    featuresMap.put(RUN_ALL, true);
    featuresMap.put(USE_AUTO_APPROVED_FLAG, false);
    TerragruntProvisionParameters terragruntProvisionParameters =
        createTerragruntProvisionParameters(null, Apply, APPLY, featuresMap);
    doReturn(TerragruntDelegateTaskOutput.builder()
                 .cliResponse(cliResponseSuccess)
                 .planJsonLogOutputStream(new PlanJsonLogOutputStream())
                 .build())
        .when(terragruntRunAllTaskHandler)
        .executeRunAllTask(any(TerragruntProvisionParameters.class), any(TerragruntCliCommandRequestParams.class),
            any(DelegateLogService.class), any(TerragruntCommand.class));
    TerragruntExecutionData terragruntExecutionData = terragruntProvisionTask.run(terragruntProvisionParameters);
    verify(terragruntExecutionData, APPLY, true);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunApplySpecificModule() throws IOException {
    setupForApply();
    TerragruntProvisionParameters terragruntProvisionParameters =
        createTerragruntProvisionParameters(false, false, null, Apply, APPLY, false, false, false);

    doReturn(new File(GIT_REPO_DIRECTORY.concat("/scriptPath/backend_configs-ENTITY_ID")))
        .when(terragruntProvisionTaskHelper)
        .getTerraformStateFile(anyString(), anyString());

    TerragruntExecutionData terragruntExecutionData = terragruntProvisionTask.run(terragruntProvisionParameters);
    Mockito.verify(terragruntProvisionTaskHelper, times(1)).getTerraformStateFile(anyString(), anyString());
    verify(terragruntExecutionData, APPLY, false);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testApplyUsingApprovedPlan() throws IOException, TimeoutException, InterruptedException {
    setupForApply();

    TerragruntProvisionParameters terragruntProvisionParameters =
        createTerragruntProvisionParameters(false, false, encryptedPlanContent, Apply, APPLY, false, false, false);

    TerragruntExecutionData terragruntExecutionData = terragruntProvisionTask.run(terragruntProvisionParameters);

    verify(terragruntExecutionData, APPLY, false);

    Mockito.verify(terragruntProvisionTaskHelper, times(1)).getTerraformStateFile(anyString(), anyString());
    Mockito.verify(encryptDecryptHelper, times(1)).deleteEncryptedRecord(any(), any());
    Mockito.verify(terragruntClient, times(1)).refresh(any(), any(), any(), any(), any());
    Mockito.verify(terragruntApplyDestroyTaskHandler, times(1))
        .executeApplyTask(any(TerragruntProvisionParameters.class), any(TerragruntCliCommandRequestParams.class),
            any(DelegateLogService.class), anyString(), anyString());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testApplyUsingApprovedPlanRefreshRefreshTrue()
      throws IOException, TimeoutException, InterruptedException {
    setupForApply();

    TerragruntProvisionParameters terragruntProvisionParameters =
        createTerragruntProvisionParameters(false, false, encryptedPlanContent, Apply, APPLY, false, true, false);
    TerragruntExecutionData terragruntExecutionData = terragruntProvisionTask.run(terragruntProvisionParameters);

    Mockito.verify(terragruntClient, never()).refresh(any(), any(), any(), any(), any());
    Mockito.verify(encryptDecryptHelper, times(1)).deleteEncryptedRecord(any(), any());
    Mockito.verify(terragruntApplyDestroyTaskHandler, times(1))
        .executeApplyTask(any(TerragruntProvisionParameters.class), any(TerragruntCliCommandRequestParams.class),
            any(DelegateLogService.class), anyString(), anyString());
    verify(terragruntExecutionData, APPLY, false);
  }

  /**
   *  should not skip refresh since not using approved plan
   */
  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPlanAndExport() throws IOException, TimeoutException, InterruptedException {
    setupForApply();
    // run plan only and execute terraform show command
    byte[] terraformPlan = "terraformPlan".getBytes();
    TerragruntProvisionParameters terragruntProvisionParameters =
        createTerragruntProvisionParameters(true, true, null, Apply, APPLY, true, false, false);
    doReturn(terraformPlan).when(terragruntProvisionTaskHelper).getTerraformPlanFile(anyString(), anyString());
    doReturn(encryptedPlanContent).when(encryptDecryptHelper).encryptContent(any(), any(), any());
    TerragruntExecutionData terragruntExecutionData = terragruntProvisionTask.run(terragruntProvisionParameters);
    verify(terragruntExecutionData, APPLY, false);
    Mockito.verify(terragruntClient, times(1)).refresh(any(), any(), any(), any(), any());
    Mockito.verify(terragruntApplyDestroyTaskHandler, times(1))
        .executeApplyTask(any(TerragruntProvisionParameters.class), any(TerragruntCliCommandRequestParams.class),
            any(DelegateLogService.class), anyString(), anyString());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunDestroyNoPlanExported() throws IOException, TimeoutException, InterruptedException {
    setupForDestroyTests();

    Map<String, Boolean> featuresMap = new HashMap<>();
    featuresMap.put(RUN_PLAN_ONLY, false);
    featuresMap.put(EXPORT_PLAN_TO_APPLY_STEP, false);
    featuresMap.put(SAVE_TERRAGRUNT_JSON, false);
    featuresMap.put(SKIP_REFRESH, false);
    featuresMap.put(RUN_ALL, false);
    featuresMap.put(USE_AUTO_APPROVED_FLAG, true);
    // regular destroy with no plan exported
    TerragruntProvisionParameters terragruntProvisionParameters =
        createTerragruntProvisionParameters(null, Destroy, DESTROY, featuresMap);
    ArgumentCaptor<TerragruntCliCommandRequestParams> cliParams =
        ArgumentCaptor.forClass(TerragruntCliCommandRequestParams.class);
    doReturn("-auto_approve")
        .when(terragruntProvisionTaskHelper)
        .getTfAutoApproveArgument(any(TerragruntCliCommandRequestParams.class), anyString());

    TerragruntExecutionData terragruntExecutionData = terragruntProvisionTask.run(terragruntProvisionParameters);

    verify(terragruntExecutionData, DESTROY, false);
    Mockito.verify(terragruntProvisionTaskHelper, times(1))
        .getTfAutoApproveArgument(any(TerragruntCliCommandRequestParams.class), eq("terraform"));

    Mockito.verify(terragruntApplyDestroyTaskHandler, times(1))
        .executeDestroyTask(any(TerragruntProvisionParameters.class), cliParams.capture(),
            any(DelegateLogService.class), anyString(), anyString());

    assertThat(cliParams.getValue().getAutoApproveArgument()).isEqualTo("-auto_approve");

    FileIo.deleteDirectoryAndItsContentIfExists(GIT_REPO_DIRECTORY);
    FileIo.deleteDirectoryAndItsContentIfExists("./terraform-working-dir");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDestroyRunPlanOnly() throws InterruptedException, TimeoutException, IOException {
    setupForDestroyTests();

    byte[] terraformDestroyPlan = "terraformDestroyPlan".getBytes();
    TerragruntProvisionParameters terraformProvisionParameters =
        createTerragruntProvisionParameters(true, true, null, Destroy, DESTROY, true, false, false);
    doReturn(terraformDestroyPlan).when(terragruntProvisionTaskHelper).getTerraformPlanFile(anyString(), anyString());
    doReturn(encryptedPlanContent).when(encryptDecryptHelper).encryptContent(any(), any(), any());
    TerragruntExecutionData terragruntExecutionData = terragruntProvisionTask.run(terraformProvisionParameters);
    Mockito.verify(terragruntApplyDestroyTaskHandler, times(1))
        .executeDestroyTask(any(TerragruntProvisionParameters.class), any(TerragruntCliCommandRequestParams.class),
            any(DelegateLogService.class), anyString(), anyString());
    verify(terragruntExecutionData, DESTROY, false);
    Mockito.verify(terragruntClient, times(1)).refresh(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetLogSannitizerForEncryptedVars() {
    Map<String, EncryptedDataDetail> encryptedVariables = new HashMap<>();
    encryptedVariables.put("k1", encryptedDataDetail);
    when(encryptionService.getDecryptedValue(any(EncryptedDataDetail.class), anyBoolean()))
        .thenReturn(new char[] {'v', '1'})
        .thenReturn(new char[] {'v', '1'})
        .thenReturn(new char[] {'v', '2'});
    Optional<LogSanitizer> logSanitizer = terragruntProvisionTask.getLogSannitizerForEncryptedVars(
        "activityId", encryptedVariables, encryptedBackendConfigs, encryptedEnvironmentVariables);
    assertThat(logSanitizer.isPresent()).isTrue();
    Mockito.verify(encryptionService, times(3)).getDecryptedValue(any(EncryptedDataDetail.class), anyBoolean());
  }

  private void setupForApply() throws IOException {
    FileIo.createDirectoryIfDoesNotExist(GIT_REPO_DIRECTORY.concat("/scriptPath"));
    FileIo.writeFile(GIT_REPO_DIRECTORY.concat("/scriptPath/backend_configs-ENTITY_ID"), new byte[] {});
    FileIo.writeFile(GIT_REPO_DIRECTORY.concat("/scriptPath/terraform-ENTITY_ID.tfvars"), new byte[] {});
  }

  private TerragruntProvisionParameters createTerragruntProvisionParameters(boolean runPlanOnly,
      boolean exportPlanToApplyStep, EncryptedRecordData encryptedTfPlan, TerragruntCommandUnit commandUnit,
      TerragruntCommand command, boolean saveTerragruntJson, boolean skipRefresh, boolean runAll) {
    Map<String, String> backendConfigs = new HashMap<>();
    backendConfigs.put("var1", "value1");

    Map<String, String> variables = new HashMap<>();
    variables.put("var3", "val3");

    Map<String, String> environmentVariables = new HashMap<>();
    environmentVariables.put("TF_LOG", "TRACE");

    List<String> tfVarFiles = Arrays.asList("tfVarFile");

    return TerragruntProvisionParameters.builder()
        .sourceRepo(gitConfig)
        .sourceRepoSettingId(SOURCE_REPO_SETTINGS_ID)
        .sourceRepoEncryptionDetails(sourceRepoEncryptionDetails)
        .command(command)
        .commandUnit(commandUnit)
        .accountId(ACCOUNT_ID)
        .workspace(WORKSPACE)
        .entityId(ENTITY_ID)
        .backendConfigs(backendConfigs)
        .encryptedBackendConfigs(encryptedBackendConfigs)
        .encryptedTfPlan(encryptedTfPlan)
        .runPlanOnly(runPlanOnly)
        .exportPlanToApplyStep(exportPlanToApplyStep)
        .variables(variables)
        .environmentVariables(environmentVariables)
        .encryptedEnvironmentVariables(encryptedEnvironmentVariables)
        .tfVarFiles(tfVarFiles)
        .saveTerragruntJson(saveTerragruntJson)
        .skipRefreshBeforeApplyingPlan(skipRefresh)
        .secretManagerConfig(KmsConfig.builder().name("config").uuid("uuid").build())
        .runAll(runAll)
        .pathToModule(PATH_TO_MODULE)
        .build();
  }

  private TerragruntProvisionParameters createTerragruntProvisionParameters(EncryptedRecordData encryptedTfPlan,
      TerragruntCommandUnit commandUnit, TerragruntCommand command, Map<String, Boolean> featuresMap) {
    Map<String, String> backendConfigs = new HashMap<>();
    backendConfigs.put("var1", "value1");

    Map<String, String> variables = new HashMap<>();
    variables.put("var3", "val3");

    Map<String, String> environmentVariables = new HashMap<>();
    environmentVariables.put("TF_LOG", "TRACE");

    List<String> tfVarFiles = Arrays.asList("tfVarFile");

    return TerragruntProvisionParameters.builder()
        .sourceRepo(gitConfig)
        .sourceRepoSettingId(SOURCE_REPO_SETTINGS_ID)
        .sourceRepoEncryptionDetails(sourceRepoEncryptionDetails)
        .command(command)
        .commandUnit(commandUnit)
        .accountId(ACCOUNT_ID)
        .workspace(WORKSPACE)
        .entityId(ENTITY_ID)
        .backendConfigs(backendConfigs)
        .encryptedBackendConfigs(encryptedBackendConfigs)
        .encryptedTfPlan(encryptedTfPlan)
        .runPlanOnly(featuresMap.get(RUN_PLAN_ONLY))
        .exportPlanToApplyStep(featuresMap.get(EXPORT_PLAN_TO_APPLY_STEP))
        .variables(variables)
        .environmentVariables(environmentVariables)
        .encryptedEnvironmentVariables(encryptedEnvironmentVariables)
        .tfVarFiles(tfVarFiles)
        .saveTerragruntJson(featuresMap.get(SAVE_TERRAGRUNT_JSON))
        .skipRefreshBeforeApplyingPlan(featuresMap.get(SKIP_REFRESH))
        .secretManagerConfig(KmsConfig.builder().name("config").uuid("uuid").build())
        .runAll(featuresMap.get(RUN_ALL))
        .pathToModule(PATH_TO_MODULE)
        .useAutoApproveFlag(featuresMap.get(USE_AUTO_APPROVED_FLAG))
        .build();
  }

  private void verify(TerragruntExecutionData terragruntExecutionData, TerragruntCommand command, boolean runAll) {
    Mockito.verify(encryptionService, times(1)).decrypt(gitConfig, sourceRepoEncryptionDetails, false);
    Mockito.verify(gitClient, times(1)).ensureRepoLocallyClonedAndUpdated(any(GitOperationContext.class));
    Mockito.verify(gitClientHelper, times(1)).getRepoDirectory(any(GitOperationContext.class));
    Mockito.verify(delegateFileManager, times(runAll ? 0 : 1)).upload(any(DelegateFile.class), any(InputStream.class));
    assertThat(terragruntExecutionData.getWorkspace()).isEqualTo(WORKSPACE);
    assertThat(terragruntExecutionData.getEntityId()).isEqualTo(ENTITY_ID);
    assertThat(terragruntExecutionData.getCommandExecuted()).isEqualTo(command);
    assertThat(terragruntExecutionData.getSourceRepoReference()).isEqualTo("latestCommit");
    assertThat(terragruntExecutionData.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  private void setupForDestroyTests() throws IOException, TimeoutException, InterruptedException {
    setupForApply();

    doReturn(new ByteArrayInputStream(new byte[] {}))
        .when(delegateFileManager)
        .downloadByFileId(any(FileBucket.class), anyString(), anyString());
    doReturn(TerragruntDelegateTaskOutput.builder()
                 .cliResponse(cliResponseSuccess)
                 .planJsonLogOutputStream(new PlanJsonLogOutputStream())
                 .build())
        .when(terragruntApplyDestroyTaskHandler)
        .executeDestroyTask(any(TerragruntProvisionParameters.class), any(TerragruntCliCommandRequestParams.class),
            any(DelegateLogService.class), anyString(), anyString());
  }
}
