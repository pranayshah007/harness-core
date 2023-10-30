/*

  * Copyright 2022 Harness Inc. All rights reserved.
  * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
  * that can be found in the licenses directory at the root of this repository, also available at
  * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.delegate.task.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_BACKEND_FILE_SOURCE_REF;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_BE_FILES_DIR;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_CONFIG_FILE_SOURCE_REF;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_RUN_PATH;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_STATE_ID;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_TF_PLAN_JSON;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_VAR_FILES_DIR;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_WORKING_DIR;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliCommandRequest;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.terragrunt.request.TerragruntPlanTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntPlanTaskResponse;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.exception.runtime.TerragruntCliRuntimeException;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedRecordData;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.jose4j.lang.JoseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class TerragruntPlanTaskNGTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private LogCallback logCallback;
  @Mock private TerragruntTaskService taskService;
  @Mock private EncryptDecryptHelper encryptDecryptHelper;
  @Mock private TerraformBaseHelper terraformHelper;
  @Mock private CliHelper cliHelper;

  @Mock PlanJsonLogOutputStream planJsonLogOutputStream;

  private final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build();

  @InjectMocks
  private TerragruntPlanTaskNG terragruntPlanTaskNG =
      new TerragruntPlanTaskNG(delegateTaskPackage, null, response -> {}, () -> true);

  EncryptedRecordData encryptedPlanContent =
      EncryptedRecordData.builder().name("planName").encryptedValue("encryptedPlan".toCharArray()).build();

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPlanRunModule() throws JoseException, IOException, InterruptedException, TimeoutException {
    TerragruntRunConfiguration runConfiguration =
        TerragruntRunConfiguration.builder().runType(TerragruntTaskRunType.RUN_MODULE).path(TG_RUN_PATH).build();
    TerragruntPlanTaskParameters planParameters = TerragruntTestUtils.createPlanTaskParameters(runConfiguration);

    TerragruntContext terragruntContext = TerragruntTestUtils.createTerragruntContext(cliHelper);
    when(taskService.prepareTerragrunt(
             any(), any(), eq("./terragrunt-working-dir/test-account-ID/test-entity-ID"), any()))
        .thenReturn(terragruntContext);
    doNothing().when(taskService).decryptTaskParameters(any());
    doReturn(logCallback).when(taskService).getLogCallback(any(), any(), any());
    when(cliHelper.executeCliCommand(any()))
        .thenReturn(CliResponse.builder().exitCode(0).build())
        .thenReturn(
            CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).exitCode(0).output("").build())
        .thenReturn(CliResponse.builder().exitCode(0).build())
        .thenReturn(CliResponse.builder().exitCode(0).build())
        .thenReturn(CliResponse.builder().exitCode(0).build());
    when(terraformHelper.uploadTfPlanJson(any(), any(), any(), any(), eq("tfplan"), eq("test-tfPlanLocalPath")))
        .thenReturn(TG_TF_PLAN_JSON);
    when(taskService.uploadStateFile(eq("workingDir/"), eq("test-workspace"), any(), any(), any(), any(), any()))
        .thenReturn(TG_STATE_ID);

    doReturn(encryptedPlanContent).when(encryptDecryptHelper).encryptFile(any(), eq("test-plan-name"), any(), any());

    when(taskService.getPlanJsonLogOutputStream()).thenReturn(planJsonLogOutputStream);
    when(planJsonLogOutputStream.getTfPlanJsonLocalPath()).thenReturn("test-tfPlanLocalPath");

    FileIo.createDirectoryIfDoesNotExist(TG_WORKING_DIR);
    FileIo.writeFile(TG_WORKING_DIR + "tfplan", new byte[] {});
    FileIo.createDirectoryIfDoesNotExist(TG_BE_FILES_DIR);
    FileIo.writeFile(terragruntContext.getBackendFile(), new byte[] {});
    FileIo.createDirectoryIfDoesNotExist(TG_VAR_FILES_DIR);
    FileIo.writeFile(terragruntContext.getVarFiles().get(0), new byte[] {});

    TerragruntPlanTaskResponse response = (TerragruntPlanTaskResponse) terragruntPlanTaskNG.run(planParameters);
    assertThat(response).isNotNull();
    assertThat(response.getEncryptedPlan()).isNotNull();
    assertThat(response.getStateFileId()).isEqualTo(TG_STATE_ID);
    assertThat(response.getConfigFilesSourceReference()).isEqualTo(TG_CONFIG_FILE_SOURCE_REF);
    assertThat(response.getBackendFileSourceReference()).isEqualTo(TG_BACKEND_FILE_SOURCE_REF);
    assertThat(response.getPlanJsonFileId()).isEqualTo(TG_TF_PLAN_JSON);
    assertThat(response.getVarFilesSourceReference()).isNotNull();
    assertThat(response.getVarFilesSourceReference().get("test-varFileId-1")).isEqualTo("test-ref1");

    ArgumentCaptor<CliCommandRequest> captor = ArgumentCaptor.forClass(CliCommandRequest.class);
    verify(cliHelper, times(5)).executeCliCommand(captor.capture());

    assertThat(captor.getAllValues().get(0).getCommand())
        .isEqualTo("terragrunt init -backend-config=backendFileDirectory/test-backendFile.tfvars -lock-timeout=10s");
    assertThat(captor.getAllValues().get(0).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(1).getCommand()).isEqualTo("terragrunt workspace list");
    assertThat(captor.getAllValues().get(1).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(2).getCommand()).isEqualTo("terragrunt workspace new  test-workspace");
    assertThat(captor.getAllValues().get(2).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(3).getCommand())
        .isEqualTo(
            "terragrunt plan -out=tfplan -input=false -target=\"test-target\" -var-file=\"test-terragrunt-12345.tfvars\" -lock-timeout=10s");
    assertThat(captor.getAllValues().get(3).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(4).getCommand()).isEqualTo("terragrunt show -json tfplan");
    assertThat(captor.getAllValues().get(4).getEnvVariables()).isEqualTo(planParameters.getEnvVars());

    FileIo.deleteDirectoryAndItsContentIfExists(TG_WORKING_DIR);
    FileIo.deleteDirectoryAndItsContentIfExists(TG_BE_FILES_DIR);
    FileIo.deleteDirectoryAndItsContentIfExists(TG_VAR_FILES_DIR);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPlanRunModuleWithUniqueBaseDir()
      throws JoseException, IOException, InterruptedException, TimeoutException {
    TerragruntRunConfiguration runConfiguration =
        TerragruntRunConfiguration.builder().runType(TerragruntTaskRunType.RUN_MODULE).path(TG_RUN_PATH).build();
    TerragruntPlanTaskParameters planParameters = TerragruntTestUtils.createPlanTaskParameters(runConfiguration);

    planParameters.setUseUniqueDirectoryForBaseDir(true);

    TerragruntContext terragruntContext = TerragruntTestUtils.createTerragruntContext(cliHelper);
    when(taskService.prepareTerragrunt(
             any(), any(), contains("./terragrunt-working-dir/test-account-ID/test-entity-ID"), any()))
        .thenReturn(terragruntContext);
    doNothing().when(taskService).decryptTaskParameters(any());
    doReturn(logCallback).when(taskService).getLogCallback(any(), any(), any());
    when(cliHelper.executeCliCommand(any()))
        .thenReturn(CliResponse.builder().exitCode(0).build())
        .thenReturn(
            CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).exitCode(0).output("").build())
        .thenReturn(CliResponse.builder().exitCode(0).build())
        .thenReturn(CliResponse.builder().exitCode(0).build())
        .thenReturn(CliResponse.builder().exitCode(0).build());
    when(terraformHelper.uploadTfPlanJson(any(), any(), any(), any(), eq("tfplan"), eq("test-tfPlanLocalPath")))
        .thenReturn(TG_TF_PLAN_JSON);
    when(taskService.uploadStateFile(eq("workingDir/"), eq("test-workspace"), any(), any(), any(), any(), any()))
        .thenReturn(TG_STATE_ID);

    doReturn(encryptedPlanContent).when(encryptDecryptHelper).encryptFile(any(), eq("test-plan-name"), any(), any());

    when(taskService.getPlanJsonLogOutputStream()).thenReturn(planJsonLogOutputStream);
    when(planJsonLogOutputStream.getTfPlanJsonLocalPath()).thenReturn("test-tfPlanLocalPath");

    FileIo.createDirectoryIfDoesNotExist(TG_WORKING_DIR);
    FileIo.writeFile(TG_WORKING_DIR + "tfplan", new byte[] {});
    FileIo.createDirectoryIfDoesNotExist(TG_BE_FILES_DIR);
    FileIo.writeFile(terragruntContext.getBackendFile(), new byte[] {});
    FileIo.createDirectoryIfDoesNotExist(TG_VAR_FILES_DIR);
    FileIo.writeFile(terragruntContext.getVarFiles().get(0), new byte[] {});

    TerragruntPlanTaskResponse response = (TerragruntPlanTaskResponse) terragruntPlanTaskNG.run(planParameters);
    assertThat(response).isNotNull();
    assertThat(response.getEncryptedPlan()).isNotNull();
    assertThat(response.getStateFileId()).isEqualTo(TG_STATE_ID);
    assertThat(response.getConfigFilesSourceReference()).isEqualTo(TG_CONFIG_FILE_SOURCE_REF);
    assertThat(response.getBackendFileSourceReference()).isEqualTo(TG_BACKEND_FILE_SOURCE_REF);
    assertThat(response.getPlanJsonFileId()).isEqualTo(TG_TF_PLAN_JSON);
    assertThat(response.getVarFilesSourceReference()).isNotNull();
    assertThat(response.getVarFilesSourceReference().get("test-varFileId-1")).isEqualTo("test-ref1");

    ArgumentCaptor<CliCommandRequest> captor = ArgumentCaptor.forClass(CliCommandRequest.class);
    verify(cliHelper, times(5)).executeCliCommand(captor.capture());

    assertThat(captor.getAllValues().get(0).getCommand())
        .isEqualTo("terragrunt init -backend-config=backendFileDirectory/test-backendFile.tfvars -lock-timeout=10s");
    assertThat(captor.getAllValues().get(0).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(1).getCommand()).isEqualTo("terragrunt workspace list");
    assertThat(captor.getAllValues().get(1).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(2).getCommand()).isEqualTo("terragrunt workspace new  test-workspace");
    assertThat(captor.getAllValues().get(2).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(3).getCommand())
        .isEqualTo(
            "terragrunt plan -out=tfplan -input=false -target=\"test-target\" -var-file=\"test-terragrunt-12345.tfvars\" -lock-timeout=10s");
    assertThat(captor.getAllValues().get(3).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(4).getCommand()).isEqualTo("terragrunt show -json tfplan");
    assertThat(captor.getAllValues().get(4).getEnvVariables()).isEqualTo(planParameters.getEnvVars());

    FileIo.deleteDirectoryAndItsContentIfExists(TG_WORKING_DIR);
    FileIo.deleteDirectoryAndItsContentIfExists(TG_BE_FILES_DIR);
    FileIo.deleteDirectoryAndItsContentIfExists(TG_VAR_FILES_DIR);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPlanRunAll() throws JoseException, IOException, InterruptedException, TimeoutException {
    TerragruntRunConfiguration runConfiguration =
        TerragruntRunConfiguration.builder().runType(TerragruntTaskRunType.RUN_ALL).path(TG_RUN_PATH).build();
    TerragruntPlanTaskParameters planParameters = TerragruntTestUtils.createPlanTaskParameters(runConfiguration);

    TerragruntContext terragruntContext = TerragruntTestUtils.createTerragruntContext(cliHelper);
    when(taskService.prepareTerragrunt(
             any(), any(), eq("./terragrunt-working-dir/test-account-ID/test-entity-ID"), any()))
        .thenReturn(terragruntContext);
    doNothing().when(taskService).decryptTaskParameters(any());
    doReturn(logCallback).when(taskService).getLogCallback(any(), any(), any());
    when(cliHelper.executeCliCommand(any()))
        .thenReturn(CliResponse.builder().exitCode(0).build())
        .thenReturn(
            CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).exitCode(0).output("").build())
        .thenReturn(CliResponse.builder().exitCode(0).build())
        .thenReturn(CliResponse.builder().exitCode(0).build());

    FileIo.createDirectoryIfDoesNotExist(TG_WORKING_DIR);
    FileIo.writeFile(TG_WORKING_DIR + "tfplan", new byte[] {});
    FileIo.createDirectoryIfDoesNotExist(TG_BE_FILES_DIR);
    FileIo.writeFile(terragruntContext.getBackendFile(), new byte[] {});
    FileIo.createDirectoryIfDoesNotExist(TG_VAR_FILES_DIR);
    FileIo.writeFile(terragruntContext.getVarFiles().get(0), new byte[] {});

    TerragruntPlanTaskResponse response = (TerragruntPlanTaskResponse) terragruntPlanTaskNG.run(planParameters);
    assertThat(response).isNotNull();
    assertThat(response.getConfigFilesSourceReference()).isEqualTo(TG_CONFIG_FILE_SOURCE_REF);
    assertThat(response.getBackendFileSourceReference()).isEqualTo(TG_BACKEND_FILE_SOURCE_REF);
    assertThat(response.getVarFilesSourceReference()).isNotNull();
    assertThat(response.getVarFilesSourceReference().get("test-varFileId-1")).isEqualTo("test-ref1");

    ArgumentCaptor<CliCommandRequest> captor = ArgumentCaptor.forClass(CliCommandRequest.class);
    verify(cliHelper, times(4)).executeCliCommand(captor.capture());

    assertThat(captor.getAllValues().get(0).getCommand())
        .isEqualTo(
            "echo \"y\" | terragrunt run-all init -backend-config=backendFileDirectory/test-backendFile.tfvars -lock-timeout=10s");
    assertThat(captor.getAllValues().get(0).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(1).getCommand()).isEqualTo("echo \"y\" | terragrunt run-all workspace list");
    assertThat(captor.getAllValues().get(1).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(2).getCommand()).isEqualTo("terragrunt run-all workspace new  test-workspace");
    assertThat(captor.getAllValues().get(2).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(3).getCommand())
        .isEqualTo(
            "terragrunt run-all plan -out=tfplan -input=false --terragrunt-non-interactive -target=\"test-target\" -var-file=\"test-terragrunt-12345.tfvars\" -lock-timeout=10s");
    assertThat(captor.getAllValues().get(3).getEnvVariables()).isEqualTo(planParameters.getEnvVars());

    FileIo.deleteDirectoryAndItsContentIfExists(TG_WORKING_DIR);
    FileIo.deleteDirectoryAndItsContentIfExists(TG_BE_FILES_DIR);
    FileIo.deleteDirectoryAndItsContentIfExists(TG_VAR_FILES_DIR);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPlanThrowsAnException() throws IOException, InterruptedException, TimeoutException {
    TerragruntRunConfiguration runConfiguration =
        TerragruntRunConfiguration.builder().runType(TerragruntTaskRunType.RUN_ALL).path(TG_RUN_PATH).build();
    TerragruntPlanTaskParameters planParameters = TerragruntTestUtils.createPlanTaskParameters(runConfiguration);

    TerragruntContext terragruntContext = TerragruntTestUtils.createTerragruntContext(cliHelper);
    when(taskService.prepareTerragrunt(
             any(), any(), eq("./terragrunt-working-dir/test-account-ID/test-entity-ID"), any()))
        .thenReturn(terragruntContext);
    doNothing().when(taskService).decryptTaskParameters(any());
    doReturn(logCallback).when(taskService).getLogCallback(any(), any(), any());
    when(cliHelper.executeCliCommand(any()))
        .thenReturn(CliResponse.builder().exitCode(0).build())
        .thenReturn(CliResponse.builder()
                        .command("echo \"y\" | terragrunt run-all workspace list")
                        .error("command failed")
                        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                        .exitCode(-1)
                        .build());

    assertThatThrownBy(() -> terragruntPlanTaskNG.run(planParameters)).matches(throwable -> {
      assertThat(throwable).isInstanceOf(TaskNGDataException.class);
      assertThat(throwable.getCause()).isInstanceOf(TerragruntCliRuntimeException.class);
      assertThat(throwable.getCause().getMessage())
          .contains("Terragrunt command 'echo \"y\" | terragrunt run-all workspace list' failed with error code '-1'");
      return true;
    });

    ArgumentCaptor<CliCommandRequest> captor = ArgumentCaptor.forClass(CliCommandRequest.class);
    verify(cliHelper, times(2)).executeCliCommand(captor.capture());

    assertThat(captor.getAllValues().get(0).getCommand())
        .isEqualTo("echo \"y\" | terragrunt run-all init -lock-timeout=10s");
    assertThat(captor.getAllValues().get(0).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
    assertThat(captor.getAllValues().get(1).getCommand()).isEqualTo("echo \"y\" | terragrunt run-all workspace list");
    assertThat(captor.getAllValues().get(1).getEnvVariables()).isEqualTo(planParameters.getEnvVars());
  }
}