/*

 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.delegate.task.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.cli.CliHelper;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.InlineFileConfig;
import io.harness.delegate.beans.storeconfig.InlineStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.terragrunt.request.TerragruntCommandType;
import io.harness.delegate.beans.terragrunt.request.TerragruntPlanTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.terragrunt.v2.TerragruntClientImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class TerragruntTestUtils {
  public static final String ACCOUNT_ID = "test-account-ID";
  public static final String ENTITY_ID = "test-entity-ID";

  public static final String TG_RUN_PATH = "test-path";
  public static final String TG_WORKSPACE = "test-workspace";
  public static final String TG_TARGET = "test-target";
  public static final String TG_VAR_FILE_CONTENT = "test-varFileContent";
  public static final String TG_VAR_FILE_NAME = "test-terragrunt-${UUID}.tgvars";
  public static final String TG_STATE_ID = "test-stateId";
  public static final String TG_TF_PLAN_JSON = "test-tfPlanJson";
  public static final String TG_BACKEND_FILE = "test-backendFile";
  public static final String TG_BACKEND_FILE_SOURCE_REF = "test-backendFileSourceRef";
  public static final String TG_CONFIG_FILE_SOURCE_REF = "test-configFileSourceRef";
  public static final String TG_WORKING_DIR = "workingDir/";
  public static final String TG_SCRIPT_DIR = "scriptDirectory/";
  public static final String TG_VAR_FILES_DIR = "scriptDirectory/";
  public static final String TEST_TERRAFORM_VERSION = "1.3.6";

  public TerragruntPlanTaskParameters createPlanTaskParameters() {
    List<InlineFileConfig> files = new ArrayList<>();
    files.add(InlineFileConfig.builder().content(TG_VAR_FILE_CONTENT).name(TG_VAR_FILE_NAME).build());
    StoreDelegateConfig varFiles = InlineStoreDelegateConfig.builder().files(files).build();

    List<InlineFileConfig> backendFiles = new ArrayList<>();
    backendFiles.add(InlineFileConfig.builder().build());
    StoreDelegateConfig backendFileStore = InlineStoreDelegateConfig.builder().files(backendFiles).build();

    return TerragruntPlanTaskParameters.builder()
        .exportJsonPlan(true)
        .commandType(TerragruntCommandType.APPLY)
        .accountId(ACCOUNT_ID)
        .timeoutInMillis(1000)
        .entityId(ENTITY_ID)
        .runConfiguration(
            TerragruntRunConfiguration.builder().runType(TerragruntTaskRunType.RUN_MODULE).path(TG_RUN_PATH).build())
        .envVars(new HashMap<>())
        .commandUnitsProgress(null)
        .workspace(TG_WORKSPACE)
        .targets(new ArrayList<>() {
          { add(TG_TARGET); }
        })
        .encryptedDataDetailList(List.of(EncryptedDataDetail.builder().build()))
        .varFiles(Collections.singletonList(varFiles))
        .backendFilesStore(backendFileStore)
        .configFilesStore(GitStoreDelegateConfig.builder().build())
        .build();
  }

  public TerragruntContext createTerragruntContext(CliHelper cliHelper) {
    return TerragruntContext.builder()
        .backendFile(TG_BACKEND_FILE)
        .backendFileSourceReference(TG_BACKEND_FILE_SOURCE_REF)
        .varFilesSourceReference(new HashMap<>() {
          { put("test-varFileId-1", "test-ref1"); }
        })
        .configFilesSourceReference(TG_CONFIG_FILE_SOURCE_REF)
        .terragruntWorkingDirectory(TG_WORKING_DIR)
        .scriptDirectory(TG_SCRIPT_DIR)
        .varFilesDirectory(TG_VAR_FILES_DIR)
        .client(TerragruntClientImpl.builder()
                    .terraformVersion(Version.parse(TEST_TERRAFORM_VERSION))
                    .cliHelper(cliHelper)
                    .build())
        .build();
  }
}
