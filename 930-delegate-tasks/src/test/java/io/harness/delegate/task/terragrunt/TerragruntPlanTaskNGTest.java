/*

  * Copyright 2022 Harness Inc. All rights reserved.
  * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
  * that can be found in the licenses directory at the root of this repository, also available at
  * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.delegate.task.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_BACKEND_FILE_SOURCE_REF;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_CONFIG_FILE_SOURCE_REF;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_STATE_ID;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_TF_PLAN_JSON;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_WORKING_DIR;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.terragrunt.request.TerragruntPlanTaskParameters;
import io.harness.delegate.beans.terragrunt.response.TerragruntPlanTaskResponse;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.filesystem.FileIo;
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
  public void testPlan() throws JoseException, IOException, InterruptedException, TimeoutException {
    TerragruntPlanTaskParameters planParameters = TerragruntTestUtils.createPlanTaskParameters();

    when(taskService.prepareTerragrunt(any(), any(), any()))
        .thenReturn(TerragruntTestUtils.createTerragruntContext(cliHelper));
    doNothing().when(taskService).decryptTaskParameters(any());
    doNothing().when(taskService).cleanupTerragruntLocalFiles(any());
    doReturn(logCallback).when(taskService).getLogCallback(any(), any(), any());
    when(cliHelper.executeCliCommand(any(), anyLong(), any(), any(), any(), any(), any(), any()))
        .thenReturn(CliResponse.builder().exitCode(0).build());
    when(taskService.uploadStateFile(any(), any(), any(), any(), any(), any())).thenReturn(TG_STATE_ID);
    when(terraformHelper.uploadTfPlanJson(any(), any(), any(), any(), any(), anyString())).thenReturn(TG_TF_PLAN_JSON);
    doReturn(encryptedPlanContent).when(encryptDecryptHelper).encryptFile(any(), any(), any(), any());

    when(taskService.getPlanJsonLogOutputStream()).thenReturn(planJsonLogOutputStream);
    when(planJsonLogOutputStream.getTfPlanJsonLocalPath()).thenReturn("test-tfPlanLocalPath");

    FileIo.createDirectoryIfDoesNotExist(TG_WORKING_DIR);
    FileIo.writeFile("workingDir/tfplan", new byte[] {});

    TerragruntPlanTaskResponse response = (TerragruntPlanTaskResponse) terragruntPlanTaskNG.run(planParameters);
    assertThat(response).isNotNull();
    assertThat(response.getEncryptedPlan()).isNotNull();
    assertThat(response.getStateFileId()).isEqualTo(TG_STATE_ID);
    assertThat(response.getConfigFilesSourceReference()).isEqualTo(TG_CONFIG_FILE_SOURCE_REF);
    assertThat(response.getBackendFileSourceReference()).isEqualTo(TG_BACKEND_FILE_SOURCE_REF);
    assertThat(response.getPlanJsonFileId()).isEqualTo(TG_TF_PLAN_JSON);
    assertThat(response.getVarFilesSourceReference()).isNotNull();
    assertThat(response.getVarFilesSourceReference().get("test-varFileId-1")).isEqualTo("test-ref1");

    FileIo.deleteDirectoryAndItsContentIfExists(TG_WORKING_DIR);
  }
}
