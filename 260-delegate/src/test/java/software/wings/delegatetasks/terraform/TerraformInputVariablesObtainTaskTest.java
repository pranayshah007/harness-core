/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.NameValuePair;
import software.wings.beans.TerraformInputVariablesTaskResponse;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.delegatetasks.TerraformInputVariablesObtainTask;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.TerraformConfigInspectService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.GitUtilsDelegate;
import software.wings.utils.WingsTestConstants;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class TerraformInputVariablesObtainTaskTest extends WingsBaseTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock GitService gitService;
  @Mock GitUtilsDelegate gitUtilsDelegate;
  @Mock EncryptionService encryptionService;
  @Mock TerraformConfigInspectService terraformConfigInspectService;

  private TerraformProvisionParameters parameters;

  @InjectMocks
  TerraformInputVariablesObtainTask delegateRunnableTask = new TerraformInputVariablesObtainTask(
      DelegateTaskPackage.builder()
          .delegateId(WingsTestConstants.DELEGATE_ID)
          .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
          .build(),
      null, notifyResponseData -> {}, () -> true);

  @Before
  public void setup() {
    initMocks(this);
    parameters = TerraformProvisionParameters.builder()
                     .sourceRepo(GitConfig.builder().branch("master").build())
                     .scriptPath("")
                     .build();
    when(encryptionService.decrypt(any(), any(), eq(false))).thenReturn(null);
    mockStatic(FileUtils.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRun() throws IOException {
    String moduleDir = "some-dir";
    when(gitUtilsDelegate.cloneRepo(any(), any(), any())).thenReturn(GitOperationContext.builder().build());
    when(gitUtilsDelegate.resolveAbsoluteFilePath(any(), any())).thenReturn(moduleDir);
    when(FileUtils.listFiles(any(), any(), any())).thenReturn(Arrays.asList(new File(moduleDir)));

    when(terraformConfigInspectService.parseFieldsUnderCategory(moduleDir, "variables", false))
        .thenReturn(Arrays.asList("var_1", "var_2"));

    TerraformInputVariablesTaskResponse inputVariables = delegateRunnableTask.run(new Object[] {parameters});
    assertThat(inputVariables.getVariablesList().isEmpty()).isFalse();
    List<String> variableNames =
        inputVariables.getVariablesList().stream().map(NameValuePair::getName).collect(Collectors.toList());
    assertThat(variableNames).containsExactlyInAnyOrder("var_1", "var_2");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testNoTerraformFilesFound() {
    when(gitUtilsDelegate.cloneRepo(any(), any(), any())).thenReturn(GitOperationContext.builder().build());
    when(gitUtilsDelegate.resolveAbsoluteFilePath(any(), any())).thenReturn("some-path");
    when(FileUtils.listFiles(any(), any(), any())).thenReturn(Collections.EMPTY_LIST);
    TerraformInputVariablesTaskResponse response = delegateRunnableTask.run(new Object[] {parameters});
    assertThat(response.getTerraformExecutionData().getErrorMessage())
        .contains("No "
            + "Terraform Files Found");
  }
}
