/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.delegation;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.capability.ProcessExecutionCapabilityHelper;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.provision.TfVarSource;
import io.harness.provision.TfVarSource.TfVarSourceType;
import io.harness.provision.model.TfConfigInspectVersion;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.api.terraform.TfVarGitSource;
import software.wings.api.terraform.TfVarS3Source;
import software.wings.beans.AwsConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.TerraformSourceType;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.delegatetasks.validation.capabilities.GitConnectionCapability;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class TerraformProvisionParameters implements TaskParameters, ActivityAccess, ExecutionCapabilityDemander {
  public static final long TIMEOUT_IN_MINUTES = 100;
  public static final String TERRAFORM = "terraform";
  private String accountId;
  private final String activityId;
  private final String appId;
  private final String entityId;
  private final String currentStateFileId;
  private final String sourceRepoSettingId;
  private final GitConfig sourceRepo;
  private final String sourceRepoBranch;
  private final String commitId;
  List<EncryptedDataDetail> sourceRepoEncryptionDetails;
  private final String scriptPath;
  private final List<NameValuePair> rawVariables;
  @Expression(ALLOW_SECRETS) private final Map<String, String> variables;
  private final Map<String, EncryptedDataDetail> encryptedVariables;

  private final TfVarGitSource remoteBackendConfig;
  private final String backendConfigStoreType;
  private final Map<String, String> backendConfigs;
  private final Map<String, EncryptedDataDetail> encryptedBackendConfigs;

  @Expression(ALLOW_SECRETS) private final Map<String, String> environmentVariables;
  private final Map<String, EncryptedDataDetail> encryptedEnvironmentVariables;

  private final TerraformCommand command;
  private final TerraformCommandUnit commandUnit;
  @Builder.Default private long timeoutInMillis = TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES);

  private final List<String> targets;

  private final List<String> tfVarFiles;
  private final boolean runPlanOnly;
  private final boolean exportPlanToApplyStep;
  private final boolean exportPlanToHumanReadableOutput;

  private final String workspace;
  private final String delegateTag;

  private final boolean saveTerraformJson;
  private final boolean useOptimizedTfPlanJson;
  private final SecretManagerConfig secretManagerConfig;
  private final EncryptedRecordData encryptedTfPlan;
  private final String planName;

  private final TfVarSource tfVarSource;
  private final boolean useTfClient; // FF: USE_TF_CLIENT
  private final boolean useActivityIdBasedTfBaseDir;
  private final boolean syncGitCloneAndCopyToDestDir;
  /**
   * Boolean to indicate if we should skip updating terraform state using refresh command before applying an approved
   * terraform plan
   */
  private boolean skipRefreshBeforeApplyingPlan;
  private boolean isGitHostConnectivityCheck;
  private final boolean useTfConfigInspectLatestVersion;
  private final AwsConfig awsConfig;

  private final String awsConfigId;
  private final String awsRoleArn;
  private final String awsRegion;
  private List<EncryptedDataDetail> awsConfigEncryptionDetails;

  private final boolean analyseTfPlanSummary; // FF: ANALYSE_TF_PLAN_SUMMARY

  private TerraformSourceType sourceType;
  private final String configFilesS3URI;
  private final AwsConfig configFilesAwsSourceConfig;
  private List<EncryptedDataDetail> configFileAWSEncryptionDetails;
  private final TfVarS3Source remoteS3BackendConfig;
  private final TfConfigInspectVersion terraformConfigInspectVersion;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = ProcessExecutionCapabilityHelper.generateExecutionCapabilitiesForTerraform(
        sourceRepoEncryptionDetails, maskingEvaluator);
    if (sourceRepo != null) {
      if (isGitHostConnectivityCheck) {
        capabilities.addAll(CapabilityHelper.generateExecutionCapabilitiesForGit(sourceRepo));

      } else {
        capabilities.add(GitConnectionCapability.builder()
                             .gitConfig(sourceRepo)
                             .settingAttribute(sourceRepo.getSshSettingAttribute())
                             .encryptedDataDetails(sourceRepoEncryptionDetails)
                             .build());
      }
      if (isNotEmpty(sourceRepo.getDelegateSelectors())) {
        capabilities.add(
            SelectorCapability.builder().selectors(new HashSet<>(sourceRepo.getDelegateSelectors())).build());
      }
    }
    if (tfVarSource != null && tfVarSource.getTfVarSourceType() == TfVarSourceType.GIT) {
      TfVarGitSource tfVarGitSource = (TfVarGitSource) tfVarSource;
      if (tfVarGitSource.getGitConfig() != null && isNotEmpty(tfVarGitSource.getGitConfig().getDelegateSelectors())) {
        capabilities.add(SelectorCapability.builder()
                             .selectors(new HashSet<>(tfVarGitSource.getGitConfig().getDelegateSelectors()))
                             .build());
      }
    }
    if (secretManagerConfig != null) {
      capabilities.addAll(
          EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilityForSecretManager(secretManagerConfig, null));
    }
    if (awsConfig != null) {
      capabilities.addAll(
          CapabilityHelper.generateDelegateCapabilities(awsConfig, awsConfigEncryptionDetails, maskingEvaluator));
    }

    if (tfVarSource != null && tfVarSource.getTfVarSourceType() != null
        && tfVarSource.getTfVarSourceType().equals(TfVarSourceType.S3)) {
      TfVarS3Source tfVarS3Source = (TfVarS3Source) tfVarSource;
      if (tfVarS3Source.getAwsConfig() != null) {
        capabilities.addAll(CapabilityHelper.generateDelegateCapabilities(
            tfVarS3Source.getAwsConfig(), tfVarS3Source.getEncryptedDataDetails(), maskingEvaluator));
      }
    }

    if (sourceType != null && sourceType.equals(TerraformSourceType.S3) && configFilesAwsSourceConfig != null) {
      capabilities.addAll(CapabilityHelper.generateDelegateCapabilities(
          configFilesAwsSourceConfig, configFileAWSEncryptionDetails, maskingEvaluator));
    }

    if (remoteS3BackendConfig != null) {
      capabilities.addAll(CapabilityHelper.generateDelegateCapabilities(
          remoteS3BackendConfig.getAwsConfig(), remoteS3BackendConfig.getEncryptedDataDetails(), maskingEvaluator));
    }

    return capabilities;
  }
}
