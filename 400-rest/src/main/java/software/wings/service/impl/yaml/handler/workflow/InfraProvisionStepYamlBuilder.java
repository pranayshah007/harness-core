/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.RESOURCE_NOT_FOUND;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.beans.InfrastructureProvisioner;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
@OwnedBy(CDP)
public class InfraProvisionStepYamlBuilder extends StepYamlBuilder {
  private static final String ENCRYPTED_TEXT = "ENCRYPTED_TEXT";
  private static final String VALUE_TYPE = "valueType";
  private static final String VALUE = "value";
  private static final String NAME = "name";
  protected static final String PROVISIONER_NAME = "provisionerName";
  private static final List<String> ENCRYPTION_TYPES =
      Stream.of(EncryptionType.values()).map(EncryptionType::getYamlName).collect(Collectors.toList());
  public static final String YAML_REF_DELIMITER = ":";

  @Inject private AppService appService;
  @Inject private SecretManager secretManager;
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;

  protected void convertPropertyIdsToNames(final String propertyName, final String appId, Object objectValue) {
    if (Objects.isNull(objectValue) || StringUtils.isBlank(appId)) {
      return;
    }

    String accountId = appService.getAccountIdByAppId(appId);
    if (StringUtils.isBlank(accountId)) {
      return;
    }

    try {
      BasicDBList subProperties = (BasicDBList) objectValue;
      subProperties.stream()
          .filter(Objects::nonNull)
          .map(BasicDBObject.class ::cast)
          .forEach(property -> replaceIdWithName(property, accountId));
    } catch (ClassCastException ex) {
      throw new InvalidArgumentsException(
          format("Unable to update cloud provider encrypted text values with ids for property: %s", propertyName), ex,
          USER);
    }
  }

  protected String convertProvisionerIdToName(String appId, Object objectValue) {
    String provisionerId = (String) objectValue;
    InfrastructureProvisioner provisioner = infrastructureProvisionerService.get(appId, provisionerId);
    notNullCheck("Provisioner not found for the given provisionerId:" + provisionerId, provisioner, USER);
    return provisioner.getName();
  }

  protected String convertProvisionerNameToId(String appId, Object objectValue) {
    String provisionerName = (String) objectValue;
    InfrastructureProvisioner provisioner = infrastructureProvisionerService.getByName(appId, provisionerName);
    notNullCheck("Provisioner not found for the given name:" + provisionerName, provisioner, USER);
    return provisioner.getUuid();
  }

  private void replaceIdWithName(BasicDBObject subProperty, final String accountId) {
    String valueType = String.valueOf(subProperty.get(VALUE_TYPE));
    if (ENCRYPTED_TEXT.equals(valueType)) {
      String secretYamlRef = (String) subProperty.get(VALUE);
      String secretYamlName = (String) subProperty.get(NAME);
      try {
        if (!isYamlRefSecretName(secretYamlName, secretYamlRef, accountId)) {
          String encryptedYamlRef = secretManager.getEncryptedYamlRef(accountId, secretYamlRef);
          subProperty.put(VALUE, encryptedYamlRef);
        }
      } catch (SecretManagementException sme) {
        log.error("Secret yaml ref does not exist for {}", secretYamlRef);
      }
    }
  }

  protected void convertPropertyNamesToIds(final String propertyName, final String accountId, Object objectValue) {
    if (Objects.isNull(objectValue) || StringUtils.isBlank(accountId)) {
      return;
    }

    try {
      ArrayList<LinkedHashMap<String, String>> subProperties = (ArrayList<LinkedHashMap<String, String>>) objectValue;
      subProperties.stream().filter(Objects::nonNull).forEach(property -> replaceNameWithId(property, accountId));
    } catch (ClassCastException ex) {
      throw new InvalidArgumentsException(
          format("Unable to update cloud provider encrypted text values with names for property: %s", propertyName), ex,
          USER);
    } catch (SecretManagementException sme) {
      throw new InvalidRequestException(sme.getMessage(), USER);
    }
  }

  private void replaceNameWithId(LinkedHashMap<String, String> subProperty, final String accountId) {
    String valueType = subProperty.get(VALUE_TYPE);
    if (ENCRYPTED_TEXT.equals(valueType)) {
      String secretYamlRef = subProperty.get(VALUE);
      String secretYamlName = subProperty.get(NAME);
      if (isYamlRefSecretName(secretYamlName, secretYamlRef, accountId)) {
        EncryptedData encryptedYamlRef = secretManager.getEncryptedDataFromYamlRef(secretYamlRef, accountId);
        subProperty.put(VALUE, encryptedYamlRef.getUuid());
      } else {
        secretManager.getEncryptedYamlRef(accountId, secretYamlRef);
      }
    }
  }

  private boolean isYamlRefSecretName(String secretYamlName, final String secretYamlRef, String accountId) {
    if (StringUtils.isBlank(secretYamlRef)) {
      throw new SecretManagementException(
          RESOURCE_NOT_FOUND, String.format("Could not find secret value for name: %s", secretYamlName), USER);
    }

    String[] yamlRefs = secretYamlRef.split(YAML_REF_DELIMITER);
    if (yamlRefs.length == 2) {
      if (!ENCRYPTION_TYPES.contains(yamlRefs[0])) {
        throw new SecretManagementException(
            RESOURCE_NOT_FOUND, String.format("Could not find type: %s in account: %s", yamlRefs[0], accountId), USER);
      } else {
        return true;
      }
    }
    return false;
  }
}
