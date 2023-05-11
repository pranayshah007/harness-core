/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public final class TerragruntExceptionConstants {
  public static final class CliErrorMessages {
    public static final String UNSUPPORTED_ARGUMENT = "Unsupported argument";
    public static final String UNSUPPORTED_ATTRIBUTE = "Unsupported attribute";
    public static final String UNKNOWN_FUNCTION = "Call to unknown function";
    public static final String FUNCTION_CALL_ERROR = "Error in function call";
    public static final String UNSUPORTED_BLOCK_TYPE = "Unsupported block type";
    public static final String MISSING_REQUIRED_ARGUMENT = "Missing required argument";
    public static final String UNSUITABLE_TYPE = "Unsuitable value type";
    public static final String DOWNLOADING_ERROR = "error downloading";
    public static final String CANT_READ_REMOTE_REPOSITORY = "Could not read from remote repository";
    public static final String NO_TERRAGRUNT_HCL_FILE = "terragrunt.hcl: no such file or directory";
    public static final String TERRAGRUNT_NOT_FOUND = "terragrunt: not found";
    public static final String TERRAFORM_NOT_FOUND = "terraform: not found";
  }

  public static final class Message {
    public static final String MESSAGE_UNSUPPORTED_ARGUMENT = "Failed to parse terragrunt configuration";
    public static final String MESSAGE_DOWNLOAD_REMOTE_REPO = "Failed to download remote repo with terraform modules";
    public static final String MESSAGE_NO_TERRAGRUNT_HCL_FILE = "Failed to find terragrunt.hcl file";
    public static final String MESSAGE_NO_TERRAGRUNT_TERRAFORM_FOUND = "Wasn't able to find terragrunt or terraform";
  }

  public static final class Hints {
    public static final String HINT_UNSUPPORTED_ARGUMENT =
        "Failed to parse terragrunt configuration, check your terragrunt configuration for errors";
    public static final String HINT_CLONE_REMOTE_REPOSITORY =
        "Please make sure you have set ssh key for downloading remote repos, make sure you have the correct access rights and the repository exists";
    public static final String HINT_NO_TERRAGRUNT_HCL_FILE =
        "Please make sure when running single terragrunt module to have a terragrunt.hcl file";
    public static final String HINT_TERRAFORM_TERRAGRUNT_NOT_FOUND =
        "Please make sure terragrunt/terraform is installed on delegate";
  }
}
