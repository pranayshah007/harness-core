/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.NGCommonEntityConstants.FUNCTOR_BASE64_METHOD_NAME;
import static io.harness.NGCommonEntityConstants.FUNCTOR_STRING_METHOD_NAME;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.configfile.ConfigGitFile;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.encoding.EncodingUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.utils.FilePathUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
public class ConfigFileFunctor implements SdkFunctor {
  public static final int MAX_CONFIG_FILE_SIZE = 4 * ExpressionEvaluatorUtils.EXPANSION_LIMIT;
  private static final int NUMBER_OF_EXPECTED_ARGS = 2;
  private static final int METHOD_NAME_ARG = 0;
  private static final int CONFIG_FILE_IDENTIFIER_WITH_REFERENCE = 1;
  private static final String CONFIG_FILE_IDENTIFIER_REFERENCE_DELIMITER = ":";

  @Inject private CDStepHelper cdStepHelper;
  @Inject private CDExpressionResolver cdExpressionResolver;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    if (args.length != NUMBER_OF_EXPECTED_ARGS) {
      throw new InvalidArgumentsException(format("Invalid configFile functor arguments: %s", Arrays.asList(args)));
    }

    String methodName = args[METHOD_NAME_ARG];
    String configFileIdentifierWithReference = args[CONFIG_FILE_IDENTIFIER_WITH_REFERENCE];

    Pair<String, String> configFileIdentifierAndReference =
        getConfigFileIdentifierAndReference(configFileIdentifierWithReference);
    String configFileIdentifier = configFileIdentifierAndReference.getLeft();
    String reference = configFileIdentifierAndReference.getRight();

    ConfigFileOutcome configFileOutcome = getConfigFileOutcome(ambiance, configFileIdentifier);
    return getConfigFileContent(ambiance, configFileOutcome, reference, methodName);
  }

  @VisibleForTesting
  Pair<String, String> getConfigFileIdentifierAndReference(String configFileIdentifierWithReference) {
    if (isEmpty(configFileIdentifierWithReference)) {
      throw new InvalidArgumentsException("Config file identifier cannot be null or empty");
    }
    if (configFileIdentifierWithReference.startsWith(CONFIG_FILE_IDENTIFIER_REFERENCE_DELIMITER)
        || configFileIdentifierWithReference.endsWith(CONFIG_FILE_IDENTIFIER_REFERENCE_DELIMITER)) {
      throw new InvalidArgumentsException(
          format("Found invalid config file identifier, %s", configFileIdentifierWithReference));
    }

    if (!configFileIdentifierWithReference.contains(CONFIG_FILE_IDENTIFIER_REFERENCE_DELIMITER)) {
      return Pair.of(configFileIdentifierWithReference, null);
    }

    String[] configFileIdentifierAndReference =
        configFileIdentifierWithReference.split(CONFIG_FILE_IDENTIFIER_REFERENCE_DELIMITER, 2);
    return Pair.of(configFileIdentifierAndReference[0], configFileIdentifierAndReference[1]);
  }

  private ConfigFileOutcome getConfigFileOutcome(Ambiance ambiance, String configFileIdentifier) {
    Optional<ConfigFilesOutcome> configFilesOutcomeOpt = cdStepHelper.getConfigFilesOutcome(ambiance);
    if (configFilesOutcomeOpt.isEmpty()) {
      throw new InvalidArgumentsException("Not found config files");
    }

    ConfigFilesOutcome configFilesOutcome = configFilesOutcomeOpt.get();
    if (configFilesOutcome.get(configFileIdentifier) == null) {
      throw new InvalidArgumentsException(format("Not found config file with identifier: %s", configFileIdentifier));
    }

    return configFilesOutcome.get(configFileIdentifier);
  }

  private String getConfigFileContent(
      Ambiance ambiance, ConfigFileOutcome configFileOutcome, String reference, final String methodName) {
    String configFileIdentifier = configFileOutcome.getIdentifier();
    StoreConfig storeConfig = configFileOutcome.getStore();
    validateStoreConfig(configFileIdentifier, storeConfig);

    String storeConfigKind = storeConfig.getKind();
    if (HARNESS_STORE_TYPE.equals(storeConfigKind)) {
      List<String> files = ParameterFieldHelper.getParameterFieldValue(((HarnessStore) storeConfig).getFiles());
      List<String> secretFiles =
          ParameterFieldHelper.getParameterFieldValue(((HarnessStore) storeConfig).getSecretFiles());
      validateHarnessStoreConfigFiles(configFileIdentifier, reference, files, secretFiles);
      if (isEmpty(reference)) {
        return isNotEmpty(files) ? getFileStoreFileContent(ambiance, methodName, files.get(0))
                                 : getSecretFileContent(ambiance, methodName, secretFiles.get(0));
      }

      return FilePathUtils.isScopedFilePath(reference) ? getFileStoreFileContent(ambiance, methodName, reference)
                                                       : getSecretFileContent(ambiance, methodName, reference);
    } else if (ManifestStoreType.isInGitSubset(storeConfigKind)) {
      validateGitStoreConfigFiles(configFileOutcome.getIdentifier(), reference, configFileOutcome.getGitFiles());
      String gitFileContent =
          getGitFileContentOrThrow(configFileIdentifier, reference, configFileOutcome.getGitFiles());
      return updateGitFileContentByMethodAndRenderExpressions(ambiance, methodName, gitFileContent);
    } else {
      throw new InvalidRequestException(
          format("Invalid store kind for config file, configFileIdentifier: %s, storeConfigKind: %s",
              configFileIdentifier, storeConfigKind));
    }
  }

  private void validateStoreConfig(String configFileIdentifier, StoreConfig storeConfig) {
    if (storeConfig == null) {
      throw new InvalidRequestException(
          format("Not added store config to config file, configFileIdentifier: %s", configFileIdentifier));
    }
  }

  private void validateHarnessStoreConfigFiles(
      String configFileIdentifier, String reference, List<String> files, List<String> secretFiles) {
    if (isEmpty(files) && isEmpty(secretFiles)) {
      throw new InvalidArgumentsException(
          format("Not added Harness Store file or encrypted file to config file, configFileIdentifier: %s",
              configFileIdentifier));
    }

    if (isEmpty(reference)) {
      validateHarnessStoreConfigFilesWithoutReference(configFileIdentifier, files, secretFiles);
    }
  }

  private void validateHarnessStoreConfigFilesWithoutReference(
      String configFileIdentifier, List<String> files, List<String> secretFiles) {
    if (isNotEmpty(files) && isNotEmpty(secretFiles)) {
      throw new InvalidArgumentsException(
          format("Found file and encrypted file both attached to config file, configFileIdentifier: %s",
              configFileIdentifier));
    }

    if (isNotEmpty(files) && files.size() > 1) {
      throw new InvalidArgumentsException(
          format("Found more files attached to config file, configFileIdentifier: %s", configFileIdentifier));
    }

    if (isNotEmpty(secretFiles) && secretFiles.size() > 1) {
      throw new InvalidArgumentsException(
          format("Found more encrypted files attached to config file, configFileIdentifier: %s", configFileIdentifier));
    }
  }

  private String getFileStoreFileContent(Ambiance ambiance, final String methodName, final String scopedFilePath) {
    if (FUNCTOR_STRING_METHOD_NAME.equals(methodName)) {
      return cdStepHelper.getFileContentAsString(ambiance, scopedFilePath, MAX_CONFIG_FILE_SIZE);
    } else if (FUNCTOR_BASE64_METHOD_NAME.equals(methodName)) {
      return cdStepHelper.getFileContentAsBase64(ambiance, scopedFilePath, MAX_CONFIG_FILE_SIZE);
    } else {
      throw new InvalidArgumentsException(
          format("Unsupported configFile functor method: %s, scopedFilePath: %s", methodName, scopedFilePath));
    }
  }

  private String getSecretFileContent(Ambiance ambiance, final String methodName, final String secretRef) {
    if (FUNCTOR_STRING_METHOD_NAME.equals(methodName)) {
      return getSecretFileContentAsString(ambiance, secretRef);
    } else if (FUNCTOR_BASE64_METHOD_NAME.equals(methodName)) {
      return getSecretFileContentAsBase64(ambiance, secretRef);
    } else {
      throw new InvalidArgumentsException(
          format("Unsupported configFile functor method: %s, secretRef: %s", methodName, secretRef));
    }
  }

  private String getSecretFileContentAsString(Ambiance ambiance, final String ref) {
    return "${ngSecretManager.obtainSecretFileAsString(\"" + ref + "\", " + ambiance.getExpressionFunctorToken() + ")}";
  }

  private String getSecretFileContentAsBase64(Ambiance ambiance, final String ref) {
    return "${ngSecretManager.obtainSecretFileAsBase64(\"" + ref + "\", " + ambiance.getExpressionFunctorToken() + ")}";
  }

  private void validateGitStoreConfigFiles(
      String configFileIdentifier, String reference, List<ConfigGitFile> configGitFileList) {
    if (isEmpty(configGitFileList)) {
      throw new InvalidArgumentsException(
          format("Not added Git file to config file, configFileIdentifier: %s", configFileIdentifier));
    }

    if (isEmpty(reference)) {
      validateGitStoreConfigFilesWithoutReference(configFileIdentifier, configGitFileList);
    }
  }

  private void validateGitStoreConfigFilesWithoutReference(
      String configFileIdentifier, List<ConfigGitFile> configGitFileList) {
    if (isNotEmpty(configGitFileList) && configGitFileList.size() > 1) {
      throw new InvalidArgumentsException(
          format("Found more git files attached to config file, configFileIdentifier: %s", configFileIdentifier));
    }
  }

  private String getGitFileContentOrThrow(String configFileIdentifier, String reference, List<ConfigGitFile> gitFiles) {
    return isEmpty(reference)
        ? gitFiles.get(0).getFileContent()
        : gitFiles.stream()
              .filter(configGitFile -> configGitFile != null && reference.equals(configGitFile.getFilePath()))
              .map(ConfigGitFile::getFileContent)
              .findFirst()
              .orElseThrow(()
                               -> new InvalidArgumentsException(
                                   format("Not found Git file with reference: [%s], configFileIdentifier: %s",
                                       reference, configFileIdentifier)));
  }

  private String updateGitFileContentByMethodAndRenderExpressions(
      Ambiance ambiance, final String methodName, String content) {
    content = cdExpressionResolver.renderExpression(ambiance, content);
    if (FUNCTOR_STRING_METHOD_NAME.equals(methodName)) {
      return content;
    } else if (FUNCTOR_BASE64_METHOD_NAME.equals(methodName)) {
      return EncodingUtils.encodeBase64(content);
    } else {
      throw new InvalidArgumentsException(format("Unsupported configFile functor method: %s", methodName));
    }
  }
}
