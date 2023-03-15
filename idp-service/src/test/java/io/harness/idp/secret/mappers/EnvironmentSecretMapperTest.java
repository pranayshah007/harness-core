/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.mappers;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.secret.beans.entity.EnvironmentSecretEntity;
import io.harness.idp.secret.mappers.EnvironmentSecretMapper;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;
import io.harness.spec.server.idp.v1.model.EnvironmentSecretResponse;

import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class EnvironmentSecretMapperTest extends CategoryTest {
  static final String TEST_ENV_SECRET_ID = "envSecretId";
  static final String TEST_ENV_NAME = "HARNESS_API_KEY";
  static final String TEST_SECRET_IDENTIFIER = "accountHarnessKey";
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final String TEST_ENV_SECRET_ID1 = "envSecretId1";
  static final String TEST_ENV_NAME1 = "GITHUB_TOKEN";
  static final String TEST_SECRET_IDENTIFIER1 = "harnessKey";
  AutoCloseable openMocks;

  @Before
  public void setup() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToDTO() {
    EnvironmentSecretEntity envSecretEntity = EnvironmentSecretEntity.builder()
                                                  .id(TEST_ENV_SECRET_ID)
                                                  .envName(TEST_ENV_NAME)
                                                  .secretIdentifier(TEST_SECRET_IDENTIFIER)
                                                  .createdAt(System.currentTimeMillis())
                                                  .lastModifiedAt(System.currentTimeMillis())
                                                  .build();
    EnvironmentSecret envSecret = EnvironmentSecretMapper.toDTO(envSecretEntity);
    assertEquals(envSecretEntity.getId(), envSecret.getIdentifier());
    assertEquals(envSecretEntity.getEnvName(), envSecret.getEnvName());
    assertEquals(envSecretEntity.getSecretIdentifier(), envSecret.getSecretIdentifier());
    assertEquals(envSecretEntity.getCreatedAt(), envSecret.getCreated());
    assertEquals(envSecretEntity.getLastModifiedAt(), envSecret.getUpdated());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFromDTO() {
    EnvironmentSecret envSecret = new EnvironmentSecret();
    envSecret.setIdentifier(TEST_ENV_SECRET_ID);
    envSecret.setEnvName(TEST_ENV_NAME);
    envSecret.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret.created(System.currentTimeMillis());
    envSecret.updated(System.currentTimeMillis());
    EnvironmentSecretEntity envSecretEntity = EnvironmentSecretMapper.fromDTO(envSecret, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(envSecret.getIdentifier(), envSecretEntity.getId());
    assertEquals(envSecret.getEnvName(), envSecretEntity.getEnvName());
    assertEquals(envSecret.getSecretIdentifier(), envSecretEntity.getSecretIdentifier());
    assertEquals(envSecret.getCreated(), envSecretEntity.getCreatedAt());
    assertEquals(envSecret.getUpdated(), envSecretEntity.getLastModifiedAt());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToResponseList() {
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecret1.setIdentifier(TEST_ENV_SECRET_ID);
    envSecret1.setEnvName(TEST_ENV_NAME);
    envSecret1.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret1.created(System.currentTimeMillis());
    envSecret1.updated(System.currentTimeMillis());
    EnvironmentSecret envSecret2 = new EnvironmentSecret();
    envSecret2.setIdentifier(TEST_ENV_SECRET_ID1);
    envSecret2.setEnvName(TEST_ENV_NAME1);
    envSecret2.secretIdentifier(TEST_SECRET_IDENTIFIER1);
    envSecret2.created(System.currentTimeMillis());
    envSecret2.updated(System.currentTimeMillis());
    List<EnvironmentSecretResponse> response =
        EnvironmentSecretMapper.toResponseList(Arrays.asList(envSecret1, envSecret2));
    assertEquals(2, response.size());
    assertEquals(envSecret1, response.get(0).getSecret());
    assertEquals(envSecret2, response.get(1).getSecret());
  }

  @After
  public void teardown() throws Exception {
    openMocks.close();
  }
}
