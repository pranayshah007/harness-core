/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.aws.sam.AwsSamFilePathContentConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.FetchFilesResult;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamStepHelper {
  @Inject private AwsSamStepUtils awsSamStepUtils;
  @Inject private EngineExpressionService engineExpressionService;
  public ManifestOutcome getAwsSamManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> awsSamManifests =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.AwsSamDirectory.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    if (isEmpty(awsSamManifests)) {
      throw new InvalidRequestException("Manifests are mandatory for AWS SAM step", USER);
    }
    if (awsSamManifests.size() > 1) {
      throw new InvalidRequestException("There can be only a single manifest for AWS SAM step", USER);
    }
    ManifestOutcome manifestOutcome = awsSamManifests.get(0);
    if (!(manifestOutcome instanceof AwsSamDirectoryManifestOutcome)) {
      throw new UnsupportedOperationException(
          format("Unsupported AWS SAM manifest type: [%s]", manifestOutcome.getType()));
    }
    return manifestOutcome;
  }

  public String getTemplateFilePath(ManifestOutcome manifestOutcome) {
    AwsSamDirectoryManifestOutcome awsSamManifestOutcome = (AwsSamDirectoryManifestOutcome) manifestOutcome;
    return getParameterFieldValue(awsSamManifestOutcome.getTemplateFilePath());
  }

  public String getConfigFilePath(ManifestOutcome manifestOutcome) {
    AwsSamDirectoryManifestOutcome awsSamManifestOutcome = (AwsSamDirectoryManifestOutcome) manifestOutcome;
    return getParameterFieldValue(awsSamManifestOutcome.getConfigFilePath());
  }

  public String getGitFolderPath(ManifestOutcome manifestOutcome) {
    AwsSamDirectoryManifestOutcome awsSamManifestOutcome = (AwsSamDirectoryManifestOutcome) manifestOutcome;
    GitStoreConfig gitStoreConfig = (GitStoreConfig) awsSamManifestOutcome.getStore();
    return getParameterFieldValue(gitStoreConfig.getPaths()).get(0);
  }

  public String getDefaultFilePath(String filePath, ManifestOutcome manifestOutcome) {
    if (manifestOutcome instanceof AwsSamDirectoryManifestOutcome) {
      String folderPath = getGitFolderPath(manifestOutcome);
      return getRelativePath(filePath, folderPath);
    }
    throw new UnsupportedOperationException(
        format("Unsupported AWS SAM manifest type: [%s]", manifestOutcome.getType()));
  }

  public String getRelativePath(String filePath, String prefixPath) {
    Path fileAbsolutePath = Paths.get(filePath).toAbsolutePath();
    Path prefixAbsolutePath = Paths.get(prefixPath).toAbsolutePath();
    return prefixAbsolutePath.relativize(fileAbsolutePath).toString();
  }

  public AwsSamFilePathContentConfig getFilePathRenderedContent(
      FetchFilesResult fetchFilesResult, ManifestOutcome manifestOutcome, Ambiance ambiance) {
    String filePath = fetchFilesResult.getFiles().get(0).getFilePath();
    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        awsSamStepUtils.getGitStoreDelegateConfig(ambiance, gitStoreConfig, manifestOutcome);
    filePath = getRelativePath(filePath, gitStoreDelegateConfig.getPaths().get(0));
    String fileContent =
        engineExpressionService.renderExpression(ambiance, fetchFilesResult.getFiles().get(0).getFileContent());
    return AwsSamFilePathContentConfig.builder().filePath(filePath).fileContent(fileContent).build();
  }
}
