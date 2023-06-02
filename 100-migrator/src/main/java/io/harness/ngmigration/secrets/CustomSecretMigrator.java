/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.secrets;

import static io.harness.secretmanagerclient.SecretType.SecretText;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.delegate.beans.connector.customsecretmanager.TemplateLinkConfigForCustomSecretManager;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2.SecretDTOV2Builder;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.secretmanagerclient.ValueType;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.serializer.JsonUtils;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;

import software.wings.beans.NameValuePairWithDefault;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class CustomSecretMigrator implements SecretMigrator {
  @Override
  public SecretDTOV2Builder getSecretDTOBuilder(
      EncryptedData encryptedData, SecretManagerConfig secretManagerConfig, String secretManagerIdentifier) {
    String value = "";

    Set<EncryptedDataParams> params = encryptedData.getParameters();
    if (EmptyPredicate.isNotEmpty(params)) {
      List<Map<String, String>> variables = new ArrayList<>();
      params.stream()
          .filter(variable -> StringUtils.isNotBlank(variable.getName()))
          .forEach(variable
              -> variables.add(
                  ImmutableMap.of("name", variable.getName(), "type", "String", "value", variable.getValue())));

      value = JsonUtils.asJson(ImmutableMap.<String, Object>builder().put("environmentVariables", variables).build());
    }

    return SecretDTOV2.builder()
        .type(SecretText)
        .spec(SecretTextSpecDTO.builder()
                  .valueType(ValueType.CustomSecretManagerValues)
                  .value(value)
                  .secretManagerIdentifier(secretManagerIdentifier)
                  .build());
  }

  @Override
  public SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    CustomSecretsManagerConfig customSecretsManagerConfig = (CustomSecretsManagerConfig) secretManagerConfig;
    CgEntityId templateId = CgEntityId.builder()
                                .type(NGMigrationEntityType.SECRET_MANAGER_TEMPLATE)
                                .id(customSecretsManagerConfig.getTemplateId())
                                .build();
    if (!migratedEntities.containsKey(templateId)) {
      throw new InvalidRequestException("Could not fetch the template used in the custom secret manager");
    }

    Map<String, List<NameValuePairWithDefault>> inputs = new HashMap<>();
    NGYamlFile templateYamlFile = migratedEntities.get(templateId);

    NGTemplateConfig templateConfig = (NGTemplateConfig) templateYamlFile.getYaml();
    JsonNode spec = templateConfig.getTemplateInfoConfig().getSpec();
    JsonNode vars = spec.get("environmentVariables");
    if (vars != null) {
      List<Map<String, String>> envVars =
          JsonUtils.convertValue(vars, new TypeReference<List<Map<String, String>>>() {});
      List<NameValuePairWithDefault> envInputs = envVars.stream()
                                                     .map(map
                                                         -> NameValuePairWithDefault.builder()
                                                                .type(map.get("type"))
                                                                .name(map.get("name"))
                                                                .value(map.get("value"))
                                                                .build())
                                                     .collect(Collectors.toList());
      inputs.put("environmentVariables", envInputs);
    }

    CustomSecretManagerConnectorDTO secretManagerConnectorDTO =
        CustomSecretManagerConnectorDTO.builder()
            .onDelegate(customSecretsManagerConfig.isExecuteOnDelegate())
            .template(TemplateLinkConfigForCustomSecretManager.builder()
                          .templateRef(MigratorUtility.getIdentifierWithScope(templateYamlFile.getNgEntityDetail()))
                          .templateInputs(inputs)
                          .build())
            .build();
    return SecretManagerCreatedDTO.builder()
        .connector(secretManagerConnectorDTO)
        .secrets(Collections.emptyList())
        .build();
  }
}
