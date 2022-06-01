/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.provision.TfVarSource;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.api.ExecutionDataValue;
import software.wings.beans.NameValuePair;
import software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand;
import software.wings.sm.StateExecutionData;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class TerragruntExecutionData extends StateExecutionData implements DelegateTaskNotifyResponseData {
  private String activityId;

  private ExecutionStatus executionStatus;
  private String errorMessage;

  private String entityId;
  private String stateFileId;

  private String outputs;
  private TerragruntCommand commandExecuted;
  private List<NameValuePair> variables;
  private List<NameValuePair> backendConfigs;
  private List<NameValuePair> environmentVariables;

  private String sourceRepoReference;
  private List<String> targets;
  private List<String> tfVarFiles;
  private TfVarSource tfVarSource;

  private String workspace;
  private String delegateTag;

  private String tfPlanJson;
  private EncryptedRecordData encryptedTfPlan;
  private String branch;
  private String pathToModule;
  private boolean runAll;

  @Override
  public Map<String, ExecutionDataValue> getExecutionDetails() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionDetails();
    putNotNull(executionDetails, "activityId", ExecutionDataValue.builder().displayName("").value(activityId).build());
    return executionDetails;
  }
}
