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

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@OwnedBy(HarnessTeam.CDP)
public class ConfigFileFunctor implements SdkFunctor {
  public static final int MAX_CONFIG_FILE_SIZE = 4 * ExpressionEvaluatorUtils.EXPANSION_LIMIT;
  private static final int NUMBER_OF_EXPECTED_ARGS = 2;
  private static final int METHOD_NAME_ARG = 0;
  private static final int CONFIG_FILE_IDENTIFIER = 1;

  @Inject private CDStepHelper cdStepHelper;
  @Inject private CDExpressionResolver cdExpressionResolver;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    if (args.length != NUMBER_OF_EXPECTED_ARGS) {
      throw new InvalidArgumentsException(format("Invalid configFile functor arguments: %s", Arrays.asList(args)));
    }

    String methodName = args[METHOD_NAME_ARG];
    String configFileIdentifier = args[CONFIG_FILE_IDENTIFIER];

    ConfigFileOutcome configFileOutcome = getConfigFileOutcome(ambiance, configFileIdentifier);
    return getConfigFileContent(ambiance, configFileOutcome, methodName);
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

  private String getConfigFileContent(Ambiance ambiance, ConfigFileOutcome configFileOutcome, final String methodName) {
    String configFileIdentifier = configFileOutcome.getIdentifier();
    StoreConfig storeConfig = configFileOutcome.getStore();
    if (storeConfig == null) {
      throw new InvalidRequestException(
          format("Not added Harness file source to config file, configFileIdentifier: %s, store kind: %s",
              configFileIdentifier, storeConfig.getKind()));
    }

    if (HARNESS_STORE_TYPE.equals(storeConfig.getKind())) {
      List<String> files = ParameterFieldHelper.getParameterFieldValue(((HarnessStore) storeConfig).getFiles());
      List<String> secretFiles =
          ParameterFieldHelper.getParameterFieldValue(((HarnessStore) storeConfig).getSecretFiles());
      validateConfigFileAttachedFilesAndEncryptedFiles(configFileIdentifier, files, secretFiles);

      return isNotEmpty(files) ? getFileStoreFileContent(ambiance, methodName, files.get(0))
                               : getSecretFileContent(ambiance, methodName, secretFiles.get(0));
    } else if (ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      validateConfigGitFiles(configFileOutcome.getIdentifier(), configFileOutcome.getGitFiles());
      return getGitFileContent(ambiance, methodName, configFileOutcome.getGitFiles().get(0).getFileContent());
    } else {
      throw new InvalidRequestException(
          format("Invalid store kind for config file, configFileIdentifier: %s", configFileIdentifier));
    }
  }

  private String getGitFileContent(Ambiance ambiance, final String methodName, String content) {
    content = cdExpressionResolver.renderExpression(ambiance, content);
    if (FUNCTOR_STRING_METHOD_NAME.equals(methodName)) {
      return content;
    } else if (FUNCTOR_BASE64_METHOD_NAME.equals(methodName)) {
      return EncodingUtils.encodeBase64(content);
    } else {
      throw new InvalidArgumentsException(format("Unsupported configFile functor method: %s", methodName));
    }
  }

  private void validateConfigGitFiles(String configFileIdentifier, List<ConfigGitFile> configGitFileList) {
    if (isEmpty(configGitFileList)) {
      throw new InvalidArgumentsException(
          format("Not added file to config file, configFileIdentifier: %s", configFileIdentifier));
    }
    if (configGitFileList.size() > 1) {
      throw new InvalidArgumentsException(
          format("Found more files attached to config file, configFileIdentifier: %s", configFileIdentifier));
    }
  }

  private void validateConfigFileAttachedFilesAndEncryptedFiles(
      String configFileIdentifier, List<String> files, List<String> secretFiles) {
    if (isEmpty(files) && isEmpty(secretFiles)) {
      throw new InvalidArgumentsException(
          format("Not added file or encrypted file to config file, configFileIdentifier: %s", configFileIdentifier));
    }

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
}
