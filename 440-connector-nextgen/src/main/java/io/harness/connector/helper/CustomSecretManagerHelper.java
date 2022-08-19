/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.helper;

import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.data.algorithm.HashGenerator;
import io.harness.engine.expressions.ShellScriptBaseDTO;
import io.harness.engine.expressions.ShellScriptYamlDTO;
import io.harness.engine.expressions.ShellScriptYamlExpressionEvaluator;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.dto.CustomSecretManagerConfigDTO;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.template.remote.TemplateResourceClient;

import software.wings.beans.CustomSecretNGManagerConfig;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomSecretManagerHelper {
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private NGConnectorSecretManagerService ngConnectorSecretManagerService;

  public Set<EncryptedDataParams> prepareEncryptedDataParamsSet(
      CustomSecretManagerConfigDTO customNGSecretManagerConfigDTO, String yaml) {
    String mergedYaml = NGRestUtils
                            .getResponse(templateResourceClient.applyTemplatesOnGivenYaml(
                                customNGSecretManagerConfigDTO.getAccountIdentifier(),
                                customNGSecretManagerConfigDTO.getOrgIdentifier(),
                                customNGSecretManagerConfigDTO.getProjectIdentifier(), null, null, null,
                                TemplateApplyRequestDTO.builder().originalEntityYaml(yaml).build()))
                            .getMergedPipelineYaml();

    log.info("Yaml received from template service is \n" + mergedYaml);
    int functorToken = HashGenerator.generateIntegerHash();
    ShellScriptYamlExpressionEvaluator shellScriptYamlExpressionEvaluator =
        new ShellScriptYamlExpressionEvaluator(mergedYaml, functorToken);
    ShellScriptBaseDTO shellScriptBaseDTO;
    try {
      shellScriptBaseDTO = YamlUtils.read(mergedYaml, ShellScriptYamlDTO.class).getShellScriptBaseDTO();
    } catch (IOException e) {
      throw new InvalidRequestException("Can not convert input to shell script base dto");
    }
    shellScriptBaseDTO = (ShellScriptBaseDTO) shellScriptYamlExpressionEvaluator.resolve(shellScriptBaseDTO, false);
    // get the script out of resolved yaml
    String script = shellScriptBaseDTO.getShellScriptSpec().getSource().getSpec().getScript().getValue();
    log.info("Resolved script is " + script);
    // pass the script to encryptor -> encryptor passes call to create delegate task which is then executed.
    // delegate task is picked by encryptor to execute it.

    // delegate task creation ->
    // Get template id , version , name all will be in secManagerConfigDTO-> template service (id, ver) ->
    // template (yaml) yeml -> resolve -> yaml -> filter relevant fields ( script field ) -> encryptor( resolved
    // yaml / script , config
    Set<EncryptedDataParams> encryptedDataParamsSet = new HashSet<>();
    encryptedDataParamsSet.add(EncryptedDataParams.builder().name("Script").value(script).build());
    encryptedDataParamsSet.add(
        EncryptedDataParams.builder().name("expressionFunctorToken").value(String.valueOf(functorToken)).build());
    return encryptedDataParamsSet;
  }

  public Set<EncryptedDataParams> prepareEncryptedDataParamsSet(
      CustomSecretManagerConfigDTO customNGSecretManagerConfigDTO) {
    String yaml = YamlUtils.write(ngConnectorSecretManagerService.getConnectorDTO(
        customNGSecretManagerConfigDTO.getAccountIdentifier(), customNGSecretManagerConfigDTO.getOrgIdentifier(),
        customNGSecretManagerConfigDTO.getProjectIdentifier(), customNGSecretManagerConfigDTO.getIdentifier()));
    return prepareEncryptedDataParamsSet(customNGSecretManagerConfigDTO, yaml);
  }

  public Set<EncryptedDataParams> prepareEncryptedDataParamsSet(
      CustomSecretManagerConfigDTO customNGSecretManagerConfigDTO, CustomSecretNGManagerConfig encryptionConfig) {
    String yaml = YamlUtils.write(ngConnectorSecretManagerService.getConnectorDTO(
        customNGSecretManagerConfigDTO.getAccountIdentifier(), customNGSecretManagerConfigDTO.getOrgIdentifier(),
        customNGSecretManagerConfigDTO.getProjectIdentifier(), customNGSecretManagerConfigDTO.getIdentifier()));
    return prepareEncryptedDataParamsSet(customNGSecretManagerConfigDTO, encryptionConfig, yaml);
  }

  private Set<EncryptedDataParams> prepareEncryptedDataParamsSet(
      CustomSecretManagerConfigDTO customNGSecretManagerConfigDTO, CustomSecretNGManagerConfig encryptionConfig,
      String yaml) {
    String mergedYaml = NGRestUtils
                            .getResponse(templateResourceClient.applyTemplatesOnGivenYaml(
                                customNGSecretManagerConfigDTO.getAccountIdentifier(),
                                customNGSecretManagerConfigDTO.getOrgIdentifier(),
                                customNGSecretManagerConfigDTO.getProjectIdentifier(), null, null, null,
                                TemplateApplyRequestDTO.builder().originalEntityYaml(yaml).build()))
                            .getMergedPipelineYaml();

    log.info("Yaml received from template service is \n" + mergedYaml);
    int functorToken = HashGenerator.generateIntegerHash();
    ShellScriptYamlExpressionEvaluator shellScriptYamlExpressionEvaluator =
        new ShellScriptYamlExpressionEvaluator(mergedYaml, functorToken);
    ShellScriptBaseDTO shellScriptBaseDTO;
    try {
      shellScriptBaseDTO = YamlUtils.read(mergedYaml, ShellScriptYamlDTO.class).getShellScriptBaseDTO();
    } catch (IOException e) {
      throw new InvalidRequestException("Can not convert input to shell script base dto");
    }
    shellScriptBaseDTO = (ShellScriptBaseDTO) shellScriptYamlExpressionEvaluator.resolve(shellScriptBaseDTO, false);
    // get the script out of resolved yaml
    String script = shellScriptBaseDTO.getShellScriptSpec().getSource().getSpec().getScript().getValue();
    log.info("Resolved script is " + script);
    // pass the script to encryptor -> encryptor passes call to create delegate task which is then executed.
    // delegate task is picked by encryptor to execute it.

    // delegate task creation ->
    // Get template id , version , name all will be in secManagerConfigDTO-> template service (id, ver) ->
    // template (yaml) yeml -> resolve -> yaml -> filter relevant fields ( script field ) -> encryptor( resolved
    // yaml / script , config
    Set<EncryptedDataParams> encryptedDataParamsSet = new HashSet<>();

    //    customSecretNGManagerConfig.setScript(script);
    encryptedDataParamsSet.add(EncryptedDataParams.builder().name("Script").value(script).build());
    encryptedDataParamsSet.add(
        EncryptedDataParams.builder().name("expressionFunctorToken").value(String.valueOf(functorToken)).build());
    return encryptedDataParamsSet;
  }
}