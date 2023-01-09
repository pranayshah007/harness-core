/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.dl.exportimport;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author marklu on 10/24/18
 */

@OwnedBy(PL)
@TargetModule(_955_ACCOUNT_MGMT)
@Slf4j
public class WingsMongoExportImportTest extends WingsBaseTest {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WingsMongoExportImport mongoExportImport;
  @Inject private Morphia morphia;

  private String accountId;
  private String appId;
  private String appName;

  @Before
  public void setup() {
    accountId = generateUuid();
    appName = generateUuid();
    appId = wingsPersistence.save(Application.Builder.anApplication().name(appName).accountId(accountId).build());
  }

  @After
  public void teardown() {
    // Cleanup
    wingsPersistence.delete(accountId, Application.class, appId);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCollectionExportImport() {
    String collectionName = Application.class.getAnnotation(Entity.class).value();

    List<String> records = mongoExportImport.exportRecords(new BasicDBObject("accountId", accountId), collectionName);
    assertThat(records).isNotNull();
    assertThat(records).hasSize(1);

    String appJson = records.get(0);
    log.info("Application JSON: " + appJson);

    // Remove the inserted application to make space for re-importing.
    wingsPersistence.delete(accountId, Application.class, appId);

    mongoExportImport.importRecords(collectionName, records, ImportMode.UPSERT);

    Application application = wingsPersistence.get(Application.class, appId);
    assertThat(application).isNotNull();
    assertThat(application.getName()).isEqualTo(appName);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetCollectionName() {
    String collectionName = mongoExportImport.getCollectionName(Application.class);
    assertThat(collectionName).isEqualTo("applications");
  }

  private boolean isAnnotatedExportable(Class<? extends PersistentEntity> clazz) {
    HarnessEntity harnessEntity = clazz.getAnnotation(HarnessEntity.class);
    return harnessEntity != null && harnessEntity.exportable();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetCollectionNameInExportableSet() {
    String toBeExportedCollectionName = "applications";
    Set<String> collectionNames = new HashSet<>();

    morphia.getMapper().getMappedClasses().forEach(mc -> {
      Class<? extends PersistentEntity> clazz = (Class<? extends PersistentEntity>) mc.getClazz();
      if (mc.getEntityAnnotation() != null && isAnnotatedExportable(clazz)) {
        String collectionName = mc.getEntityAnnotation().value();
        collectionNames.add(collectionName);
      }
    });
    assertThat(collectionNames).contains(toBeExportedCollectionName);
  }
}
