/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilities;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.k8s.model.HelmVersion.V2;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.helm.HelmCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.dto.HelmChartSpecification;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.settings.SettingValue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 3/22/18.
 */
@Data
@AllArgsConstructor
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class HelmCommandRequest implements TaskParameters, ActivityAccess, ExecutionCapabilityDemander {
  @NotEmpty private HelmCommandType helmCommandType;
  private String accountId;
  private String appId;
  private String kubeConfigLocation;
  private String commandName;
  private String activityId;
  private ContainerServiceParams containerServiceParams;
  private String releaseName;
  private HelmChartSpecification chartSpecification;
  private String repoName;
  private GitConfig gitConfig;
  private List<EncryptedDataDetail> encryptedDataDetails;
  @JsonIgnore private transient LogCallback executionLogCallback;
  @Expression(ALLOW_SECRETS) private String commandFlags;
  @Expression(ALLOW_SECRETS) private HelmCommandFlag helmCommandFlag;
  private K8sDelegateManifestConfig repoConfig;
  @Builder.Default private HelmVersion helmVersion = V2;
  private String ocPath;
  private String workingDir;
  @Expression(ALLOW_SECRETS) private List<String> variableOverridesYamlFiles;
  private GitFileConfig gitFileConfig;
  private boolean k8SteadyStateCheckEnabled;
  private boolean mergeCapabilities; // HELM_MERGE_CAPABILITIES
  private boolean isGitHostConnectivityCheck;
  private boolean useNewKubectlVersion;
  private boolean useLatestChartMuseumVersion;
  private String gcpKeyPath;
  private boolean useRefactorSteadyStateCheck;
  private boolean skipSteadyStateCheck;

  public HelmCommandRequest(HelmCommandType helmCommandType, boolean mergeCapabilities) {
    this.helmCommandType = helmCommandType;
    this.mergeCapabilities = mergeCapabilities;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (mergeCapabilities) {
      executionCapabilities.add(
          HelmInstallationCapability.builder().version(helmVersion).criteria("helmcommand").build());
    } else {
      executionCapabilities.add(HelmCommandCapability.builder().commandRequest(this).build());
    }
    if (gitConfig != null) {
      if (isGitHostConnectivityCheck) {
        executionCapabilities.addAll(CapabilityHelper.generateExecutionCapabilitiesForGit(gitConfig));
      } else {
        executionCapabilities.add(GitConnectionCapability.builder()
                                      .gitConfig(gitConfig)
                                      .settingAttribute(gitConfig.getSshSettingAttribute())
                                      .encryptedDataDetails(getEncryptedDataDetails())
                                      .build());
      }
    }
    Set<String> delegateSelectors = getDelegateSelectorsFromConfigurations();
    if (isNotEmpty(delegateSelectors)) {
      executionCapabilities.add(SelectorCapability.builder().selectors(delegateSelectors).build());
    }
    if (containerServiceParams != null) {
      executionCapabilities.addAll(containerServiceParams.fetchRequiredExecutionCapabilities(maskingEvaluator));
    }
    return executionCapabilities;
  }

  @NonNull
  private Set<String> getDelegateSelectorsFromConfigurations() {
    Set<String> delegateSelectors = new HashSet<>();
    if (repoConfig != null && repoConfig.getHelmChartConfigParams() != null) {
      SettingValue connectorConfig = repoConfig.getHelmChartConfigParams().getConnectorConfig();
      if (connectorConfig != null) {
        if (connectorConfig instanceof AwsConfig) {
          AwsConfig awsConfig = (AwsConfig) connectorConfig;
          if (isNotEmpty(awsConfig.getTag())) {
            delegateSelectors.add(awsConfig.getTag());
          }
        } else if (connectorConfig instanceof GcpConfig) {
          GcpConfig gcpConfig = (GcpConfig) connectorConfig;
          if (isNotEmpty(gcpConfig.getDelegateSelectors())) {
            delegateSelectors.addAll(new HashSet<>(gcpConfig.getDelegateSelectors()));
          }
        }
      }
    }
    if (gitConfig != null && isNotEmpty(gitConfig.getDelegateSelectors())) {
      delegateSelectors.addAll(gitConfig.getDelegateSelectors());
    }
    return delegateSelectors;
  }
}
