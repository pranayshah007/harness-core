/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Explanation.EXPLANATION_ERROR_ASKING_FOR_STATE_MIGRATION;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_CHECK_TERRAFORM_CONFIG_LOCATION;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_CHECK_TERRAFORM_CONFIG_LOCATION_ARGUMENT;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_ERROR_ASKING_FOR_STATE_MIGRATION;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_FAILED_TO_GET_EXISTING_WORKSPACES;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_FAIL_TO_INSTALL_PROVIDER;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Message.GENERIC_NO_TERRAFORM_ERROR;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Message.MESSAGE_ERROR_TOO_LONG;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Message.MESSAGE_FAILED_TO_GET_EXISTING_WORKSPACES;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.TerraformCommandExecutionException;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.TerraformCliRuntimeException;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class TerraformRuntimeExceptionHandlerTest {
  private static final String TEST_ERROR_NO_VALUE_VARIABLE =
      "\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mNo value for required variable\u001B[0m\u001B[0m  on config.tf line 1:   1: \u001B[4mvariable \"access_key\"\u001B[0m {\u001B[0mThe root module input variable \"access_key\" is not set, and has no default value. Use a -var or -var-file command line argument to provide a value for this variable.\u001B[0m\u001B[0m\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mNo value for required variable\u001B[0m\u001B[0m  on config.tf line 4:   4: \u001B[4mvariable \"secret_key\"\u001B[0m {\u001B[0mThe root module input variable \"secret_key\" is not set, and has no default value. Use a -var or -var-file command line argument to provide a value for this variable.\u001B[0m\u001B[0m\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mNo value for required variable\u001B[0m\u001B[0m  on config.tf line 7:   7: \u001B[4mvariable \"region\"\u001B[0m {\u001B[0mThe root module input variable \"region\" is not set, and has no default value. Use a -var or -var-file command line argument to provide a value for this variable.\u001B[0m\u001B[0m\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mNo value for required variable\u001B[0m\u001B[0m  on config.tf line 11:  11: \u001B[4mvariable \"tag_name\"\u001B[0m {\u001B[0mThe root module input variable \"tag_name\" is not set, and has no default value. Use a -var or -var-file command line argument to provide a value for this variable.\u001B[0m\u001B[0m\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mNo value for required variable\u001B[0m\u001B[0m  on config.tf line 16:  16: \u001B[4mvariable \"keyName\"\u001B[0m {}\u001B[0mThe root module input variable \"keyName\" is not set, and has no default value. Use a -var or -var-file command line argument to provide a value for this variable.\u001B[0m\u001B[0m";
  private static final String TEST_ERROR_UNKNOWN1 =
      "\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mUnknown error with terraform block\u001B[0m\u001B[0m  on config.tf line 10:   10: \u001B[4mcloud \"test\"\u001B[0m {\u001B[0mSomething went wrong and we're not aware of this error or error itself is self explanatory\u001B[0m\u001B[0m";
  private static final String TEST_ERROR_UNKNOWN2 =
      "\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mUnknown error with terraform block and argument\u001B[0m\u001B[0m  on main.tf line 1, in cloud \"test\":   1: argument \"test\" \u001B[4m{\u001B[0m\u001B[0mSomething went wrong and we're not aware of this error or error itself is self explanatory\u001B[0m\u001B[0m";
  private static final String TEST_ERROR_FAILED_WORKSPACES =
      "[31m \u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mFailed to get existing workspaces: Get \"http://127.0.0.1:8500/v1/kv/state-env:?keys=&separator=%2F\": dial tcp 127.0.0.1:8500: connect: connection refused\u001B[0m  \u001B[0m\u001B[0m\u001B[0m";
  private static final String TEST_ERROR_INVALID_REGION_TF13 =
      "[31m \u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mInvalid AWS Region: random\u001B[0m  \u001B[0m  on config.tf line 15, in provider \"aws\":   15: provider \"aws\" \u001B[4m{\u001B[0m \u001B[0m \u001B[0m\u001B[0m";
  private static final String TEST_ERROR_S3_BACKEND_CONFIG =
      "[31m \u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1merror configuring S3 Backend: no valid credential sources for S3 Backend found \u001B[0m  \u001B[0m\u001B[0m\u001B[0m";
  private static final String NO_DIR_EXIST_ERROR_MESSAGE =
      "Could not find provided terraform config folder terraform-dir/module1";
  private static final String TEST_ERROR_FAILED_INSTALL_PROVIDER =
      "[31m \u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mFailed to install provider\u001B[0m  \u001B[0mError while installing hashicorp/null v3.1.0: mkdir .terraform: no such file or directory \u001B[0m\u001B[0m";

  private static final String TEST_ERROR_ASKING_FOR_STATE_MIGRATION =
      "[31m \u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mFailed to install provider\u001B[0m  \u001B[0merror asking for state migration \u001B[0m\u001B[0m";
  private static final String TEST_LONG_UNKNOWN_ERROR = StringUtils.repeat("Errr", 156);

  TerraformRuntimeExceptionHandler handler = new TerraformRuntimeExceptionHandler();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleUnknownError1() {
    TerraformCliRuntimeException cliRuntimeException =
        new TerraformCliRuntimeException("Terraform failed", "terraform refresh", TEST_ERROR_UNKNOWN1);
    assertSingleErrorMessage(handler.handleException(cliRuntimeException),
        format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION, "config.tf", "10", "cloud \"test\""),
        "Something went wrong and we're not aware of this error or error itself is self explanatory",
        "Unknown error with terraform block");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleUnknownError2() {
    TerraformCliRuntimeException cliRuntimeException =
        new TerraformCliRuntimeException("Terraform failed", "terraform refresh", TEST_ERROR_UNKNOWN2);
    assertSingleErrorMessage(handler.handleException(cliRuntimeException),
        format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION_ARGUMENT, "main.tf", "1", "cloud \"test\"", "argument \"test\""),
        "Something went wrong and we're not aware of this error or error itself is self explanatory",
        "Unknown error with terraform block");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleErrorInvalidRegionOldTfVersion13() {
    TerraformCliRuntimeException cliRuntimeException =
        new TerraformCliRuntimeException("Terraform failed", "terraform refresh", TEST_ERROR_INVALID_REGION_TF13);
    assertSingleErrorMessage(handler.handleException(cliRuntimeException),
        format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION, "config.tf", "15", "provider \"aws\""), null,
        "terraform refresh failed with: Invalid AWS Region: random");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleConfigDirNotExistError() {
    TerraformCliRuntimeException cliRuntimeException = new TerraformCliRuntimeException(
        format("Failed to execute terraform Command %s : Reason: %s", "terraform init", NO_DIR_EXIST_ERROR_MESSAGE),
        "terraform init", NO_DIR_EXIST_ERROR_MESSAGE);

    assertSingleErrorMessage(handler.handleException(cliRuntimeException),
        "Please check your inputs for Configuration File Repository", null,
        format("terraform init failed with: %s", NO_DIR_EXIST_ERROR_MESSAGE));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleErrorInvalidS3BackendConfig() {
    TerraformCliRuntimeException cliRuntimeException =
        new TerraformCliRuntimeException("Terraform failed", "terraform init", TEST_ERROR_S3_BACKEND_CONFIG);
    assertSingleErrorMessage(handler.handleException(cliRuntimeException), HINT_FAILED_TO_GET_EXISTING_WORKSPACES,
        "error configuring S3 Backend: no valid credential sources for S3 Backend found",
        "terraform init failed with: error configuring S3 Backend: no valid credential sources for S3 Backend found");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFailedToInstallProviderError() {
    TerraformCliRuntimeException cliRuntimeException =
        new TerraformCliRuntimeException("Terraform failed", "terraform init", TEST_ERROR_FAILED_INSTALL_PROVIDER);
    assertSingleErrorMessage(handler.handleException(cliRuntimeException), HINT_FAIL_TO_INSTALL_PROVIDER,
        "Error while installing hashicorp/null v3.1.0: mkdir .terraform: no such file or directory",
        "terraform init failed with: Failed to install provider");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFailedWithStateMigrationError() {
    TerraformCliRuntimeException cliRuntimeException =
        new TerraformCliRuntimeException("Terraform failed", "terraform init", TEST_ERROR_ASKING_FOR_STATE_MIGRATION);
    assertSingleErrorMessage(handler.handleException(cliRuntimeException), HINT_ERROR_ASKING_FOR_STATE_MIGRATION,
        EXPLANATION_ERROR_ASKING_FOR_STATE_MIGRATION, "terraform init failed with: Failed to install provider");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleFailedWorkspaces() {
    TerraformCliRuntimeException cliRuntimeException =
        new TerraformCliRuntimeException("Terraform failed", "terraform refresh", TEST_ERROR_FAILED_WORKSPACES);
    assertSingleErrorMessage(handler.handleException(cliRuntimeException), HINT_FAILED_TO_GET_EXISTING_WORKSPACES,
        "Failed to get existing workspaces: Get \"http://127.0.0.1:8500/v1/kv/state-env:?keys=&separator=%2F\": dial tcp 127.0.0.1:8500: connect: connection refused",
        MESSAGE_FAILED_TO_GET_EXISTING_WORKSPACES);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleExceptionNoValueForRequiredVariable() {
    TerraformCliRuntimeException cliRuntimeException =
        new TerraformCliRuntimeException("Terraform failed", "terraform refresh", TEST_ERROR_NO_VALUE_VARIABLE);
    WingsException handledException = handler.handleException(cliRuntimeException);
    assertMultipleErrorMessages(handledException,
        asList(format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION, "config.tf", "1", "variable \"access_key\""),
            format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION, "config.tf", "7", "variable \"region\""),
            format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION, "config.tf", "4", "variable \"secret_key\""),
            format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION, "config.tf", "16", "variable \"keyName\""),
            format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION, "config.tf", "11", "variable \"tag_name\"")),
        asList(
            "The root module input variable \"access_key\" is not set, and has no default value. Use a -var or -var-file command line argument to provide a value for this variable.",
            "The root module input variable \"region\" is not set, and has no default value. Use a -var or -var-file command line argument to provide a value for this variable.",
            "The root module input variable \"secret_key\" is not set, and has no default value. Use a -var or -var-file command line argument to provide a value for this variable.",
            "The root module input variable \"keyName\" is not set, and has no default value. Use a -var or -var-file command line argument to provide a value for this variable.",
            "The root module input variable \"tag_name\" is not set, and has no default value. Use a -var or -var-file command line argument to provide a value for this variable."),
        "terraform refresh failed with: No value for required variable");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleExceptionLongUnhandledException() {
    TerraformCliRuntimeException cliRuntimeException =
        new TerraformCliRuntimeException("Terraform failed", "terraform refresh", TEST_LONG_UNKNOWN_ERROR);
    WingsException handledException = handler.handleException(cliRuntimeException);
    assertThat(handledException).isInstanceOf(TerraformCommandExecutionException.class);
    TerraformCommandExecutionException terraformCommandExecutionException =
        (TerraformCommandExecutionException) handledException;
    // Expected Errr to be repeated 128 that is going to be exact 512 characters
    assertThat(terraformCommandExecutionException.getParams().get("message"))
        .isEqualTo(StringUtils.repeat("Errr", 128) + "... (" + MESSAGE_ERROR_TOO_LONG + ")");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleExceptionNoCliError() {
    TerraformCliRuntimeException cliRuntimeException =
        new TerraformCliRuntimeException("Terraform failed", "terraform refresh", " ");
    WingsException handledException = handler.handleException(cliRuntimeException);
    assertThat(handledException).isInstanceOf(TerraformCommandExecutionException.class);
    TerraformCommandExecutionException terraformCommandExecutionException =
        (TerraformCommandExecutionException) handledException;
    assertThat(terraformCommandExecutionException.getParams().get("message")).isEqualTo(GENERIC_NO_TERRAFORM_ERROR);
  }

  private void assertSingleErrorMessage(WingsException exception, String hint, String explanation, String message) {
    HintException hintException = ExceptionUtils.cause(HintException.class, exception);
    ExplanationException explanationException = ExceptionUtils.cause(ExplanationException.class, exception);
    TerraformCommandExecutionException terraformException =
        ExceptionUtils.cause(TerraformCommandExecutionException.class, exception);

    assertThat(hintException.getMessage()).isEqualTo(hint);
    if (explanationException != null && isNotEmpty(explanationException.getMessage())) {
      assertThat(explanationException.getMessage()).contains(explanation);
    }
    assertThat(terraformException.getMessage()).contains(message);
  }

  private void assertMultipleErrorMessages(
      Throwable exception, List<String> hints, List<String> explanations, String message) {
    List<String> exceptionHints = new ArrayList<>();
    List<String> exceptionExplanations = new ArrayList<>();
    TerraformCommandExecutionException terraformException =
        ExceptionUtils.cause(TerraformCommandExecutionException.class, exception);

    while (exception != null) {
      if (exception instanceof HintException) {
        exceptionHints.add(exception.getMessage());
      }

      if (exception instanceof ExplanationException) {
        exceptionExplanations.add(exception.getMessage());
      }

      exception = exception.getCause();
    }

    assertThat(exceptionHints).containsExactlyInAnyOrder(hints.toArray(new String[0]));
    assertThat(exceptionExplanations).containsExactlyInAnyOrder(explanations.toArray(new String[0]));
    assertThat(terraformException.getMessage()).contains(message);
  }
}
