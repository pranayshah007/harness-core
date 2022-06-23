/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.cdng.configfile.validator.IndividualConfigFileStepValidator.validateConfigFileAttributes;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.mapper.ConfigFileOutcomeMapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStoreFile;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;
import io.harness.common.ParameterRuntimeFiledHelper;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class IndividualConfigFileStep implements SyncExecutable<ConfigFileStepParameters> {
  private static final String OUTPUT = "output";
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.CONFIG_FILE.getName()).setStepCategory(StepCategory.STEP).build();

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private FileStoreService fileStoreService;
  @Inject private NGEncryptedDataService ngEncryptedDataService;

  @Override
  public Class<ConfigFileStepParameters> getStepParametersClass() {
    return ConfigFileStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ConfigFileStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ConfigFileAttributes finalConfigFile = applyConfigFileOverrides(stepParameters);
    validateConfigFileAttributes(stepParameters.getIdentifier(), finalConfigFile, true);
    verifyConfigFileReference(stepParameters.getIdentifier(), finalConfigFile, ambiance);
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OUTPUT)
                         .outcome(ConfigFileOutcomeMapper.toConfigFileOutcome(stepParameters, finalConfigFile))
                         .build())
        .build();
  }

  private ConfigFileAttributes applyConfigFileOverrides(ConfigFileStepParameters stepParameters) {
    List<ConfigFileAttributes> configFileAttributesList = new LinkedList<>();

    if (stepParameters.getSpec() != null) {
      configFileAttributesList.add(stepParameters.getSpec());
    }

    if (stepParameters.getStageOverride() != null) {
      configFileAttributesList.add(stepParameters.getStageOverride());
    }

    if (isEmpty(configFileAttributesList)) {
      throw new InvalidArgumentsException("No found config file attributes");
    }

    ConfigFileAttributes resultantConfigFile = configFileAttributesList.get(0);
    for (ConfigFileAttributes configFileAttributesOverride :
        configFileAttributesList.subList(1, configFileAttributesList.size())) {
      resultantConfigFile = resultantConfigFile.applyOverrides(configFileAttributesOverride);
    }

    return resultantConfigFile;
  }

  private void verifyConfigFileReference(
      final String configFileIdentifier, ConfigFileAttributes configFileAttributes, Ambiance ambiance) {
    StoreConfig storeConfig = configFileAttributes.getStore().getValue().getSpec();
    String storeKind = storeConfig.getKind();
    if (HARNESS_STORE_TYPE.equals(storeKind)) {
      HarnessStore harnessStore = (HarnessStore) storeConfig;
      verifyFilesByPathAndScope(configFileIdentifier, harnessStore.getFiles(), ambiance);
      verifySecretFilesByRef(configFileIdentifier, harnessStore.getSecretFiles(), ambiance);
    } else {
      verifyConnectorByRef(configFileIdentifier, storeConfig, ambiance);
    }
  }

  private void verifyFilesByPathAndScope(
      final String configFileIdentifier, ParameterField<List<HarnessStoreFile>> files, Ambiance ambiance) {
    if (files == null || isEmpty(files.getValue()) || files.isExpression()) {
      return;
    }

    files.getValue()
        .stream()
        .filter(Objects::nonNull)
        .forEach(file -> verifyFileByPathAndScope(ambiance, file, configFileIdentifier));
  }

  private void verifyFileByPathAndScope(Ambiance ambiance, HarnessStoreFile file, final String configFileIdentifier) {
    String filePathValue =
        ParameterFieldHelper.getParameterFieldFinalValue(file.getPath())
            .orElseThrow(
                ()
                    -> new InvalidRequestException(format(
                        "Config file path cannot be null or empty, ConfigFile identifier: %s", configFileIdentifier)));
    Scope fileScopeValue =
        ParameterRuntimeFiledHelper.getScopeParameterFieldFinalValue(file.getScope())
            .orElseThrow(
                ()
                    -> new InvalidRequestException(format(
                        "Config file scope cannot be null or empty, ConfigFile identifier: %s", configFileIdentifier)));

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    io.harness.beans.Scope scope = io.harness.beans.Scope.of(
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), fileScopeValue);

    Optional<FileStoreNodeDTO> configFile = fileStoreService.getByPath(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), filePathValue, false);

    if (!configFile.isPresent()) {
      throw new InvalidRequestException(
          format("Config file not found in File Store with path: [%s], scope: [%s], ConfigFile identifier: [%s]",
              filePathValue, fileScopeValue, configFileIdentifier));
    }
  }

  private void verifySecretFilesByRef(
      final String configFileIdentifier, ParameterField<List<String>> secretFiles, Ambiance ambiance) {
    if (secretFiles == null || isEmpty(secretFiles.getValue()) || secretFiles.isExpression()) {
      return;
    }

    secretFiles.getValue().forEach(
        secretFileRef -> verifySecretFileByRef(ambiance, secretFileRef, configFileIdentifier));
  }

  private void verifySecretFileByRef(Ambiance ambiance, final String fileRef, final String configFileIdentifier) {
    if (isBlank(fileRef)) {
      throw new InvalidRequestException(
          format("Config file reference cannot be null or empty, ConfigFile identifier: %s", configFileIdentifier));
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef secretFileRef = IdentifierRefHelper.getIdentifierRef(
        fileRef, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    NGEncryptedData ngEncryptedData = ngEncryptedDataService.get(secretFileRef.getAccountIdentifier(),
        secretFileRef.getOrgIdentifier(), secretFileRef.getProjectIdentifier(), secretFileRef.getIdentifier());

    if (ngEncryptedData == null) {
      throw new InvalidRequestException(
          format("Config file not found in Encrypted Store with secretFQN: [%s], ConfigFile identifier: [%s]",
              secretFileRef.getFullyQualifiedName(), configFileIdentifier));
    }
  }

  private void verifyConnectorByRef(final String configFileIdentifier, StoreConfig storeConfig, Ambiance ambiance) {
    String connectorIdentifierRef =
        ParameterFieldHelper.getParameterFieldFinalValue(storeConfig.getConnectorReference())
            .orElseThrow(()
                             -> new InvalidRequestException(
                                 format("Config file connector ref cannot be null or empty, ConfigFile identifier: %s",
                                     configFileIdentifier)));

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifierRef,
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(format("Connector not found with identifier: [%s]", connectorIdentifierRef));
    }

    ConnectorUtils.checkForConnectorValidityOrThrow(connectorDTO.get());
  }
}
