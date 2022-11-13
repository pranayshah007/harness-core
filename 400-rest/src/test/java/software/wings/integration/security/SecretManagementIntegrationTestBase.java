/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.security;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.EncryptedData;
import io.harness.beans.SecretChangeLog;
import io.harness.beans.SecretText;
import io.harness.helpers.ext.vault.SecretEngineSummary;
import io.harness.rest.RestResponse;
import io.harness.scm.SecretName;
import io.harness.secretmanagers.SecretManagerConfigService;

import software.wings.beans.KmsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.VaultConfig;
import software.wings.integration.IntegrationTestBase;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.io.File;
import java.util.List;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.junit.Before;

/**
 * @author marklu on 10/1/19
 */
public abstract class SecretManagementIntegrationTestBase extends IntegrationTestBase {
  @Inject private SecretManagementDelegateService secretManagementDelegateService;
  @Inject private SecretManagerConfigService secretManagerConfigService;

  String vaultToken = System.getProperty("vault.token", "root");
  String kmsAccessKey = "AKIAJXKK6OAOHQ5MO34Q";
  String kmsArn = "arn:aws:kms:us-east-1:448640225317:key/4feb7890-a727-4f88-af43-378b5a88e77c";
  String kmsSecretKey;
  String kmsRegion = "us-east-1";

  protected KmsConfig kmsConfig;

  @Override
  @Before
  public void setUp() {
    super.loginAdminUser();
    kmsSecretKey = scmSecret.decryptToString(new SecretName("kms_qa_secret_key"));
    kmsConfig = KmsConfig.builder()
                    .name("TestAwsKMS")
                    .accessKey(kmsAccessKey)
                    .kmsArn(kmsArn)
                    .secretKey(kmsSecretKey)
                    .region(kmsRegion)
                    .build();
    kmsConfig.setAccountId(accountId);
    kmsConfig.setDefault(true);
  }

  String createEncryptedFile(String name, String fileName) {
    File fileToImport = new File(getClass().getClassLoader().getResource(fileName).getFile());

    MultiPart multiPart = new MultiPart();
    FormDataBodyPart filePart = new FormDataBodyPart("file", fileToImport, MediaType.MULTIPART_FORM_DATA_TYPE);
    FormDataBodyPart namePart = new FormDataBodyPart("name", name, MediaType.MULTIPART_FORM_DATA_TYPE);
    multiPart.bodyPart(filePart);
    multiPart.bodyPart(namePart);

    WebTarget target = client.target(API_BASE + "/secrets/add-file?accountId=" + accountId);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE), new GenericType<RestResponse<String>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    String encryptedDataId = restResponse.getResource();
    assertThat(isNotEmpty(encryptedDataId)).isTrue();

    return encryptedDataId;
  }

  String updateEncryptedFileWithNoContent(String name, String uuid) {
    MultiPart multiPart = new MultiPart();
    FormDataBodyPart uuidPart = new FormDataBodyPart("uuid", uuid, MediaType.MULTIPART_FORM_DATA_TYPE);
    FormDataBodyPart namePart = new FormDataBodyPart("name", name, MediaType.MULTIPART_FORM_DATA_TYPE);
    multiPart.bodyPart(uuidPart);
    multiPart.bodyPart(namePart);

    WebTarget target = client.target(API_BASE + "/secrets/update-file?accountId=" + accountId);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE), new GenericType<RestResponse<String>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    String encryptedDataId = restResponse.getResource();
    assertThat(isNotEmpty(encryptedDataId)).isTrue();

    return encryptedDataId;
  }

  String createSecretText(String name, String value, String path) {
    WebTarget target = client.target(API_BASE + "/secrets/add-secret?accountId=" + accountId);
    SecretText secretText = SecretText.builder().name(name).value(value).path(path).build();
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(secretText, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    String encryptedDataId = restResponse.getResource();
    assertThat(isNotEmpty(encryptedDataId)).isTrue();

    return encryptedDataId;
  }

  void updateSecretText(String uuid, String name, String value, String path) {
    WebTarget target = client.target(API_BASE + "/secrets/update-secret?accountId=" + accountId + "&uuid=" + uuid);
    SecretText secretText = SecretText.builder().name(name).value(value).path(path).build();
    RestResponse<Boolean> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(secretText, APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    Boolean updated = restResponse.getResource();
    assertThat(updated).isTrue();
  }

  void importSecretTextsFromCsv(String secretCsvFilePath) {
    File fileToImport = new File(getClass().getClassLoader().getResource(secretCsvFilePath).getFile());

    MultiPart multiPart = new MultiPart();
    FormDataBodyPart formDataBodyPart = new FormDataBodyPart("file", fileToImport, MediaType.MULTIPART_FORM_DATA_TYPE);
    multiPart.bodyPart(formDataBodyPart);

    WebTarget target = client.target(API_BASE + "/secrets/import-secrets?accountId=" + accountId);
    RestResponse<List<String>> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE), new GenericType<RestResponse<List<String>>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    assertThat(restResponse.getResource()).isNotNull();
  }

  void deleteEncryptedFile(String uuid) {
    WebTarget target = client.target(API_BASE + "/secrets/delete-file?accountId=" + accountId + "&uuid=" + uuid);
    RestResponse<Boolean> restResponse =
        getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse<Boolean>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    Boolean deleted = restResponse.getResource();
    assertThat(deleted).isTrue();
  }

  void deleteSecretText(String uuid) {
    WebTarget target = client.target(API_BASE + "/secrets/delete-secret?accountId=" + accountId + "&uuid=" + uuid);
    RestResponse<Boolean> restResponse =
        getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse<Boolean>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    Boolean deleted = restResponse.getResource();
    assertThat(deleted).isTrue();
  }

  void verifyVaultChangeLog(String uuid) {
    WebTarget target = client.target(API_BASE + "/secrets/change-logs?accountId=" + accountId + "&entityId=" + uuid
        + "&type=" + SettingVariableTypes.SECRET_TEXT);
    RestResponse<List<SecretChangeLog>> restResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<SecretChangeLog>>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    assertThat(restResponse.getResource().size() > 0).isTrue();
  }

  void verifySecretTextExists(String secretName) {
    EncryptedData encryptedData = secretManager.getSecretMappedToAccountByName(accountId, secretName);
    assertThat(encryptedData).isNotNull();
    assertThat(encryptedData.getPath()).isNull();
    assertThat(encryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
  }

  List<SettingAttribute> listSettingAttributes() {
    WebTarget target = client.target(API_BASE + "/secrets/list-values?accountId=" + accountId);
    RestResponse<List<SettingAttribute>> response =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<SettingAttribute>>>() {});
    // Verify the vault config was deleted successfully
    assertThat(response.getResponseMessages()).isEmpty();
    assertThat(response.getResource().size() > 0).isTrue();
    return response.getResource();
  }

  List<SecretEngineSummary> listSecretEngines(VaultConfig vaultConfig) {
    WebTarget target = client.target(API_BASE + "/vault/list-engines?accountId=" + accountId);
    RestResponse<List<SecretEngineSummary>> response = getRequestBuilderWithAuthHeader(target).post(
        entity(vaultConfig, APPLICATION_JSON), new GenericType<RestResponse<List<SecretEngineSummary>>>() {});
    assertThat(response.getResponseMessages()).isEmpty();
    assertThat(response.getResource().size() > 0).isTrue();
    return response.getResource();
  }

  String createVaultConfig(VaultConfig vaultConfig) {
    WebTarget target = client.target(API_BASE + "/vault?accountId=" + accountId);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(vaultConfig, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    String vaultConfigId = restResponse.getResource();
    assertThat(isNotEmpty(vaultConfigId)).isTrue();

    return vaultConfigId;
  }

  void updateVaultConfig(VaultConfig vaultConfig) {
    WebTarget target = client.target(API_BASE + "/vault?accountId=" + accountId);
    vaultConfig.setName("TestVault_Different_Name");
    getRequestBuilderWithAuthHeader(target).post(
        entity(vaultConfig, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
  }

  void deleteVaultConfig(String vaultConfigId) {
    WebTarget target = client.target(API_BASE + "/vault?accountId=" + accountId + "&vaultConfigId=" + vaultConfigId);
    RestResponse<Boolean> deleteRestResponse =
        getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse<Boolean>>() {});
    // Verify the vault config was deleted successfully
    assertThat(deleteRestResponse.getResponseMessages()).isEmpty();
    assertThat(deleteRestResponse.getResource()).isTrue();
    assertThat(wingsPersistence.get(VaultConfig.class, vaultConfigId)).isNull();
  }

  void deleteKmsConfig(String kmsConfigId) {
    WebTarget target = client.target(API_BASE + "/kms?accountId=" + accountId + "&kmsConfigId=" + kmsConfigId);
    RestResponse<Boolean> deleteRestResponse =
        getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse<Boolean>>() {});
    // Verify the vault config was deleted successfully
    assertThat(deleteRestResponse.getResponseMessages()).isEmpty();
    assertThat(deleteRestResponse.getResource()).isTrue();
    assertThat(wingsPersistence.get(KmsConfig.class, kmsConfigId)).isNull();
  }

  String createKmsConfig(KmsConfig kmsConfig) {
    return createKmsConfigInternal(kmsConfig, "/kms/save-kms");
  }

  private String createKmsConfigInternal(KmsConfig kmsConfig, String kmsPath) {
    WebTarget target = client.target(API_BASE + kmsPath + "?accountId=" + accountId);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(kmsConfig, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    String kmsConfigId = restResponse.getResource();
    assertThat(isNotEmpty(kmsConfigId)).isTrue();

    return kmsConfigId;
  }
}
