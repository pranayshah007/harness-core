/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.delegation;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.expression.Expression.DISALLOW_SECRETS;
import static io.harness.provision.TfVarSource.TfVarSourceType.GIT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.capability.ProcessExecutionCapabilityHelper;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.provision.TfVarSource;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.api.terraform.TfVarGitSource;
import software.wings.beans.GitConfig;
import software.wings.beans.NameValuePair;
import software.wings.delegatetasks.validation.capabilities.GitConnectionCapability;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(CDP)
@Value
@Builder
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class TerragruntProvisionParameters implements TaskParameters, ActivityAccess, ExecutionCapabilityDemander {
  public static final long TIMEOUT_IN_MINUTES = 100;
  public static final String TERRAGRUNT = "terragrunt";

  public enum TerragruntCommand { APPLY, DESTROY }

  public enum TerragruntCommandUnit {
    Apply,
    Adjust,
    Destroy,
    Rollback;
  }

  private String accountId;
  private final String activityId;
  private final String appId;
  private final String entityId;
  private final String currentStateFileId;
  private final String sourceRepoSettingId;
  private final GitConfig sourceRepo;
  private final String sourceRepoBranch;
  @Expression(DISALLOW_SECRETS) private final String commitId;
  List<EncryptedDataDetail> sourceRepoEncryptionDetails;
  @Expression(DISALLOW_SECRETS) private final String scriptPath;
  @Expression(DISALLOW_SECRETS) private String pathToModule;
  private final List<NameValuePair> rawVariables;
  @Expression(ALLOW_SECRETS) private final Map<String, String> variables;
  private final Map<String, EncryptedDataDetail> encryptedVariables;

  @Expression(ALLOW_SECRETS) private final Map<String, String> backendConfigs;
  private final Map<String, EncryptedDataDetail> encryptedBackendConfigs;

  @Expression(ALLOW_SECRETS) private final Map<String, String> environmentVariables;
  private final Map<String, EncryptedDataDetail> encryptedEnvironmentVariables;

  private final TerragruntCommand command;
  private final TerragruntCommandUnit commandUnit;
  @Builder.Default private long timeoutInMillis = TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES);

  private final List<String> targets;

  private final List<String> tfVarFiles;
  private final boolean runPlanOnly;
  private final boolean exportPlanToApplyStep;
  private final boolean runAll;
  @Expression(DISALLOW_SECRETS) private final String workspace;
  private final String delegateTag;

  private final boolean saveTerragruntJson;
  private final SecretManagerConfig secretManagerConfig;
  private final EncryptedRecordData encryptedTfPlan;
  private final String planName;

  private final TfVarSource tfVarSource;
  /**
   * Boolean to indicate if we should skip updating terraform state using refresh command before applying an approved
   * terraform plan
   */
  private boolean skipRefreshBeforeApplyingPlan;
  private boolean useAutoApproveFlag;
  boolean encryptDecryptPlanForHarnessSMOnManager;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = ProcessExecutionCapabilityHelper.generateExecutionCapabilitiesForTerraform(
        sourceRepoEncryptionDetails, maskingEvaluator);
    capabilities.addAll(ProcessExecutionCapabilityHelper.generateExecutionCapabilitiesForTerragrunt(
        sourceRepoEncryptionDetails, maskingEvaluator));
    if (sourceRepo != null) {
      capabilities.add(GitConnectionCapability.builder()
                           .gitConfig(sourceRepo)
                           .settingAttribute(sourceRepo.getSshSettingAttribute())
                           .encryptedDataDetails(sourceRepoEncryptionDetails)
                           .build());
      if (isNotEmpty(sourceRepo.getDelegateSelectors())) {
        capabilities.add(
            SelectorCapability.builder().selectors(new HashSet<>(sourceRepo.getDelegateSelectors())).build());
      }
    }
    if (tfVarSource != null && tfVarSource.getTfVarSourceType() == GIT) {
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
    return capabilities;
  }
}
