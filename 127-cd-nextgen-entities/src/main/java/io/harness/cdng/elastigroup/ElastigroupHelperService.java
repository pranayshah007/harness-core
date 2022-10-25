/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileReference;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class ElastigroupHelperService {
  @Inject @Named(DEFAULT_CONNECTOR_SERVICE) private ConnectorService connectorService;
  @Inject @Named("PRIVILEGED") private SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private FileStoreService fileStoreService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private NGEncryptedDataService ngEncryptedDataService;
  @Inject ExceptionManager exceptionManager;
  @VisibleForTesting static final int defaultTimeoutInSecs = 30;

  public void validateSettingsStoreReferences(
      StoreConfigWrapper storeConfigWrapper, Ambiance ambiance, String entityType) {
    cdExpressionResolver.updateStoreConfigExpressions(ambiance, storeConfigWrapper);
    StoreConfig storeConfig = storeConfigWrapper.getSpec();
    String storeKind = storeConfig.getKind();
    if (HARNESS_STORE_TYPE.equals(storeKind)) {
      validateSettingsFileRefs((HarnessStore) storeConfig, ambiance, entityType);
    } else if (!storeKind.equals(ManifestStoreType.INLINE)) {
      throw new InvalidRequestException("Only Inline and Harness Store Type are supported as of now");
    }
  }

  private void validateSettingsFileRefs(HarnessStore harnessStore, Ambiance ambiance, String entityType) {
    if (ParameterField.isNull(harnessStore.getFiles()) && ParameterField.isNull(harnessStore.getSecretFiles())) {
      throw new InvalidArgumentsException(
          Pair.of(entityType, "Either 'files' or 'secretFiles' should be present in Harness store"));
    }

    if (!ParameterField.isNull(harnessStore.getFiles()) && !ParameterField.isNull(harnessStore.getSecretFiles())) {
      throw new InvalidArgumentsException(Pair.of(
          entityType, "Only one of 'files' or 'secretFiles' can be present. Please use only one of these fields"));
    }

    if (!ParameterField.isNull(harnessStore.getFiles())) {
      validateSettingsFile(harnessStore, ambiance, entityType);
    }

    if (!ParameterField.isNull(harnessStore.getSecretFiles())) {
      validateSecretsFile(harnessStore, ambiance, entityType);
    }
  }

  private void validateSettingsFile(HarnessStore harnessStore, Ambiance ambiance, String entityType) {
    if (harnessStore.getFiles().isExpression()) {
      return;
    }

    List<String> fileReferences = harnessStore.getFiles().getValue();
    if (isEmpty(fileReferences)) {
      throw new InvalidRequestException(
          format("Cannot find any file for %s, store kind: %s", entityType, harnessStore.getKind()));
    }
    if (fileReferences.size() > 1) {
      throw new InvalidRequestException(
          format("Only one file should be provided for %s, store kind: %s", entityType, harnessStore.getKind()));
    }

    validateSettingsFileByPath(harnessStore, ambiance, harnessStore.getFiles().getValue().get(0), entityType);
  }

  private void validateSecretsFile(HarnessStore harnessStore, Ambiance ambiance, String entityType) {
    if (harnessStore.getSecretFiles().isExpression()) {
      return;
    }
    List<String> secretFileReferences = harnessStore.getSecretFiles().getValue();

    if (secretFileReferences.size() > 1) {
      throw new InvalidArgumentsException(Pair.of(entityType,
          format("Only one secret file reference should be provided for store kind: %s", harnessStore.getKind())));
    }

    validateSecretFileReference(ambiance, entityType, secretFileReferences.get(0));
  }

  private void validateSettingsFileByPath(
      HarnessStore harnessStore, Ambiance ambiance, String scopedFilePath, String entityType) {
    if (isEmpty(scopedFilePath)) {
      throw new InvalidRequestException(
          format("File path not found for one for %s, store kind: %s", entityType, harnessStore.getKind()));
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    FileReference fileReference = FileReference.of(
        scopedFilePath, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    Optional<FileStoreNodeDTO> fileNode = fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(),
        fileReference.getOrgIdentifier(), fileReference.getProjectIdentifier(), fileReference.getPath(), false);

    if (!fileNode.isPresent()) {
      throw new InvalidRequestException(
          format("%s file not found in File Store with ref : [%s]", entityType, fileReference.getPath()));
    }
  }

  private void validateSettingsConnectorByRef(StoreConfig storeConfig, Ambiance ambiance, String entityType) {
    if (ParameterField.isNull(storeConfig.getConnectorReference())) {
      throw new InvalidRequestException(
          format("Connector ref field not present in %S, store kind: %s ", entityType, storeConfig.getKind()));
    }

    if (storeConfig.getConnectorReference().isExpression()) {
      return;
    }

    String connectorIdentifierRef = storeConfig.getConnectorReference().getValue();
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

  private void validateSecretFileReference(Ambiance ambiance, String entityType, String secretFile) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef secretFileRef = IdentifierRefHelper.getIdentifierRef(
        secretFile, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    NGEncryptedData ngEncryptedData = ngEncryptedDataService.get(secretFileRef.getAccountIdentifier(),
        secretFileRef.getOrgIdentifier(), secretFileRef.getProjectIdentifier(), secretFileRef.getIdentifier());

    if (ngEncryptedData == null) {
      throw new InvalidArgumentsException(
          Pair.of(entityType, format("Secret file with reference: %s not found", secretFile)));
    }
  }
}
