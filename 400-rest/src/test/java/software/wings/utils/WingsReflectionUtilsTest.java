/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.settings.SettingVariableTypes.KMS;
import static software.wings.testutils.encryptionsamples.SampleEncryptableSetting.ENCRYPTED_ANNOTATION_KEY_FIELD;
import static software.wings.testutils.encryptionsamples.SampleEncryptableSetting.ENCRYPTED_ANNOTATION_VALUE_FIELD;
import static software.wings.utils.WingsReflectionUtils.buildSecretIdsToParentsMap;
import static software.wings.utils.WingsReflectionUtils.fetchSecretParentsUpdateDetailList;
import static software.wings.utils.WingsReflectionUtils.getEncryptableSetting;
import static software.wings.utils.WingsReflectionUtils.isSetByYaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.harness.CategoryTest;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.SecretParentsUpdateDetail;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.annotation.EncryptableSetting;
import software.wings.settings.SettingVariableTypes;
import software.wings.testutils.encryptionsamples.SampleEncryptableSetting;
import software.wings.testutils.encryptionsamples.SampleEncryptableSettingField;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class WingsReflectionUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBuildSecretIdsToParentMap_shouldReturnFilledMap_Test1() throws IllegalAccessException {
    String accountId = "accountId";
    String secretId1 = "secretId1";
    String secretId2 = "secretId2";
    SettingVariableTypes type = KMS;
    String uuid = "uuid";
    EncryptedDataParent keyParent = new EncryptedDataParent(uuid, type, ENCRYPTED_ANNOTATION_KEY_FIELD);
    EncryptedDataParent valueParent = new EncryptedDataParent(uuid, type, ENCRYPTED_ANNOTATION_VALUE_FIELD);

    EncryptableSetting encryptableSetting = SampleEncryptableSetting.builder()
                                                .uuid(uuid)
                                                .accountId(accountId)
                                                .encryptedKey(secretId1)
                                                .encryptedValue(secretId2)
                                                .type(type)
                                                .build();

    Map<String, Set<EncryptedDataParent>> secretIdsToParentMap = buildSecretIdsToParentsMap(encryptableSetting, uuid);

    assertThat(secretIdsToParentMap).isNotNull();
    assertThat(secretIdsToParentMap).hasSize(2);

    for (String secretId : secretIdsToParentMap.keySet()) {
      if (secretId.equals(secretId1)) {
        assertThat(secretIdsToParentMap.get(secretId)).contains(keyParent);
      } else if (secretId.equals(secretId2)) {
        assertThat(secretIdsToParentMap.get(secretId)).contains(valueParent);
      } else {
        fail("Should not contain any other string");
      }
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBuildSecretIdsToParentMap_shouldReturnFilledMap_Test2() throws IllegalAccessException {
    String accountId = "accountId";
    String expectedSecretId = "secretId";
    SettingVariableTypes type = KMS;
    String uuid = "uuid";
    EncryptedDataParent keyParent = new EncryptedDataParent(uuid, type, ENCRYPTED_ANNOTATION_KEY_FIELD);
    EncryptedDataParent valueParent = new EncryptedDataParent(uuid, type, ENCRYPTED_ANNOTATION_VALUE_FIELD);

    EncryptableSetting encryptableSetting = SampleEncryptableSetting.builder()
                                                .uuid(uuid)
                                                .accountId(accountId)
                                                .encryptedKey(expectedSecretId)
                                                .encryptedValue(expectedSecretId)
                                                .type(type)
                                                .build();

    Map<String, Set<EncryptedDataParent>> secretIdsToParentMap = buildSecretIdsToParentsMap(encryptableSetting, uuid);

    assertThat(secretIdsToParentMap).isNotNull();
    assertThat(secretIdsToParentMap).hasSize(1);
    Set<String> secretIds = secretIdsToParentMap.keySet();
    assertThat(secretIds).contains(expectedSecretId);

    for (String secretId : secretIdsToParentMap.keySet()) {
      assertThat(secretIdsToParentMap.get(secretId)).hasSize(2);
      assertThat(secretIdsToParentMap.get(secretId)).contains(keyParent);
      assertThat(secretIdsToParentMap.get(secretId)).contains(valueParent);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBuildSecretIdsToParentMap_shouldReturnFilledMap() throws IllegalAccessException {
    String accountId = "accountId";
    String expectedSecretId = "secretId";
    SettingVariableTypes type = KMS;
    String uuid = "uuid";
    EncryptedDataParent keyParent = new EncryptedDataParent(uuid, type, ENCRYPTED_ANNOTATION_KEY_FIELD);

    EncryptableSetting encryptableSetting = SampleEncryptableSetting.builder()
                                                .uuid(uuid)
                                                .accountId(accountId)
                                                .encryptedKey(expectedSecretId)
                                                .value(expectedSecretId.toCharArray())
                                                .type(type)
                                                .build();

    Map<String, Set<EncryptedDataParent>> secretIdsToParentMap = buildSecretIdsToParentsMap(encryptableSetting, uuid);

    assertThat(secretIdsToParentMap).isNotNull();
    assertThat(secretIdsToParentMap).hasSize(1);
    Set<String> secretIds = secretIdsToParentMap.keySet();
    assertThat(secretIds).contains(expectedSecretId);

    for (String secretId : secretIdsToParentMap.keySet()) {
      assertThat(secretIdsToParentMap.get(secretId)).hasSize(1);
      assertThat(secretIdsToParentMap.get(secretId)).contains(keyParent);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBuildSecretIdsToParentMap_shouldReturnEmptyMap() throws IllegalAccessException {
    String accountId = "accountId";
    String secretId1 = "secretId1";
    String secretId2 = "secretId2";
    String uuid = "uuid";

    EncryptableSetting encryptableSetting = SampleEncryptableSetting.builder()
                                                .uuid(uuid)
                                                .accountId(accountId)
                                                .key(secretId1.toCharArray())
                                                .value(secretId2.toCharArray())
                                                .type(KMS)
                                                .build();

    Map<String, Set<EncryptedDataParent>> secretIdsToParentMap = buildSecretIdsToParentsMap(encryptableSetting, uuid);

    assertThat(secretIdsToParentMap).isNotNull();
    assertThat(secretIdsToParentMap).hasSize(0);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFetchSecretParentsUpdateLists_shouldReturnEmpty() {
    String secretId1 = "secretId1";
    String secretId2 = "secretId2";
    String secretId3 = "secretId3";
    String secretId4 = "secretId4";
    EncryptedDataParent encryptedDataParent1 = new EncryptedDataParent("uuid", KMS, "field1");
    EncryptedDataParent encryptedDataParent2 = new EncryptedDataParent("uuid", KMS, "field2");
    Set<EncryptedDataParent> encryptedDataParents1 = new HashSet<>();
    encryptedDataParents1.add(encryptedDataParent1);
    encryptedDataParents1.add(encryptedDataParent2);
    Set<EncryptedDataParent> encryptedDataParents2 = new HashSet<>();
    encryptedDataParents2.add(encryptedDataParent1);
    Set<EncryptedDataParent> encryptedDataParents3 = new HashSet<>();
    encryptedDataParents3.add(encryptedDataParent2);

    Map<String, Set<EncryptedDataParent>> previous = new HashMap<>();
    previous.put(secretId1, encryptedDataParents1);
    previous.put(secretId2, encryptedDataParents2);
    previous.put(secretId4, encryptedDataParents1);

    Map<String, Set<EncryptedDataParent>> current = new HashMap<>();
    current.put(secretId2, encryptedDataParents3);
    current.put(secretId3, encryptedDataParents1);
    current.put(secretId4, encryptedDataParents1);

    List<SecretParentsUpdateDetail> secretParentsUpdateDetails = fetchSecretParentsUpdateDetailList(previous, current);
    assertThat(secretParentsUpdateDetails).isNotNull();
    assertThat(
        secretParentsUpdateDetails.stream().map(SecretParentsUpdateDetail::getSecretId).collect(Collectors.toSet()))
        .hasSize(3);

    for (SecretParentsUpdateDetail secretParentsUpdateDetail : secretParentsUpdateDetails) {
      if (secretParentsUpdateDetail.getSecretId().equals(secretId1)) {
        assertThat(secretParentsUpdateDetail.getParentsToRemove().equals(encryptedDataParents1)).isTrue();
        assertThat(secretParentsUpdateDetail.getParentsToAdd()).hasSize(0);
      } else if (secretParentsUpdateDetail.getSecretId().equals(secretId2)) {
        assertThat(secretParentsUpdateDetail.getParentsToRemove().equals(encryptedDataParents2)).isTrue();
        assertThat(secretParentsUpdateDetail.getParentsToAdd().equals(encryptedDataParents3)).isTrue();
      } else if (secretParentsUpdateDetail.getSecretId().equals(secretId3)) {
        assertThat(secretParentsUpdateDetail.getParentsToRemove()).hasSize(0);
        assertThat(secretParentsUpdateDetail.getParentsToAdd().equals(encryptedDataParents1)).isTrue();
      } else {
        fail("No other secretId should be present");
      }
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetEncryptableSetting_shouldReturnObject() {
    EncryptableSetting encryptableSetting = SampleEncryptableSetting.builder().build();
    Optional<EncryptableSetting> returnedEncryptableSetting = getEncryptableSetting(encryptableSetting);
    assertThat(returnedEncryptableSetting.isPresent()).isTrue();
    assertThat(returnedEncryptableSetting.get()).isEqualTo(encryptableSetting);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetEncryptableSetting_shouldReturnField() {
    SampleEncryptableSetting encryptableSetting = SampleEncryptableSetting.builder().build();
    SampleEncryptableSettingField sampleEncryptableSettingField = SampleEncryptableSettingField.builder()
                                                                      .entity(encryptableSetting)
                                                                      .accountId("accountId")
                                                                      .random(EncryptedData.builder().build())
                                                                      .build();
    Optional<EncryptableSetting> returnedEncryptableSetting = getEncryptableSetting(sampleEncryptableSettingField);
    assertThat(returnedEncryptableSetting.isPresent()).isTrue();
    assertThat(returnedEncryptableSetting.get()).isEqualTo(encryptableSetting);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetEncryptableSetting_shouldReturnEmpty() {
    SampleEncryptableSettingField sampleEncryptableSettingField = SampleEncryptableSettingField.builder()
                                                                      .entity(EncryptedData.builder().build())
                                                                      .accountId("accountId")
                                                                      .random(EncryptedData.builder().build())
                                                                      .build();
    Optional<EncryptableSetting> returnedEncryptableSetting = getEncryptableSetting(sampleEncryptableSettingField);
    assertThat(returnedEncryptableSetting.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testIsSetByYaml_shouldReturnTrue1() {
    String testString = EncryptionType.KMS.getYamlName().concat("secretId");
    boolean isSetByYaml = isSetByYaml(testString);
    assertThat(isSetByYaml).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testIsSetByYaml_shouldReturnFalse1() {
    String testString = "secret";
    boolean isSetByYaml = isSetByYaml(testString);
    assertThat(isSetByYaml).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testIsSetByYaml() throws IllegalAccessException {
    String secretId1 = EncryptionType.KMS.getYamlName().concat("secret1");
    String secretId2 = "secret2";

    EncryptableSetting encryptableSetting =
        SampleEncryptableSetting.builder().encryptedKey(secretId1).encryptedValue(secretId2).build();

    boolean isSetByYaml =
        isSetByYaml(encryptableSetting, FieldUtils.getField(encryptableSetting.getClass(), "encryptedKey", true));
    assertThat(isSetByYaml).isTrue();

    isSetByYaml =
        isSetByYaml(encryptableSetting, FieldUtils.getField(encryptableSetting.getClass(), "encryptedValue", true));
    assertThat(isSetByYaml).isFalse();

    isSetByYaml =
        isSetByYaml(encryptableSetting, FieldUtils.getField(encryptableSetting.getClass(), "accountId", true));
    assertThat(isSetByYaml).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testIsSetByYaml_shouldReturnTrue_secretName_prefix() {
    String testString = "secretname".concat("secretname");
    boolean isSetByYaml = isSetByYaml(testString);
    assertThat(isSetByYaml).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testIsSetByYaml_shouldReturnTrue3_secretId_prefix() {
    String testString = "secretid".concat("secretname");
    boolean isSetByYaml = isSetByYaml(testString);
    assertThat(isSetByYaml).isTrue();
  }
}
