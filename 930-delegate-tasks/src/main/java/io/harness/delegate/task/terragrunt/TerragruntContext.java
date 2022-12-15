/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.terragrunt.v2.TerragruntClient;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
public class TerragruntContext {
  String scriptDirectory;
  String varFilesDirectory;
  String workingDirectory;
  String terragruntWorkingDirectory;

  List<String> varFiles;
  String backendFile;
  String configFilesSourceReference;
  String backendFileSourceReference;
  Map<String, String> varFilesSourceReference;

  TerragruntClient client;
}
