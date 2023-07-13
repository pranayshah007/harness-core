/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.beans;

import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_OWNER;
import static io.harness.idp.onboarding.utils.Constants.ORGANIZATION;
import static io.harness.idp.onboarding.utils.Constants.PIPE_DELIMITER;
import static io.harness.idp.onboarding.utils.Constants.PROJECT;
import static io.harness.idp.onboarding.utils.Constants.SERVICE;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.HarnessBackstageEntities;

import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageCatalogEntitiesBeansTest {
  static final String TEST_ENTITY_NAME = "entityName";
  static final String TEST_ENTITY_IDENTIFIER = "entityIdentifier";
  static final String TEST_ENTITY_DOMAIN = "domainEntity";
  static final String TEST_ENTITY_SYSTEM = "domainSystem";

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBackstageCatalogDomainEntity() {
    BackstageCatalogDomainEntity backstageCatalogDomainEntity = BackstageCatalogDomainEntity.builder()
                                                                    .metadata(BackstageCatalogEntity.Metadata.builder()
                                                                                  .name(TEST_ENTITY_NAME)
                                                                                  .identifier(TEST_ENTITY_IDENTIFIER)
                                                                                  .build())
                                                                    .build();

    List<HarnessBackstageEntities> harnessBackstageEntities =
        BackstageCatalogDomainEntity.map(Collections.singletonList(backstageCatalogDomainEntity));

    assertNotNull(harnessBackstageEntities);
    assertEquals(1, harnessBackstageEntities.size());
    assertEquals(TEST_ENTITY_IDENTIFIER, harnessBackstageEntities.get(0).getIdentifier());
    assertEquals(ORGANIZATION, harnessBackstageEntities.get(0).getEntityType());
    assertEquals(TEST_ENTITY_NAME, harnessBackstageEntities.get(0).getName());
    assertEquals(ORGANIZATION, harnessBackstageEntities.get(0).getType());
    assertEquals(ENTITY_UNKNOWN_OWNER, harnessBackstageEntities.get(0).getOwner());
    assertEquals("", harnessBackstageEntities.get(0).getSystem());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBackstageCatalogSystemEntity() {
    BackstageCatalogSystemEntity backstageCatalogSystemEntity =
        BackstageCatalogSystemEntity.builder()
            .spec(BackstageCatalogSystemEntity.Spec.builder().domain(TEST_ENTITY_DOMAIN).build())
            .metadata(BackstageCatalogEntity.Metadata.builder()
                          .name(TEST_ENTITY_NAME)
                          .identifier(TEST_ENTITY_IDENTIFIER)
                          .build())
            .build();

    List<HarnessBackstageEntities> harnessBackstageEntities =
        BackstageCatalogSystemEntity.map(Collections.singletonList(backstageCatalogSystemEntity));

    assertNotNull(harnessBackstageEntities);
    assertEquals(1, harnessBackstageEntities.size());
    assertEquals(
        TEST_ENTITY_DOMAIN + PIPE_DELIMITER + TEST_ENTITY_IDENTIFIER, harnessBackstageEntities.get(0).getIdentifier());
    assertEquals(PROJECT, harnessBackstageEntities.get(0).getEntityType());
    assertEquals(TEST_ENTITY_NAME, harnessBackstageEntities.get(0).getName());
    assertEquals(PROJECT, harnessBackstageEntities.get(0).getType());
    assertEquals(ENTITY_UNKNOWN_OWNER, harnessBackstageEntities.get(0).getOwner());
    assertEquals(TEST_ENTITY_DOMAIN, harnessBackstageEntities.get(0).getSystem());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBackstageCatalogComponentEntity() {
    BackstageCatalogComponentEntity backstageCatalogComponentEntity =
        BackstageCatalogComponentEntity.builder()
            .spec(BackstageCatalogComponentEntity.Spec.builder()
                      .domain(TEST_ENTITY_DOMAIN)
                      .harnessSystem(TEST_ENTITY_SYSTEM)
                      .build())
            .metadata(BackstageCatalogEntity.Metadata.builder()
                          .name(TEST_ENTITY_NAME)
                          .identifier(TEST_ENTITY_IDENTIFIER)
                          .build())
            .build();

    List<HarnessBackstageEntities> harnessBackstageEntities =
        BackstageCatalogComponentEntity.map(Collections.singletonList(backstageCatalogComponentEntity));

    assertNotNull(harnessBackstageEntities);
    assertEquals(1, harnessBackstageEntities.size());
    assertEquals(TEST_ENTITY_DOMAIN + PIPE_DELIMITER + TEST_ENTITY_SYSTEM + PIPE_DELIMITER + TEST_ENTITY_IDENTIFIER,
        harnessBackstageEntities.get(0).getIdentifier());
    assertEquals(SERVICE, harnessBackstageEntities.get(0).getEntityType());
    assertEquals(TEST_ENTITY_NAME, harnessBackstageEntities.get(0).getName());
    assertEquals(SERVICE, harnessBackstageEntities.get(0).getType());
    assertEquals(ENTITY_UNKNOWN_OWNER, harnessBackstageEntities.get(0).getOwner());
    assertEquals(TEST_ENTITY_SYSTEM, harnessBackstageEntities.get(0).getSystem());
  }
}
