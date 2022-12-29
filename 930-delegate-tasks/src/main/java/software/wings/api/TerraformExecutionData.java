/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.provision.TfVarSource;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.api.terraform.TfVarGitSource;
import software.wings.api.terraform.TfVarS3Source;
import software.wings.beans.NameValuePair;
import software.wings.sm.StateExecutionData;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class TerraformExecutionData extends StateExecutionData implements DelegateTaskNotifyResponseData {
  private String activityId;

  private ExecutionStatus executionStatus;
  private String errorMessage;

  private String entityId;
  private String stateFileId;

  private String outputs;
  private TerraformCommand commandExecuted;
  private List<NameValuePair> variables;
  private List<NameValuePair> backendConfigs;
  private String backendConfigStoreType;
  private TfVarGitSource remoteBackendConfig;
  private TfVarS3Source remoteS3BackendConfig;
  private List<NameValuePair> environmentVariables;

  private String sourceRepoReference;
  private List<String> targets;
  private List<String> tfVarFiles;
  private TfVarSource tfVarSource;

  private String workspace;
  private String delegateTag;

  private String tfPlanJson;
  private String tfPlanHumanReadable;
  private String tfPlanHumanReadableFileId;
  private String tfPlanJsonFiledId;
  private EncryptedRecordData encryptedTfPlan;

  private String awsConfigId;
  private String awsRoleArn;
  private String awsRegion;

  @Override
  public Map<String, ExecutionDataValue> getExecutionDetails() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionDetails();
    putNotNull(executionDetails, "activityId", ExecutionDataValue.builder().displayName("").value(activityId).build());
    return executionDetails;
  }
}
