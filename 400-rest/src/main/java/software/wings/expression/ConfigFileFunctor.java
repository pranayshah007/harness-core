/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static software.wings.expression.SecretManagerFunctorInterface.obtainConfigFileExpression;

import io.harness.data.encoding.EncodingUtils;
import io.harness.exception.FunctorException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.functors.ExpressionFunctor;

import software.wings.beans.ConfigFile;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.ServiceTemplateService;

import java.nio.charset.Charset;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
public class ConfigFileFunctor implements ExpressionFunctor {
  // Some customers have large JSON files with size around 512K, so MAX_CONFIG_FILE_SIZE should be greater than that.
  static final int MAX_CONFIG_FILE_SIZE = 4 * ExpressionEvaluatorUtils.EXPANSION_LIMIT;

  private ServiceTemplateService serviceTemplateService;
  private ConfigService configService;
  private String appId;
  private String envId;
  private String serviceTemplateId;
  private int expressionFunctorToken;

  public Object getAsString(String relativeFilePath) {
    if (serviceTemplateId == null) {
      throw new FunctorException("Cannot evaluate expression: ${configFile.getAsString(\"" + relativeFilePath + "\")}");
    }
    ConfigFile configFile = obtainConfigFile(relativeFilePath);
    if (configFile.isEncrypted()) {
      return obtainConfigFileExpression(
          "obtainConfigFileAsString", relativeFilePath, configFile.getEncryptedFileId(), expressionFunctorToken);
    }
    byte[] fileContent = getConfigFileContent(relativeFilePath, configFile);
    return new String(fileContent, Charset.forName("UTF-8"));
  }

  public Object getAsBase64(String relativeFilePath) {
    if (serviceTemplateId == null) {
      throw new FunctorException("Cannot evaluate expression: ${configFile.getAsBase64(\"" + relativeFilePath + "\")}");
    }
    ConfigFile configFile = obtainConfigFile(relativeFilePath);
    if (configFile.isEncrypted()) {
      return obtainConfigFileExpression(
          "obtainConfigFileAsBase64", relativeFilePath, configFile.getEncryptedFileId(), expressionFunctorToken);
    }
    byte[] fileContent = getConfigFileContent(relativeFilePath, configFile);
    return EncodingUtils.encodeBase64(fileContent);
  }

  private byte[] getConfigFileContent(String relativeFilePath, ConfigFile configFile) {
    log.info("ConfigFile details: relativePath:{}, encrypted:{}, encryptedFileId:{}, fileId:{}",
        configFile.getRelativeFilePath(), configFile.isEncrypted(), configFile.getEncryptedFileId(),
        configFile.getUuid());

    byte[] contents = configService.getFileContent(appId, configFile);
    if (contents.length > MAX_CONFIG_FILE_SIZE) {
      throw new FunctorException("Too large config file " + relativeFilePath);
    }
    return contents;
  }

  private ConfigFile obtainConfigFile(String relativeFilePath) {
    log.info("Get content for file: {}", relativeFilePath);
    ConfigFile configFile =
        serviceTemplateService.computedConfigFileByRelativeFilePath(appId, envId, serviceTemplateId, relativeFilePath);
    if (configFile == null) {
      throw new FunctorException("Config file " + relativeFilePath + " not found");
    }
    return configFile;
  }
}
