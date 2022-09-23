/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.scheduler.PersistentScheduler;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.dl.WingsMongoPersistence;
import software.wings.dl.exportimport.WingsMongoExportImport;
import software.wings.licensing.LicenseService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.UserService;
import software.wings.utils.AccountPermissionUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.mapping.MapperOptions;

/**
 * @author marklu on 2019-03-01
 */

@OwnedBy(PL)
@TargetModule(_955_ACCOUNT_MGMT)
@Slf4j
public class AccountExportImportResourceTest extends WingsBaseTest {
  @Mock private WingsMongoPersistence wingsMongoPersistence;
  @Mock private Morphia morphia;
  @Inject private WingsMongoExportImport wingsMongoExportImport;
  @Mock private AppService appService;
  @Mock private AccountService accountService;
  @Mock private LicenseService licenseService;
  @Mock private UsageRestrictionsService usageRestrictionsService;
  @Mock private UserService userService;
  @Mock private AuthService authService;
  @Mock private AccountPermissionUtils accountPermissionUtils;
  @Mock private MainConfiguration mainConfiguration;
  @Mock private PersistentScheduler persistentScheduler;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private HPersistence persistence;

  @Mock private Account account;
  @Mock private User user;

  private JsonArray users;
  private String userId;
  private String accountId;
  private AccountExportImportResource accountExportImportResource;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    Mapper mapper = mock(Mapper.class);
    MapperOptions mapperOptions = mock(MapperOptions.class);
    when(mapper.getOptions()).thenReturn(mapperOptions);
    when(morphia.getMapper()).thenReturn(mapper);

    accountExportImportResource = new AccountExportImportResource(wingsMongoPersistence, morphia,
        wingsMongoExportImport, accountService, licenseService, appService, authService, userService,
        accountPermissionUtils, persistentScheduler, featureFlagService, mainConfiguration);

    accountId = UUIDGenerator.generateUuid();
    userId = UUIDGenerator.generateUuid();

    String email = "user@harness.io";

    when(mainConfiguration.getExportAccountDataBatchSize()).thenReturn(1000);
    JsonObject userObject = new JsonObject();
    userObject.add("_id", new JsonPrimitive(userId));
    userObject.add("name", new JsonPrimitive("Harness User"));
    userObject.add("email", new JsonPrimitive(email));

    users = new JsonArray();
    users.add(userObject);

    when(wingsMongoPersistence.get(Account.class, accountId)).thenReturn(account);
    when(userService.getUserByEmail(email)).thenReturn(user);
    when(user.getUuid()).thenReturn(UUIDGenerator.generateUuid());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFindClashedUserIdMapping() {
    Map<String, String> userIdMapping = accountExportImportResource.findClashedUserIdMapping(accountId, users);
    assertThat(userIdMapping).hasSize(1);
    assertThat(userIdMapping.containsKey(userId)).isTrue();
    verify(wingsMongoPersistence).save(user);

    String usersJson = users.toString();
    String newUsersJson = accountExportImportResource.replaceClashedUserIds(usersJson, userIdMapping);
    log.info(userIdMapping.toString());
    log.info(usersJson);
    log.info(newUsersJson);

    assertThat(newUsersJson).isNotEqualTo(usersJson);
    assertThat(newUsersJson.indexOf(userId) < 0).isTrue();
  }
}
