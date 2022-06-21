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

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStoreFile;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;
import io.harness.common.ParameterRuntimeFiledHelper;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.shell.SshCommandTaskParameters.SshCommandTaskParametersBuilder;
import io.harness.delegate.task.shell.TailFilePatternDto;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.steps.shellscript.ShellScriptHelperService;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

@Singleton
@OwnedBy(CDP)
public class SshCommandStepHelper extends CDStepHelper {
  @Inject private ShellScriptHelperService shellScriptHelperService;
  @Inject private SshEntityHelper sshEntityHelper;
  @Inject private FileStoreService fileStoreService;
  @Inject private NGEncryptedDataService ngEncryptedDataService;

  public SshCommandTaskParameters buildSshCommandTaskParameters(
      @Nonnull Ambiance ambiance, @Nonnull CommandStepParameters executeCommandStepParameters) {
    InfrastructureOutcome infrastructure = getInfrastructureOutcome(ambiance);
    Optional<ArtifactOutcome> artifactOutcome = resolveArtifactsOutcome(ambiance);
    Optional<ConfigFilesOutcome> configFilesOutcomeOptional = getConfigFilesOutcome(ambiance);
    Boolean onDelegate = getBooleanParameterFieldValue(executeCommandStepParameters.onDelegate);
    SshCommandTaskParametersBuilder<?, ?> builder = SshCommandTaskParameters.builder();
    return builder.accountId(AmbianceUtils.getAccountId(ambiance))
        .executeOnDelegate(onDelegate)
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .environmentVariables(
            shellScriptHelperService.getEnvironmentVariables(executeCommandStepParameters.getEnvironmentVariables()))
        .sshInfraDelegateConfig(sshEntityHelper.getSshInfraDelegateConfig(infrastructure, ambiance))
        .artifactDelegateConfig(
            artifactOutcome.map(outcome -> sshEntityHelper.getArtifactDelegateConfigConfig(outcome, ambiance))
                .orElse(null))
        .fileDelegateConfig(
            configFilesOutcomeOptional.map(configFilesOutcome -> getFileDelegateConfig(ambiance, configFilesOutcome))
                .orElse(null))
        .commandUnits(mapCommandUnits(executeCommandStepParameters.getCommandUnits(), onDelegate))
        .host(executeCommandStepParameters.getHost())
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
    List<HarnessStoreFile> files = ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles());
    List<String> secretFiles = ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles());

    List<ConfigFileParameters> configFileParameters = new ArrayList<>(files.size());
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    if (isNotEmpty(files)) {
      files.forEach(harnessStoreFile -> {
        Scope fileScope =
            ParameterRuntimeFiledHelper.getScopeParameterFieldFinalValue(harnessStoreFile.getScope())
                .orElseThrow(() -> new InvalidRequestException("Config file scope cannot be null or empty"));
        io.harness.beans.Scope scope = io.harness.beans.Scope.of(
            ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), fileScope);

        configFileParameters.add(fetchConfigFileFromFileStore(scope, harnessStoreFile));
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

  private ConfigFileParameters fetchConfigFileFromFileStore(
      io.harness.beans.Scope scope, @NotNull HarnessStoreFile file) {
    String filePathValue =
        ParameterFieldHelper.getParameterFieldFinalValue(file.getPath())
            .orElseThrow(() -> new InvalidRequestException("Config file path cannot be null or empty"));
    Optional<FileStoreNodeDTO> configFile = fileStoreService.getByPath(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), filePathValue, true);

    if (!configFile.isPresent()) {
      throw new InvalidRequestException(format("Config file not found in local file store, path [%s], scope: [%s]",
          filePathValue, ParameterRuntimeFiledHelper.getScopeParameterFieldFinalValue(file.getScope()).orElse(null)));
    }

    FileStoreNodeDTO fileStoreNodeDTO = configFile.get();
    if (NGFileType.FOLDER.equals(fileStoreNodeDTO.getType()) || !(fileStoreNodeDTO instanceof FileNodeDTO)) {
      throw new InvalidRequestException("Copy config can only accept file types, but provided folder");
    }

    FileNodeDTO fileNodeDTO = (FileNodeDTO) fileStoreNodeDTO;
    return ConfigFileParameters.builder()
        .fileContent(fileNodeDTO.getContent())
        .fileName(fileNodeDTO.getName())
        .fileSize(fileNodeDTO.getSize())
        .build();
  }

  private ConfigFileParameters fetchSecretConfigFile(IdentifierRef fileRef) {
    // TODO decryption should be handled on delegate
    NGEncryptedData ngEncryptedData = ngEncryptedDataService.get(fileRef.getAccountIdentifier(),
        fileRef.getOrgIdentifier(), fileRef.getProjectIdentifier(), fileRef.getIdentifier());
    if (ngEncryptedData == null) {
      throw new InvalidRequestException(
          format("Config file not found in encrypted store with identifier : [%s]", fileRef.getIdentifier()));
    }

    return ConfigFileParameters.builder()
        .fileContent(new String(ngEncryptedData.getEncryptedValue()))
        .fileName(ngEncryptedData.getName())
        .fileSize(getEncryptedDataLength(ngEncryptedData))
        .build();
  }

  private int getEncryptedDataLength(NGEncryptedData ngEncryptedData) {
    return new String(ngEncryptedData.getEncryptedValue()).getBytes(StandardCharsets.UTF_8).length;
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
        .workingDirectory(shellScriptHelperService.getWorkingDirectory(
            spec.getWorkingDirectory(), spec.getShell().getScriptType(), onDelegate))
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
}
