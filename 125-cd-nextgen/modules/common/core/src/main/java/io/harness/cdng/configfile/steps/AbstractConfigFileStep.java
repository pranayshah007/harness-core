/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.cdng.service.steps.constants.ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FileReference;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public abstract class AbstractConfigFileStep {
  @Inject private FileStoreService fileStoreService;
  @Inject private NGEncryptedDataService ngEncryptedDataService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  protected ConfigFileAttributes applyConfigFileOverrides(ConfigFileStepParameters stepParameters) {
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

  protected void checkForAccessOrThrow(Ambiance ambiance, List<ConfigFileWrapper> configFiles) {
    if (EmptyPredicate.isEmpty(configFiles)) {
      return;
    }
    List<EntityDetail> entityDetails = new ArrayList<>();

    for (ConfigFileWrapper configFile : configFiles) {
      Set<EntityDetailProtoDTO> entityDetailsProto =
          configFile == null ? Set.of() : entityReferenceExtractorUtils.extractReferredEntities(ambiance, configFile);
      List<EntityDetail> entityDetail =
          entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(emptyIfNull(entityDetailsProto)));
      if (EmptyPredicate.isNotEmpty(entityDetail)) {
        entityDetails.addAll(entityDetail);
      }
    }
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }

  protected void verifyConfigFileReference(final String configFileIdentifier, ConfigFileAttributes configFileAttributes,
      Ambiance ambiance, String fileLocation) {
    StoreConfig storeConfig = configFileAttributes.getStore().getValue().getSpec();
    String storeKind = storeConfig.getKind();
    if (HARNESS_STORE_TYPE.equals(storeKind)) {
      HarnessStore harnessStore = (HarnessStore) storeConfig;
      verifyFilesByPathAndScope(configFileIdentifier, harnessStore.getFiles(), ambiance, fileLocation);
      verifySecretFilesByRef(configFileIdentifier, harnessStore.getSecretFiles(), ambiance, fileLocation);
    } else {
      verifyConnectorByRef(configFileIdentifier, storeConfig, ambiance, fileLocation);
    }
  }
  protected void verifyConfigFileReference(
      final String configFileIdentifier, ConfigFileAttributes configFileAttributes, Ambiance ambiance) {
    verifyConfigFileReference(configFileIdentifier, configFileAttributes, ambiance, null);
  }

  void verifyFileByPathAndScope(
      Ambiance ambiance, String scopedFilePath, final String configFileIdentifier, String fileLocation) {
    if (isBlank(scopedFilePath)) {
      String errorMessage;
      if (EmptyPredicate.isEmpty(fileLocation)) {
        errorMessage =
            format("Config file reference cannot be null or empty, ConfigFile identifier: [%s]", configFileIdentifier);
      } else {
        errorMessage = format(
            "Config file reference cannot be null or empty, ConfigFile identifier: [%s], ConfigFile location: [%s]",
            configFileIdentifier, fileLocation);
      }
      throw new InvalidRequestException(errorMessage);
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    FileReference fileReference = FileReference.of(
        scopedFilePath, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    Optional<FileStoreNodeDTO> configFile = fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(),
        fileReference.getOrgIdentifier(), fileReference.getProjectIdentifier(), fileReference.getPath(), false);

    if (configFile.isEmpty()) {
      throw new InvalidRequestException(
          format("Config file not found in File Store with path: [%s], scope: [%s], ConfigFile identifier: [%s]",
              fileReference.getPath(), fileReference.getScope(), configFileIdentifier));
    }
  }

  private void verifyFilesByPathAndScope(final String configFileIdentifier, ParameterField<List<String>> parameterFiles,
      Ambiance ambiance, String fileLocation) {
    List<String> files = ParameterFieldHelper.getParameterFieldListValue(parameterFiles, true);
    files.forEach(file -> verifyFileByPathAndScope(ambiance, file, configFileIdentifier, fileLocation));
  }

  private void verifySecretFilesByRef(final String configFileIdentifier,
      ParameterField<List<String>> parameterSecretFiles, Ambiance ambiance, String fileLocation) {
    List<String> secretFiles = ParameterFieldHelper.getParameterFieldListValue(parameterSecretFiles, true);
    secretFiles.forEach(
        secretFileRef -> verifySecretFileByRef(ambiance, secretFileRef, configFileIdentifier, fileLocation));
  }

  private void verifySecretFileByRef(
      Ambiance ambiance, final String fileRef, final String configFileIdentifier, String fileLocation) {
    if (isBlank(fileRef)) {
      throw new InvalidRequestException(format(
          "Config file secret reference cannot be null or empty, ConfigFile identifier: [%s]", configFileIdentifier));
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef secretFileRef = IdentifierRefHelper.getIdentifierRef(
        fileRef, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    NGEncryptedData ngEncryptedData = ngEncryptedDataService.get(secretFileRef.getAccountIdentifier(),
        secretFileRef.getOrgIdentifier(), secretFileRef.getProjectIdentifier(), secretFileRef.getIdentifier());

    if (ngEncryptedData == null) {
      String errorMessage;
      if (EmptyPredicate.isEmpty(fileLocation)) {
        errorMessage =
            format("Config file not found in Encrypted Store with secretFQN: [%s], ConfigFile identifier: [%s]",
                secretFileRef.getFullyQualifiedName(), configFileIdentifier);
      } else {
        errorMessage = format(
            "Config file not found in Encrypted Store with secretFQN: [%s], ConfigFile identifier: [%s], ConfigFile Location: [%s]",
            secretFileRef.getFullyQualifiedName(), configFileIdentifier, fileLocation);
      }
      throw new InvalidRequestException(errorMessage);
    }
  }

  private void verifyConnectorByRef(
      final String configFileIdentifier, StoreConfig storeConfig, Ambiance ambiance, String fileLocation) {
    String connectorIdentifierRef =
        ParameterFieldHelper.getParameterFieldFinalValue(storeConfig.getConnectorReference()).orElseThrow(() -> {
          String errorMessage;
          if (EmptyPredicate.isEmpty(fileLocation)) {
            errorMessage = format(
                "Config file connector ref cannot be null or empty, ConfigFile identifier: [%s]", configFileIdentifier);
          } else {
            errorMessage = format(
                "Config file connector ref cannot be null or empty, ConfigFile identifier: [%s], ConfigFile Location: [%s]",
                configFileIdentifier, fileLocation);
          }
          return new InvalidRequestException(errorMessage);
        });

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifierRef,
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    if (!connectorDTO.isPresent()) {
      String errorMessage;
      if (EmptyPredicate.isEmpty(fileLocation)) {
        errorMessage = format("Connector not found with identifier: [%s], , ConfigFile identifier: [%s]",
            connectorIdentifierRef, configFileIdentifier);
      } else {
        errorMessage = format(
            "Connector not found with identifier: [%s], , ConfigFile identifier: [%s], Config file Location: [%s]",
            connectorIdentifierRef, configFileIdentifier, fileLocation);
      }
      throw new InvalidRequestException(errorMessage);
    }

    ConnectorUtils.checkForConnectorValidityOrThrow(connectorDTO.get());
  }

  @NotNull
  protected NgConfigFilesMetadataSweepingOutput fetchConfigFilesMetadataFromSweepingOutput(Ambiance ambiance) {
    final OptionalSweepingOutput resolveOptional = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(SERVICE_CONFIG_FILES_SWEEPING_OUTPUT));
    if (!resolveOptional.isFound()) {
      log.info("Could not find configFileSweepingOutput for the stage.");
    }
    return resolveOptional.isFound() ? (NgConfigFilesMetadataSweepingOutput) resolveOptional.getOutput()
                                     : NgConfigFilesMetadataSweepingOutput.builder().build();
  }
}
