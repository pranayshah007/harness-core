/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.delegatecapability;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.artifact.ArtifactStreamType.GCR;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.delegate.task.mixin.ProcessExecutorCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.setting.EncryptableSettingWithEncryptionDetails;

import software.wings.beans.GitConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.settings.SettingValue;

import com.google.inject.Singleton;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.URIish;

@OwnedBy(DEL)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Singleton
@Slf4j
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class CapabilityHelper {
  public static final String TERRAFORM = "terraform";
  public static final String TERRAGRUNT = "terragrunt";
  public static final String HELM = "helm";

  public static List<ExecutionCapability> generateDelegateCapabilities(ExecutionCapabilityDemander capabilityDemander,
      List<EncryptedDataDetail> encryptedDataDetails, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();

    if (capabilityDemander != null) {
      executionCapabilities.addAll(capabilityDemander.fetchRequiredExecutionCapabilities(maskingEvaluator));
    }
    if (isEmpty(encryptedDataDetails)) {
      return executionCapabilities;
    }

    executionCapabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
        encryptedDataDetails, maskingEvaluator));
    return executionCapabilities;
  }

  public static Map<String, EncryptionConfig> fetchEncryptionDetailsListFromParameters(TaskData taskData) {
    Map<String, EncryptionConfig> encryptionConfigsMap = new HashMap<>();
    // TODO: Remove this when jenkins/bambooConfig etc are integrated with new framework
    try {
      Object argument = Arrays.stream(taskData.getParameters())
                            .filter(CapabilityHelper::isEncryptionDetailsList)
                            .findFirst()
                            .orElse(null);

      if (argument != null) {
        List<EncryptedDataDetail> encryptedDataDetails = (List<EncryptedDataDetail>) argument;
        return EncryptedDataDetailsCapabilityHelper.fetchEncryptionConfigsMapFromEncryptedDataDetails(
            encryptedDataDetails);
      }
      // TODO: For Task "SECRET_DECRYPT_REF", is argument is only EncryptedDataDetail.
      // Actually it should be later changed to List to match all other apis
      // can be done later
      argument = Arrays.stream(taskData.getParameters())
                     .filter(parameter -> parameter instanceof EncryptedDataDetail)
                     .findFirst()
                     .orElse(null);

      if (argument != null) {
        EncryptedDataDetail encryptedDataDetail = (EncryptedDataDetail) argument;
        return EncryptedDataDetailsCapabilityHelper.fetchEncryptionConfigsMapFromEncryptedDataDetails(
            Collections.singletonList(encryptedDataDetail));
      }

      // BATCH_SECRET_DECRYPT
      argument = Arrays.stream(taskData.getParameters())
                     .filter(CapabilityHelper::isEncryptableSettingWithEncryptionDetailsList)
                     .findFirst()
                     .orElse(null);

      if (argument != null) {
        List<EncryptableSettingWithEncryptionDetails> encryptableSettingWithEncryptionDetails =
            (List<EncryptableSettingWithEncryptionDetails>) argument;
        return fetchEncryptionConfigsMapFromEncryptableSettings(encryptableSettingWithEncryptionDetails);
      }

    } catch (Exception e) {
      log.warn("Failed while generating Encryption Configs from EncryptionDataDetails: " + e);
    }
    return encryptionConfigsMap;
  }

  public static Map<String, EncryptionConfig> fetchEncryptionDetailsListFromParametersV2(TaskDataV2 taskData) {
    Map<String, EncryptionConfig> encryptionConfigsMap = new HashMap<>();
    // TODO: Remove this when jenkins/bambooConfig etc are integrated with new framework
    try {
      Object argument = Arrays.stream(taskData.getParameters())
                            .filter(CapabilityHelper::isEncryptionDetailsList)
                            .findFirst()
                            .orElse(null);

      if (argument != null) {
        List<EncryptedDataDetail> encryptedDataDetails = (List<EncryptedDataDetail>) argument;
        return EncryptedDataDetailsCapabilityHelper.fetchEncryptionConfigsMapFromEncryptedDataDetails(
            encryptedDataDetails);
      }
      // TODO: For Task "SECRET_DECRYPT_REF", is argument is only EncryptedDataDetail.
      // Actually it should be later changed to List to match all other apis
      // can be done later
      argument = Arrays.stream(taskData.getParameters())
                     .filter(parameter -> parameter instanceof EncryptedDataDetail)
                     .findFirst()
                     .orElse(null);

      if (argument != null) {
        EncryptedDataDetail encryptedDataDetail = (EncryptedDataDetail) argument;
        return EncryptedDataDetailsCapabilityHelper.fetchEncryptionConfigsMapFromEncryptedDataDetails(
            Collections.singletonList(encryptedDataDetail));
      }

      // BATCH_SECRET_DECRYPT
      argument = Arrays.stream(taskData.getParameters())
                     .filter(CapabilityHelper::isEncryptableSettingWithEncryptionDetailsList)
                     .findFirst()
                     .orElse(null);

      if (argument != null) {
        List<EncryptableSettingWithEncryptionDetails> encryptableSettingWithEncryptionDetails =
            (List<EncryptableSettingWithEncryptionDetails>) argument;
        return fetchEncryptionConfigsMapFromEncryptableSettings(encryptableSettingWithEncryptionDetails);
      }

    } catch (Exception e) {
      log.warn("Failed while generating Encryption Configs from EncryptionDataDetails: " + e);
    }
    return encryptionConfigsMap;
  }

  private static Map<String, EncryptionConfig> fetchEncryptionConfigsMapFromEncryptableSettings(
      List<EncryptableSettingWithEncryptionDetails> encryptableSettingWithEncryptionDetails) {
    Map<String, EncryptionConfig> encryptionConfigsMap = new HashMap<>();
    encryptableSettingWithEncryptionDetails.forEach(encryptableSettingWithEncryptionDetail
        -> encryptionConfigsMap.putAll(
            EncryptedDataDetailsCapabilityHelper.fetchEncryptionConfigsMapFromEncryptedDataDetails(
                encryptableSettingWithEncryptionDetail.getEncryptedDataDetails())));

    return encryptionConfigsMap;
  }

  public static boolean isTaskParameterType(TaskData taskData) {
    return taskData.getParameters() == null
        || taskData.getParameters().length == 1 && taskData.getParameters()[0] instanceof TaskParameters;
  }

  public static boolean isTaskParameterTypeV2(TaskDataV2 taskData) {
    return taskData.getParameters() == null
        || taskData.getParameters().length == 1 && taskData.getParameters()[0] instanceof TaskParameters;
  }

  private static boolean isEncryptionDetailsList(Object argument) {
    // TODO: Remove this when jenkins/bamoConfig etc are integrated with new framework
    try {
      if (!(argument instanceof List)) {
        return false;
      }

      List list = (List) argument;
      if (isNotEmpty(list) && list.get(0) instanceof EncryptedDataDetail) {
        return true;
      }
    } catch (Exception e) {
      log.warn("Failed in determining if instance of EncryptionDetails");
    }

    return false;
  }

  private static boolean isEncryptableSettingWithEncryptionDetailsList(Object argument) {
    try {
      if (!(argument instanceof List)) {
        return false;
      }

      List list = (List) argument;
      if (isNotEmpty(list) && list.get(0) instanceof EncryptableSettingWithEncryptionDetails) {
        return true;
      }
    } catch (Exception e) {
      log.warn("Failed in determining if instance of EncryptableSettingWithEncryptionDetails");
    }

    return false;
  }

  public static String generateLogStringWithCapabilitiesGenerated(
      String taskType, List<ExecutionCapability> executionCapabilities) {
    StringBuilder builder =
        new StringBuilder(128).append("Capabilities Generate for Task: ").append(taskType).append(" are: ");

    executionCapabilities.forEach(capability -> builder.append('\n').append(capability.toString()));
    return builder.append('\n').toString();
  }

  public static String generateLogStringWithSelectionCapabilitiesGenerated(
      String taskType, List<ExecutionCapability> executionCapabilities) {
    StringBuilder builder =
        new StringBuilder(128).append("Selection Capabilities for Task: ").append(taskType).append(" are: ");

    executionCapabilities.forEach(capability -> builder.append('\n').append(capability.toString()));
    return builder.append('\n').toString();
  }

  /**
   * This is Used by BuildSourceParameters
   */
  public static List<ExecutionCapability> generateCapabilities(SettingValue settingValue,
      ArtifactStreamAttributes artifactStreamAttributes, ExpressionEvaluator maskingEvaluator) {
    String artifactStreamType = artifactStreamAttributes.getArtifactStreamType();

    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (artifactStreamType.equals(GCR.name())) {
      String hostName = artifactStreamAttributes.getRegistryHostName();
      executionCapabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
          new StringBuilder(128)
              .append("https://")
              .append(hostName)
              .append(hostName.endsWith("/") ? "" : "/")
              .toString(),
          maskingEvaluator));

    } else {
      executionCapabilities.addAll(settingValue.fetchRequiredExecutionCapabilities(maskingEvaluator));
    }

    return executionCapabilities;
  }

  public static List<ExecutionCapability> generateExecutionCapabilitiesForGit(GitConfig gitConfig) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (gitConfig != null) {
      if (gitConfig.getRepoUrl().toLowerCase().startsWith("http")) {
        executionCapabilities.add(HttpConnectionExecutionCapability.builder().url(gitConfig.getRepoUrl()).build());
      } else {
        int port = getPort(gitConfig.getRepoUrl());
        HostConnectionAttributes hostConnectionAttributes =
            (HostConnectionAttributes) gitConfig.getSshSettingAttribute().getValue();
        executionCapabilities.add(
            SocketConnectivityExecutionCapability.builder()
                .port(port > -1 ? String.valueOf(port) : hostConnectionAttributes.getSshPort().toString())
                .hostName(getHostname(gitConfig.getRepoUrl()))
                .scheme("ssh")
                .url(gitConfig.getRepoUrl())
                .build());
      }
      if (isNotEmpty(gitConfig.getDelegateSelectors())) {
        executionCapabilities.add(
            SelectorCapability.builder().selectors(new HashSet<>(gitConfig.getDelegateSelectors())).build());
      }
    }
    return executionCapabilities;
  }

  private static int getPort(String repoUrl) {
    try {
      final URIish urIish = new URIish(repoUrl);
      return urIish.getPort();
    } catch (URISyntaxException e) {
      log.error("Failed to construct uri for {} repo", repoUrl, e);
      return -1;
    }
  }

  private static String getHostname(String repoUrl) {
    try {
      final URIish urIish = new URIish(repoUrl);
      return urIish.getHost();
    } catch (URISyntaxException e) {
      log.error("Failed to construct uri for {} repo", repoUrl, e);
      return repoUrl;
    }
  }

  public static List<ExecutionCapability> generateExecutionCapabilitiesForProcessExecutor(String category,
      List<String> processExecutorArguments, List<EncryptedDataDetail> encryptedDataDetails,
      ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    executionCapabilities.add(
        ProcessExecutorCapabilityGenerator.buildProcessExecutorCapability(category, processExecutorArguments));

    if (isNotEmpty(encryptedDataDetails)) {
      List<ExecutionCapability> capabilitiesForEncryption =
          EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
              encryptedDataDetails, maskingEvaluator);
      if (isNotEmpty(capabilitiesForEncryption)) {
        executionCapabilities.addAll(capabilitiesForEncryption);
      }
    }
    return executionCapabilities;
  }

  public static List<ExecutionCapability> generateExecutionCapabilitiesForTerraform(
      List<EncryptedDataDetail> encryptedDataDetails, ExpressionEvaluator maskingEvaluator) {
    List<String> processExecutorArguments = new ArrayList<>();
    processExecutorArguments.add("/bin/sh");
    processExecutorArguments.add("-c");
    processExecutorArguments.add("terraform --version");

    return generateExecutionCapabilitiesForProcessExecutor(
        TERRAFORM, processExecutorArguments, encryptedDataDetails, maskingEvaluator);
  }

  public static List<ExecutionCapability> generateExecutionCapabilitiesForTerragrunt(
      List<EncryptedDataDetail> encryptedDataDetails, ExpressionEvaluator maskingEvaluator) {
    List<String> processExecutorArguments = new ArrayList<>();
    processExecutorArguments.add("/bin/sh");
    processExecutorArguments.add("-c");
    processExecutorArguments.add("terragrunt --version");

    return generateExecutionCapabilitiesForProcessExecutor(
        TERRAGRUNT, processExecutorArguments, encryptedDataDetails, maskingEvaluator);
  }
}
