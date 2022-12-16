/*

 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@TypeAlias("terragruntInheritOutput")
@JsonTypeName("terragruntInheritOutput")
@RecasterAlias("io.harness.cdng.provision.terragrunt.TerragruntInheritOutput")
public class TerragruntInheritOutput implements ExecutionSweepingOutput {
  String workspace;
  GitStoreConfig configFiles;
  List<TerragruntVarFileConfig> varFileConfigs;
  TerragruntBackendConfigFileConfig backendConfigFile;
  Map<String, String> environmentVariables;
  List<String> targets;
  EncryptedRecordData encryptedPlan;
  EncryptionConfig encryptionConfig;
  String planName;
  TerragruntRunConfiguration runConfiguration;
  boolean useConnectorCredentials;
}
