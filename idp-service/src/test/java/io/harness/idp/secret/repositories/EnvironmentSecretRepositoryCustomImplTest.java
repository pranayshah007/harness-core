/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.repositories;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.secret.beans.entity.EnvironmentSecretEntity;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class EnvironmentSecretRepositoryCustomImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks EnvironmentSecretRepositoryCustomImpl envSecretRepoCustomImpl;
  static final String TEST_ENV_NAME = "HARNESS_API_KEY";
  static final String TEST_SECRET_IDENTIFIER = "accountHarnessKey";
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdate() {
    EnvironmentSecretEntity envSecretEntity = EnvironmentSecretEntity.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                  .envName(TEST_ENV_NAME)
                                                  .secretIdentifier(TEST_SECRET_IDENTIFIER)
                                                  .build();
    Criteria criteria = Criteria.where(EnvironmentSecretEntity.EnvironmentSecretsEntityKeys.accountIdentifier)
                            .is(envSecretEntity.getAccountIdentifier())
                            .and(EnvironmentSecretEntity.EnvironmentSecretsEntityKeys.envName)
                            .is(envSecretEntity.getEnvName());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(
        EnvironmentSecretEntity.EnvironmentSecretsEntityKeys.secretIdentifier, envSecretEntity.getSecretIdentifier());
    when(mongoTemplate.findAndModify(
             eq(query), eq(update), any(FindAndModifyOptions.class), eq(EnvironmentSecretEntity.class)))
        .thenReturn(envSecretEntity);
    assertEquals(envSecretEntity, envSecretRepoCustomImpl.update(envSecretEntity));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
