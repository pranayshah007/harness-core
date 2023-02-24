/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.k8s.manifest.ManifestHelper.normalizeFolderPath;

import static java.lang.String.format;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.serverless.ServerlessEntityHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.exception.GeneralException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AwsSamStepUtils extends CDStepHelper {
  @Inject private ServerlessEntityHelper serverlessEntityHelper;
  private static final String AWS_SAM_YAML_REGEX = ".*template\\.yaml";
  private static final String AWS_SAM_YML_REGEX = ".*template\\.yml";

  public GitStoreDelegateConfig getGitStoreDelegateConfig(
      Ambiance ambiance, GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome) {
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    String validationMessage = format("AWS SAM manifest with Id [%s]", manifestOutcome.getIdentifier());
    ConnectorInfoDTO connectorDTO = getConnectorDTO(connectorId, ambiance);
    validateManifest(gitStoreConfig.getKind(), connectorDTO, validationMessage);
    List<String> gitPaths = getFolderPathsForManifest(gitStoreConfig);
    return getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitPaths, ambiance);
  }

  public S3StoreDelegateConfig getS3StoreDelegateConfig(
      Ambiance ambiance, S3StoreConfig s3StoreConfig, ManifestOutcome manifestOutcome) {
    String connectorId = s3StoreConfig.getConnectorRef().getValue();
    String validationMessage = format("AWS SAM manifest with Id [%s]", manifestOutcome.getIdentifier());
    ConnectorInfoDTO connectorDTO = getConnectorDTO(connectorId, ambiance);
    validateManifest(s3StoreConfig.getKind(), connectorDTO, validationMessage);
    return getS3StoreDelegateConfig(s3StoreConfig, connectorDTO, ambiance);
  }

  private ConnectorInfoDTO getConnectorDTO(String connectorId, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return serverlessEntityHelper.getConnectorInfoDTO(connectorId, ngAccess);
  }

  private List<String> getFolderPathsForManifest(GitStoreConfig gitStoreConfig) {
    List<String> folderPaths = new ArrayList<>();

    List<String> paths = getParameterFieldValue(gitStoreConfig.getPaths());
    if ((paths != null) && (!paths.isEmpty())) {
      folderPaths.add(normalizeFolderPath(paths.get(0)));
    } else {
      folderPaths.add(normalizeFolderPath(getParameterFieldValue(gitStoreConfig.getFolderPath())));
    }
    return folderPaths;
  }

  public boolean isGitManifest(ManifestOutcome manifestOutcome) {
    return ManifestStoreType.isInGitSubset(manifestOutcome.getStore().getKind());
  }

  public String getTemplateDefaultFileName(String templateFilePath) {
    if (Pattern.matches(AWS_SAM_YAML_REGEX, templateFilePath)) {
      return "template.yaml";
    } else if (Pattern.matches(AWS_SAM_YML_REGEX, templateFilePath)) {
      return "template.yml";
    } else {
      throw new GeneralException("Invalid serverless file name");
    }
  }
}
