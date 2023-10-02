/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.repositories;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.envvariable.beans.entity.BackstageEnvConfigVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableKeys;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableKeys;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableKeys;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
public class BackstageEnvVariableRepositoryCustomImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks BackstageEnvVariableRepositoryCustomImpl envVariableRepoCustomImpl;
  static final String TEST_ENV_NAME = "HARNESS_API_KEY";
  static final String TEST_ENV_NAME_HOST_PROXY = "HOST_PROXY_MAP";
  static final String TEST_SECRET_IDENTIFIER = "accountHarnessKey";
  static final String TEST_VALUE = "{\"github.com\":false}";
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdate() {
    BackstageEnvSecretVariableEntity envSecretEntity =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER).build();
    envSecretEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    envSecretEntity.setEnvName(TEST_ENV_NAME);
    envSecretEntity.setDeleted(false);
    Criteria criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                            .is(envSecretEntity.getAccountIdentifier())
                            .and(BackstageEnvVariableKeys.envName)
                            .is(envSecretEntity.getEnvName());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(BackstageEnvSecretVariableKeys.harnessSecretIdentifier, TEST_SECRET_IDENTIFIER);
    update.set(BackstageEnvSecretVariableKeys.isDeleted, false);
    when(mongoTemplate.findAndModify(
             eq(query), eq(update), any(FindAndModifyOptions.class), eq(BackstageEnvVariableEntity.class)))
        .thenReturn(envSecretEntity);

    assertEquals(envSecretEntity, envVariableRepoCustomImpl.update(envSecretEntity));

    BackstageEnvConfigVariableEntity envConfigEntity =
        BackstageEnvConfigVariableEntity.builder().value(TEST_VALUE).build();
    envConfigEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    envConfigEntity.setEnvName(TEST_ENV_NAME_HOST_PROXY);
    criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                   .is(envConfigEntity.getAccountIdentifier())
                   .and(BackstageEnvVariableKeys.envName)
                   .is(envConfigEntity.getEnvName());
    query = new Query(criteria);
    update = new Update();
    update.set(BackstageEnvConfigVariableKeys.value, TEST_VALUE);
    when(mongoTemplate.findAndModify(
             eq(query), eq(update), any(FindAndModifyOptions.class), eq(BackstageEnvVariableEntity.class)))
        .thenReturn(envConfigEntity);

    assertEquals(envConfigEntity, envVariableRepoCustomImpl.update(envConfigEntity));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateSecretIsDeleted() {
    BackstageEnvSecretVariableEntity envSecretEntity =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER).build();
    envSecretEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    envSecretEntity.setDeleted(true);
    Criteria criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                            .is(TEST_ACCOUNT_IDENTIFIER)
                            .and(BackstageEnvSecretVariableKeys.harnessSecretIdentifier)
                            .is(TEST_SECRET_IDENTIFIER);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(BackstageEnvSecretVariableKeys.isDeleted, true);

    when(mongoTemplate.findAndModify(
             eq(query), eq(update), any(FindAndModifyOptions.class), eq(BackstageEnvVariableEntity.class)))
        .thenReturn(envSecretEntity);

    Optional<BackstageEnvVariableEntity> actualEnvSecretEntityOpt =
        envVariableRepoCustomImpl.updateSecretIsDeleted(TEST_ACCOUNT_IDENTIFIER, TEST_SECRET_IDENTIFIER, true);

    assertTrue(actualEnvSecretEntityOpt.isPresent());
    assertEquals(envSecretEntity, actualEnvSecretEntityOpt.get());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndHarnessSecretIdentifier() {
    BackstageEnvSecretVariableEntity envSecretEntity =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER).build();
    envSecretEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    envSecretEntity.setEnvName(TEST_ENV_NAME);
    Criteria criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                            .is(TEST_ACCOUNT_IDENTIFIER)
                            .and(BackstageEnvSecretVariableKeys.harnessSecretIdentifier)
                            .is(TEST_SECRET_IDENTIFIER);
    Query query = new Query(criteria);
    when(mongoTemplate.findOne(query, BackstageEnvVariableEntity.class)).thenReturn(envSecretEntity);

    Optional<BackstageEnvVariableEntity> actualSecretEntityOpt =
        envVariableRepoCustomImpl.findByAccountIdentifierAndHarnessSecretIdentifier(
            TEST_ACCOUNT_IDENTIFIER, TEST_SECRET_IDENTIFIER);

    assertTrue(actualSecretEntityOpt.isPresent());
    assertEquals(envSecretEntity, actualSecretEntityOpt.get());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindAllByAccountIdentifierAndMultipleEnvNames() {
    BackstageEnvSecretVariableEntity envSecretEntity =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER).build();
    envSecretEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    envSecretEntity.setEnvName(TEST_ENV_NAME);
    Criteria criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                            .is(TEST_ACCOUNT_IDENTIFIER)
                            .and(BackstageEnvVariableKeys.envName)
                            .in(TEST_ENV_NAME);
    Query query = new Query(criteria);
    when(mongoTemplate.find(query, BackstageEnvVariableEntity.class))
        .thenReturn(Collections.singletonList(envSecretEntity));

    List<BackstageEnvVariableEntity> responseList =
        envVariableRepoCustomImpl.findAllByAccountIdentifierAndMultipleEnvNames(
            TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_ENV_NAME));

    assertEquals(1, responseList.size());
    assertEquals(envSecretEntity, responseList.get(0));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteAllByAccountIdentifierAndEnvNames() {
    Criteria criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                            .is(TEST_ACCOUNT_IDENTIFIER)
                            .and(BackstageEnvVariableKeys.envName)
                            .in(TEST_ENV_NAME);
    Query query = new Query(criteria);

    envVariableRepoCustomImpl.deleteAllByAccountIdentifierAndEnvNames(
        TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_ENV_NAME));

    verify(mongoTemplate).findAllAndRemove(query, BackstageEnvVariableEntity.class);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
