/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.mapper.ConfigFileOutcomeMapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedTypeException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.NGAccess;
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

  @Override
  public Class<ConfigFileStepParameters> getStepParametersClass() {
    return ConfigFileStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ConfigFileStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ConfigFileAttributes finalConfigFile = applyConfigFileOverrides(stepParameters);
    validateStoreReferences(stepParameters.getIdentifier(), finalConfigFile, ambiance);
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

    if (EmptyPredicate.isEmpty(configFileAttributesList)) {
      throw new InvalidArgumentsException("No found config file defined");
    }

    ConfigFileAttributes resultantConfigFile = configFileAttributesList.get(0);
    for (ConfigFileAttributes configFileAttributesOverride :
        configFileAttributesList.subList(1, configFileAttributesList.size())) {
      if (!configFileAttributesOverride.getType().equals(resultantConfigFile.getType())) {
        throw new UnexpectedTypeException(
            format("Unable to apply configFile override of type '%s' to configFile of type '%s' with identifier '%s'",
                configFileAttributesOverride.getType(), resultantConfigFile.getType(), stepParameters.getIdentifier()));
      }

      resultantConfigFile = resultantConfigFile.applyOverrides(configFileAttributesOverride);
    }

    return resultantConfigFile;
  }

  private void validateStoreReferences(
      String configFileIdentifier, ConfigFileAttributes configFileAttributes, Ambiance ambiance) {
    StoreConfig storeConfig = configFileAttributes.getStore().getValue().getSpec();
    String storeKind = storeConfig.getKind();
    if (HARNESS_STORE_TYPE.equals(storeKind)) {
      validateFileByRef(configFileIdentifier, (HarnessStoreConfig) storeConfig, ambiance);
    } else {
      validateConnectorByRef(configFileIdentifier, storeConfig, ambiance);
    }
  }

  private void validateFileByRef(
      String configFileIdentifier, HarnessStoreConfig harnessStoreConfig, Ambiance ambiance) {
    if (ParameterField.isNull(harnessStoreConfig.getFileReference())) {
      throw new InvalidRequestException(
          format("File ref field not present for store kind: %s in ConfigFile with identifier: %s ",
              harnessStoreConfig.getKind(), configFileIdentifier));
    }

    if (harnessStoreConfig.getFileReference().isExpression()) {
      return;
    }

    String fileIdentifierRef = harnessStoreConfig.getFileReference().getValue();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRefHelper.getIdentifierRef(fileIdentifierRef, ngAccess.getAccountIdentifier(),
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
  }

  private void validateConnectorByRef(String configFileIdentifier, StoreConfig storeConfig, Ambiance ambiance) {
    if (ParameterField.isNull(storeConfig.getConnectorReference())) {
      throw new InvalidRequestException(
          format("Connector ref field not present for store kind: %s in ConfigFile with identifier: %s ",
              storeConfig.getKind(), configFileIdentifier));
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
      throw new InvalidRequestException(format("Connector not found for identifier : [%s]", connectorIdentifierRef));
    }
    ConnectorUtils.checkForConnectorValidityOrThrow(connectorDTO.get());
  }
}
