/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.common.ParameterFieldHelper.getBooleanParameterFieldValue;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filestore.utils.FileStoreNodeUtils.mapFileNodes;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.FileReference;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.shell.TailFilePatternDto;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.delegate.task.ssh.config.SecretConfigFile;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ScriptType;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(CDP)
@Slf4j
public class SshCommandStepHelper extends CDStepHelper {
  @Inject private SshEntityHelper sshEntityHelper;
  @Inject private FileStoreService fileStoreService;
  @Inject private NGEncryptedDataService ngEncryptedDataService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject protected CDFeatureFlagHelper cdFeatureFlagHelper;

  public CommandTaskParameters buildCommandTaskParameters(
      @Nonnull Ambiance ambiance, @Nonnull CommandStepParameters commandStepParameters) {
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    switch (serviceOutcome.getType()) {
      case ServiceSpecType.SSH:
        return buildSshCommandTaskParameters(ambiance, commandStepParameters);
      case ServiceSpecType.WINRM:
        return buildWinRmTaskParameters(ambiance, commandStepParameters);
      default:
        throw new UnsupportedOperationException(
            format("Unsupported service type: [%s] selected for command step", serviceOutcome.getType()));
    }
  }

  private SshCommandTaskParameters buildSshCommandTaskParameters(
      @Nonnull Ambiance ambiance, @Nonnull CommandStepParameters commandStepParameters) {
    InfrastructureOutcome infrastructure = getInfrastructureOutcome(ambiance);
    Optional<ArtifactOutcome> artifactOutcome = resolveArtifactsOutcome(ambiance);
    Optional<ConfigFilesOutcome> configFilesOutcomeOptional = getConfigFilesOutcome(ambiance);
    Boolean onDelegate = getBooleanParameterFieldValue(commandStepParameters.onDelegate);
    return SshCommandTaskParameters.builder()
        .accountId(AmbianceUtils.getAccountId(ambiance))
        .executeOnDelegate(onDelegate)
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVariables(getOutputVars(commandStepParameters.getOutputVariables()))
        .environmentVariables(getEnvironmentVariables(commandStepParameters.getEnvironmentVariables()))
        .sshInfraDelegateConfig(sshEntityHelper.getSshInfraDelegateConfig(infrastructure, ambiance))
        .artifactDelegateConfig(
            artifactOutcome.map(outcome -> sshEntityHelper.getArtifactDelegateConfigConfig(outcome, ambiance))
                .orElse(null))
        .fileDelegateConfig(
            configFilesOutcomeOptional.map(configFilesOutcome -> getFileDelegateConfig(ambiance, configFilesOutcome))
                .orElse(null))
        .commandUnits(mapCommandUnits(commandStepParameters.getCommandUnits(), onDelegate))
        .host(commandStepParameters.getHost())
        .build();
  }

  private WinrmTaskParameters buildWinRmTaskParameters(
      @Nonnull Ambiance ambiance, @Nonnull CommandStepParameters commandStepParameters) {
    InfrastructureOutcome infrastructure = getInfrastructureOutcome(ambiance);
    Optional<ArtifactOutcome> artifactOutcome = resolveArtifactsOutcome(ambiance);
    Optional<ConfigFilesOutcome> configFilesOutcomeOptional = getConfigFilesOutcome(ambiance);
    Boolean onDelegate = getBooleanParameterFieldValue(commandStepParameters.onDelegate);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    return WinrmTaskParameters.builder()
        .accountId(accountId)
        .executeOnDelegate(onDelegate)
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVariables(getOutputVars(commandStepParameters.getOutputVariables()))
        .environmentVariables(getEnvironmentVariables(commandStepParameters.getEnvironmentVariables()))
        .winRmInfraDelegateConfig(sshEntityHelper.getWinRmInfraDelegateConfig(infrastructure, ambiance))
        .artifactDelegateConfig(
            artifactOutcome.map(outcome -> sshEntityHelper.getArtifactDelegateConfigConfig(outcome, ambiance))
                .orElse(null))
        .fileDelegateConfig(
            configFilesOutcomeOptional.map(configFilesOutcome -> getFileDelegateConfig(ambiance, configFilesOutcome))
                .orElse(null))
        .commandUnits(mapCommandUnits(commandStepParameters.getCommandUnits(), onDelegate))
        .host(commandStepParameters.getHost())
        .useWinRMKerberosUniqueCacheFile(
            cdFeatureFlagHelper.isEnabled(accountId, FeatureName.WINRM_KERBEROS_CACHE_UNIQUE_FILE))
        .disableWinRMCommandEncodingFFSet(
            cdFeatureFlagHelper.isEnabled(accountId, FeatureName.DISABLE_WINRM_COMMAND_ENCODING))
        .build();
  }

  private FileDelegateConfig getFileDelegateConfig(Ambiance ambiance, ConfigFilesOutcome configFilesOutcome) {
    List<StoreDelegateConfig> stores = new ArrayList<>(configFilesOutcome.size());
    for (ConfigFileOutcome configFileOutcome : configFilesOutcome.values()) {
      StoreConfig storeConfig = configFileOutcome.getStore();
      if (HARNESS_STORE_TYPE.equals(storeConfig.getKind())) {
        stores.add(buildHarnessStoreDelegateConfig(ambiance, (HarnessStore) storeConfig));
      }
    }

    return FileDelegateConfig.builder().stores(stores).build();
  }

  private HarnessStoreDelegateConfig buildHarnessStoreDelegateConfig(Ambiance ambiance, HarnessStore harnessStore) {
    harnessStore = (HarnessStore) cdExpressionResolver.updateExpressions(ambiance, harnessStore);
    List<String> files = ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles());
    List<String> secretFiles = ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles());

    List<ConfigFileParameters> configFileParameters = new ArrayList<>();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    if (isNotEmpty(files)) {
      files.forEach(scopedFilePath -> {
        FileReference fileReference = FileReference.of(scopedFilePath, ngAccess.getAccountIdentifier(),
            ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

        configFileParameters.addAll(fetchConfigFileFromFileStore(fileReference));
      });
    }

    if (isNotEmpty(secretFiles)) {
      secretFiles.forEach(secretFileRef -> {
        IdentifierRef fileRef = IdentifierRefHelper.getIdentifierRef(secretFileRef, ngAccess.getAccountIdentifier(),
            ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

        configFileParameters.add(fetchSecretConfigFile(fileRef));
      });
    }

    return HarnessStoreDelegateConfig.builder().configFiles(configFileParameters).build();
  }

  private List<ConfigFileParameters> fetchConfigFileFromFileStore(FileReference fileReference) {
    Optional<FileStoreNodeDTO> configFile = fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(),
        fileReference.getOrgIdentifier(), fileReference.getProjectIdentifier(), fileReference.getPath(), true);

    if (!configFile.isPresent()) {
      throw new InvalidRequestException(format("Config file not found in local file store, path [%s], scope: [%s]",
          fileReference.getPath(), fileReference.getScope()));
    }

    return mapFileNodes(configFile.get(),
        fileNode
        -> ConfigFileParameters.builder()
               .fileContent(fileNode.getContent())
               .fileName(fileNode.getName())
               .fileSize(fileNode.getSize())
               .build());
  }

  private ConfigFileParameters fetchSecretConfigFile(IdentifierRef fileRef) {
    SecretConfigFile secretConfigFile =
        SecretConfigFile.builder()
            .encryptedConfigFile(SecretRefHelper.createSecretRef(fileRef.getIdentifier()))
            .build();

    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(fileRef.getAccountIdentifier())
                            .orgIdentifier(fileRef.getOrgIdentifier())
                            .projectIdentifier(fileRef.getProjectIdentifier())
                            .build();

    List<EncryptedDataDetail> encryptedDataDetails =
        ngEncryptedDataService.getEncryptionDetails(ngAccess, secretConfigFile);

    if (isEmpty(encryptedDataDetails)) {
      throw new InvalidRequestException(format("Secret file with identifier %s not found", fileRef.getIdentifier()));
    }

    return ConfigFileParameters.builder()
        .fileName(secretConfigFile.getEncryptedConfigFile().getIdentifier())
        .isEncrypted(true)
        .secretConfigFile(secretConfigFile)
        .encryptionDataDetails(encryptedDataDetails)
        .build();
  }

  private List<NgCommandUnit> mapCommandUnits(List<CommandUnitWrapper> stepCommandUnits, boolean onDelegate) {
    if (isEmpty(stepCommandUnits)) {
      throw new InvalidRequestException("No command units found for configured step");
    }
    List<NgCommandUnit> commandUnits = new ArrayList<>(stepCommandUnits.size() + 2);
    commandUnits.add(NgInitCommandUnit.builder().build());

    List<NgCommandUnit> commandUnitsFromStep =
        stepCommandUnits.stream()
            .map(stepCommandUnit
                -> (stepCommandUnit.isScript()) ? mapScriptCommandUnit(stepCommandUnit.getCommandUnit(), onDelegate)
                                                : mapCopyCommandUnit(stepCommandUnit.getCommandUnit()))
            .collect(Collectors.toList());

    commandUnits.addAll(commandUnitsFromStep);
    commandUnits.add(NgCleanupCommandUnit.builder().build());
    return commandUnits;
  }

  private ScriptCommandUnit mapScriptCommandUnit(StepCommandUnit stepCommandUnit, boolean onDelegate) {
    if (stepCommandUnit == null) {
      throw new InvalidRequestException("Invalid command unit format specified");
    }

    if (!(stepCommandUnit.getSpec() instanceof ScriptCommandUnitSpec)) {
      throw new InvalidRequestException("Invalid script command unit specified");
    }

    ScriptCommandUnitSpec spec = (ScriptCommandUnitSpec) stepCommandUnit.getSpec();
    return ScriptCommandUnit.builder()
        .name(stepCommandUnit.getName())
        .script(getShellScript(spec.getSource()))
        .scriptType(spec.getShell().getScriptType())
        .tailFilePatterns(mapTailFilePatterns(spec.getTailFiles()))
        .workingDirectory(getWorkingDirectory(spec.getWorkingDirectory(), spec.getShell().getScriptType(), onDelegate))
        .build();
  }

  private CopyCommandUnit mapCopyCommandUnit(StepCommandUnit stepCommandUnit) {
    if (stepCommandUnit == null) {
      throw new InvalidRequestException("Invalid command unit format specified");
    }

    if (!(stepCommandUnit.getSpec() instanceof CopyCommandUnitSpec)) {
      throw new InvalidRequestException("Invalid copy command unit specified");
    }

    CopyCommandUnitSpec spec = (CopyCommandUnitSpec) stepCommandUnit.getSpec();
    return CopyCommandUnit.builder()
        .name(stepCommandUnit.getName())
        .sourceType(spec.getSourceType().getFileSourceType())
        .destinationPath(getParameterFieldValue(spec.getDestinationPath()))
        .build();
  }

  private List<TailFilePatternDto> mapTailFilePatterns(@Nonnull List<TailFilePattern> tailFiles) {
    if (isEmpty(tailFiles)) {
      return emptyList();
    }

    return tailFiles.stream()
        .map(it
            -> TailFilePatternDto.builder()
                   .filePath(getParameterFieldValue(it.getTailFile()))
                   .pattern(getParameterFieldValue(it.getTailPattern()))
                   .build())
        .collect(Collectors.toList());
  }

  private String getShellScript(@Nonnull ShellScriptSourceWrapper shellScriptSourceWrapper) {
    ShellScriptInlineSource shellScriptInlineSource = (ShellScriptInlineSource) shellScriptSourceWrapper.getSpec();
    return (String) shellScriptInlineSource.getScript().fetchFinalValue();
  }

  Map<String, String> getEnvironmentVariables(Map<String, Object> inputVariables) {
    if (EmptyPredicate.isEmpty(inputVariables)) {
      return new HashMap<>();
    }
    Map<String, String> res = new LinkedHashMap<>();
    inputVariables.forEach((key, value) -> {
      if (value instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
        if (parameterFieldValue.getValue() == null) {
          throw new InvalidRequestException(String.format("Env. variable [%s] value found to be null", key));
        }
        res.put(key, parameterFieldValue.getValue().toString());
      } else if (value instanceof String) {
        res.put(key, (String) value);
      } else {
        log.error(String.format(
            "Value other than String or ParameterField found for env. variable [%s]. value: [%s]", key, value));
      }
    });
    return res;
  }

  String getWorkingDirectory(
      ParameterField<String> workingDirectory, @Nonnull ScriptType scriptType, boolean onDelegate) {
    if (workingDirectory != null && EmptyPredicate.isNotEmpty(workingDirectory.getValue())) {
      return workingDirectory.getValue();
    }
    String commandPath = null;
    if (scriptType == ScriptType.BASH) {
      commandPath = "/tmp";
    } else if (scriptType == ScriptType.POWERSHELL) {
      commandPath = "%TEMP%";
      if (onDelegate) {
        commandPath = "/tmp";
      }
    }
    return commandPath;
  }

  List<String> getOutputVars(Map<String, Object> outputVariables) {
    if (EmptyPredicate.isEmpty(outputVariables)) {
      return emptyList();
    }

    List<String> outputVars = new ArrayList<>();
    outputVariables.forEach((key, val) -> {
      if (val instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) val;
        if (parameterFieldValue.getValue() == null) {
          throw new InvalidRequestException(String.format("Output variable [%s] value found to be null", key));
        }
        outputVars.add(((ParameterField<?>) val).getValue().toString());
      } else if (val instanceof String) {
        outputVars.add((String) val);
      } else {
        log.error(String.format(
            "Value other than String or ParameterField found for output variable [%s]. value: [%s]", key, val));
      }
    });
    return outputVars;
  }
}
