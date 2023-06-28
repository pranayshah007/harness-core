/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.terragrunt.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.scm.GitCapabilityHelper;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfigType;
import io.harness.delegate.capability.ProcessExecutionCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.tuple.Pair;

@Data
@OwnedBy(CDP)
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class AbstractTerragruntTaskParameters
    implements TaskParameters, ExecutionCapabilityDemander, NestedAnnotationResolver {
  @NonNull String accountId;
  @NonNull String entityId;
  @Expression(ALLOW_SECRETS) @NonNull TerragruntRunConfiguration runConfiguration;
  @NonNull StoreDelegateConfig configFilesStore;
  @Expression(ALLOW_SECRETS) StoreDelegateConfig backendFilesStore;
  @Expression(ALLOW_SECRETS) List<StoreDelegateConfig> varFiles;
  EncryptionConfig planSecretManager;

  @Expression(ALLOW_SECRETS) List<String> targets;
  @Expression(ALLOW_SECRETS) String workspace;
  @Expression(ALLOW_SECRETS) Map<String, String> envVars;
  String stateFileId;

  long timeoutInMillis;

  @Nullable CommandUnitsProgress commandUnitsProgress;

  List<EncryptedDataDetail> encryptedDataDetailList;
  boolean tgModuleSourceInheritSSH;
  boolean encryptDecryptPlanForHarnessSMOnManager;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();

    executionCapabilities.addAll(ProcessExecutionCapabilityHelper.generateExecutionCapabilitiesForTerraform(
        encryptedDataDetailList, maskingEvaluator));
    executionCapabilities.addAll(ProcessExecutionCapabilityHelper.generateExecutionCapabilitiesForTerragrunt(
        encryptedDataDetailList, maskingEvaluator));

    addStoreCapabilities(configFilesStore, executionCapabilities);
    if (backendFilesStore != null) {
      addStoreCapabilities(backendFilesStore, executionCapabilities);
    }

    if (varFiles != null) {
      for (StoreDelegateConfig varFileStoreConfig : varFiles) {
        addStoreCapabilities(varFileStoreConfig, executionCapabilities);
      }
    }

    return executionCapabilities;
  }

  public List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> fetchDecryptionDetails() {
    List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails = new ArrayList<>();
    getStoreDecryptionDetails(configFilesStore).ifPresent(decryptionDetails::add);

    if (backendFilesStore != null) {
      getStoreDecryptionDetails(backendFilesStore).ifPresent(decryptionDetails::add);
    }

    if (varFiles != null) {
      for (StoreDelegateConfig varFileStoreConfig : varFiles) {
        getStoreDecryptionDetails(varFileStoreConfig).ifPresent(decryptionDetails::add);
      }
    }

    return decryptionDetails;
  }

  private void addStoreCapabilities(StoreDelegateConfig storeConfig, List<ExecutionCapability> executionCapabilities) {
    if (storeConfig.getType() == StoreDelegateConfigType.GIT) {
      GitStoreDelegateConfig gitStoreConfig = (GitStoreDelegateConfig) storeConfig;
      executionCapabilities.addAll(GitCapabilityHelper.fetchRequiredExecutionCapabilities(
          ScmConnectorMapper.toGitConfigDTO(gitStoreConfig.getGitConfigDTO()), encryptedDataDetailList,
          gitStoreConfig.getSshKeySpecDTO()));
    }
  }

  private Optional<Pair<DecryptableEntity, List<EncryptedDataDetail>>> getStoreDecryptionDetails(
      StoreDelegateConfig storeConfig) {
    if (storeConfig.getType() == StoreDelegateConfigType.GIT) {
      GitStoreDelegateConfig gitStoreConfig = (GitStoreDelegateConfig) storeConfig;
      GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreConfig.getGitConfigDTO());
      return Optional.of(Pair.of(gitConfigDTO.getGitAuth(), encryptedDataDetailList));
    }

    return Optional.empty();
  }
}
