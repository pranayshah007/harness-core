/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.k8s.manifest.ManifestHelper.normalizeFolderPath;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.exception.GeneralException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
public class ServerlessStepUtils extends CDStepHelper {
  @Inject private ServerlessEntityHelper serverlessEntityHelper;
  private static final String SERVERLESS_YAML_REGEX = ".*serverless\\.yaml";
  private static final String SERVERLESS_YML_REGEX = ".*serverless\\.yml";
  private static final String SERVERLESS_JSON_REGEX = ".*serverless\\.json";

  public GitStoreDelegateConfig getGitStoreDelegateConfig(
      Ambiance ambiance, GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome) {
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    String validationMessage = format("Serverless manifest with Id [%s]", manifestOutcome.getIdentifier());
    ConnectorInfoDTO connectorDTO = getConnectorDTO(connectorId, ambiance);
    validateManifest(gitStoreConfig.getKind(), connectorDTO, validationMessage);
    List<String> gitPaths = getFolderPathsForManifest(gitStoreConfig);
    return getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitPaths, ambiance);
  }

  public S3StoreDelegateConfig getS3StoreDelegateConfig(
      Ambiance ambiance, S3StoreConfig s3StoreConfig, ManifestOutcome manifestOutcome) {
    String connectorId = s3StoreConfig.getConnectorRef().getValue();
    String validationMessage = format("Serverless manifest with Id [%s]", manifestOutcome.getIdentifier());
    ConnectorInfoDTO connectorDTO = getConnectorDTO(connectorId, ambiance);
    validateManifest(s3StoreConfig.getKind(), connectorDTO, validationMessage);
    return getS3StoreDelegateConfig(s3StoreConfig, connectorDTO, ambiance);
  }

  private ConnectorInfoDTO getConnectorDTO(String connectorId, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return serverlessEntityHelper.getConnectorInfoDTO(connectorId, ngAccess);
  }

  public List<String> getFolderPathsForManifest(GitStoreConfig gitStoreConfig) {
    List<String> folderPaths = new ArrayList<>();

    List<String> paths = getParameterFieldValue(gitStoreConfig.getPaths());
    if ((paths != null) && (!paths.isEmpty())) {
      folderPaths.add(normalizeFolderPath(paths.get(0)));
    } else {
      folderPaths.add(normalizeFolderPath(getParameterFieldValue(gitStoreConfig.getFolderPath())));
    }
    return folderPaths;
    // todo: add error handling
  }

  public String getManifestDefaultFileName(String manifestFilePath) {
    if (Pattern.matches(SERVERLESS_YAML_REGEX, manifestFilePath)) {
      return "serverless.yaml";
    } else if (Pattern.matches(SERVERLESS_YML_REGEX, manifestFilePath)) {
      return "serverless.yml";
    } else if (Pattern.matches(SERVERLESS_JSON_REGEX, manifestFilePath)) {
      return "serverless.json";
    } else {
      throw new GeneralException("Invalid serverless file name");
    }
  }

  public Optional<ArtifactsOutcome> getArtifactsOutcome(Ambiance ambiance) {
    OptionalOutcome artifactsOutcomeOption = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (artifactsOutcomeOption.isFound()) {
      ArtifactsOutcome artifactsOutcome = (ArtifactsOutcome) artifactsOutcomeOption.getOutcome();
      return Optional.of(artifactsOutcome);
    }
    return Optional.empty();
  }

  public boolean isGitManifest(ManifestOutcome manifestOutcome) {
    return ManifestStoreType.isInGitSubset(manifestOutcome.getStore().getKind());
  }
}
