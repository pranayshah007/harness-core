/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.audit.AuditHeader.Builder.anAuditHeader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.audit.AuditHeader;
import software.wings.audit.EntityAuditRecord;
import software.wings.audit.ResourceType;
import software.wings.beans.AccountAuditFilter;
import software.wings.beans.ApplicationAuditFilter;
import software.wings.beans.AuditPreference;
import software.wings.beans.CGConstants;
import software.wings.beans.EntityType;
import software.wings.beans.Event.Type;
import software.wings.beans.PreferenceType;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AuditPreferenceHelperTest extends WingsBaseTest {
  public static final String ACCOUNT_ID = "accountId";
  @Mock AppService appService;
  @InjectMocks @Inject AuditPreferenceHelper auditPreferenceHelper;
  @Inject WingsPersistence wingsPersistence;
  private AuditHeader header0;
  private AuditHeader header1;
  private AuditHeader header2;
  private AuditHeader header3;
  private AuditHeader header4;
  private AuditHeader header5;
  private AuditHeader header6;
  private AuditHeader header7;

  private String topLevelCriteriaFilter = "{"
      + "  \"preferenceType\" : \"AUDIT_PREFERENCE\","
      + "  \"appId\" : null,"
      + "  \"accountId\" : \"accountId\","
      + "  \"preferenceType\" : \"AUDIT_PREFERENCE\","
      + "  \"startTime\" : \"1559076765000\","
      + "  \"endTime\" : \"1560195705972\","
      + "  \"createdByUserIds\" : null,"
      + "  \"operationTypes\" : [\"CREATE\", \"UPDATE\"],"
      + "  \"includeAccountLevelResources\" : false,"
      + "  \"includeAppLevelResources\" : false,"
      + "  \"accountAuditFilter\" : null,"
      + "  \"applicationAuditFilter\" : null"
      + "}";

  private String appFilterCriteriaJson = "{"
      + "  \"preferenceType\" : \"AUDIT_PREFERENCE\","
      + "  \"appId\" : null,"
      + "  \"accountId\" : \"accountId\","
      + "  \"preferenceType\" : \"AUDIT_PREFERENCE\","
      + "  \"startTime\" : \"1559076765000\","
      + "  \"endTime\" : \"1560195705972\","
      + "  \"createdByUserIds\" : null,"
      + "  \"operationTypes\" : [\"CREATE\", \"UPDATE\"],"
      + "  \"includeAccountLevelResources\" : false,"
      + "  \"includeAppLevelResources\" : true,"
      + "  \"accountAuditFilter\" : null,"
      + "  \"applicationAuditFilter\" : {"
      + "       \"appIds\" : [\"AppId1\", \"AppId2\"],"
      + "       \"resourceIds\" : [\"AppresourceId1\", \"AppresourceId2\"],"
      + "       \"resourceTypes\" : [\"AppresourceType1\", \"AppresourceType2\"]"
      + "   }"
      + "}";

  private String accFilterCriteriaJson = "{"
      + "  \"preferenceType\" : \"AUDIT_PREFERENCE\","
      + "  \"appId\" : null,"
      + "  \"accountId\" : \"accountId\","
      + "  \"preferenceType\" : \"AUDIT_PREFERENCE\","
      + "  \"startTime\" : \"1559076765000\","
      + "  \"endTime\" : \"1560195705972\","
      + "  \"createdByUserIds\" : null,"
      + "  \"operationTypes\" : [\"CREATE\", \"UPDATE\"],"
      + "  \"includeAccountLevelResources\" : true,"
      + "  \"includeAppLevelResources\" : false,"
      + "  \"accountAuditFilter\" : null,"
      + "  \"accountAuditFilter\" : {"
      + "       \"resourceIds\" : [\"AccresourceId1\", \"AccresourceId2\"],"
      + "       \"resourceTypes\" : [\"AccresourceType1\", \"AccresourceType2\"]"
      + "   }"
      + "}";

  private String appAndAccountFilterCriteriaJson = "{"
      + "  \"preferenceType\" : \"AUDIT_PREFERENCE\","
      + "  \"appId\" : null,"
      + "  \"accountId\" : \"accountId\","
      + "  \"preferenceType\" : \"AUDIT_PREFERENCE\","
      + "  \"startTime\" : \"1559076765000\","
      + "  \"endTime\" : \"1560195705972\","
      + "  \"createdByUserIds\" : null,"
      + "  \"operationTypes\" : [\"CREATE\", \"UPDATE\"],"
      + "  \"includeAccountLevelResources\" : true,"
      + "  \"includeAppLevelResources\" : true,"
      + "  \"accountAuditFilter\" : null,"
      + "  \"applicationAuditFilter\" : {"
      + "       \"appIds\" : [\"AppId1\", \"AppId2\"],"
      + "       \"resourceIds\" : [\"AppresourceId1\", \"AppresourceId2\"],"
      + "       \"resourceTypes\" : [\"AppresourceType1\", \"AppresourceType2\"]"
      + "   },"
      + "  \"accountAuditFilter\" : {"
      + "       \"resourceIds\" : [\"AccresourceId1\", \"AccresourceId2\"],"
      + "       \"resourceTypes\" : [\"AccresourceType1\", \"AccresourceType2\"]"
      + "   }"
      + "}";

  @Before
  public void setup() throws IllegalAccessException {
    doReturn(Arrays.asList("app1", "app2")).when(appService).getAppIdsByAccountId(anyString());

    /**
     * Header0 - Header3 are AccountLevel entities
     */
    header0 = anAuditHeader().build();
    header0.setAccountId(ACCOUNT_ID);
    header0.setCreatedAt(1559076765100l);
    EntityAuditRecord record0 = EntityAuditRecord.builder()
                                    .appId(CGConstants.GLOBAL_APP_ID)
                                    .affectedResourceType(ResourceType.CLOUD_PROVIDER.name())
                                    .affectedResourceId("cp1")
                                    .operationType(Type.UPDATE.name())
                                    .entityName("c_p0")
                                    .entityType(ResourceType.CLOUD_PROVIDER.name())
                                    .entityId("cp1")
                                    .affectedResourceName("c_p0")
                                    .affectedResourceOperation(Type.UPDATE.name())
                                    .build();
    header0.setEntityAuditRecords(Arrays.asList(record0));

    header1 = anAuditHeader().build();
    header1.setCreatedAt(1559076765200l);
    header1.setAccountId(ACCOUNT_ID);
    EntityAuditRecord record1 = EntityAuditRecord.builder()
                                    .appId(CGConstants.GLOBAL_APP_ID)
                                    .affectedResourceType(ResourceType.CLOUD_PROVIDER.name())
                                    .affectedResourceId("cp2")
                                    .operationType(Type.CREATE.name())
                                    .entityName("c_p1")
                                    .entityType(ResourceType.CLOUD_PROVIDER.name())
                                    .entityId("cp2")
                                    .affectedResourceName("c_p1")
                                    .affectedResourceOperation(Type.CREATE.name())
                                    .build();
    header1.setEntityAuditRecords(Arrays.asList(record1));

    header2 = anAuditHeader().build();
    header2.setAccountId(ACCOUNT_ID);
    header2.setCreatedAt(1559076765300l);
    EntityAuditRecord record2 = EntityAuditRecord.builder()
                                    .appId(CGConstants.GLOBAL_APP_ID)
                                    .affectedResourceType(ResourceType.ARTIFACT_SERVER.name())
                                    .affectedResourceId("bambooId1")
                                    .operationType(Type.UPDATE.name())
                                    .entityName("bamboo")
                                    .entityType(ResourceType.ARTIFACT_SERVER.name())
                                    .entityId("bambooId1")
                                    .affectedResourceName("bamboo")
                                    .affectedResourceOperation(Type.UPDATE.name())
                                    .build();
    header2.setEntityAuditRecords(Arrays.asList(record2));

    header3 = anAuditHeader().build();
    header3.setAccountId(ACCOUNT_ID);
    header3.setCreatedAt(1559076765400l);
    EntityAuditRecord record3 = EntityAuditRecord.builder()
                                    .appId(CGConstants.GLOBAL_APP_ID)
                                    .affectedResourceType(ResourceType.COLLABORATION_PROVIDER.name())
                                    .affectedResourceId("gitId1")
                                    .operationType(Type.DELETE.name())
                                    .entityName("git")
                                    .entityType(ResourceType.COLLABORATION_PROVIDER.name())
                                    .entityId("gitId1")
                                    .affectedResourceName("git")
                                    .affectedResourceOperation(Type.DELETE.name())
                                    .build();
    header3.setEntityAuditRecords(Arrays.asList(record3));

    /**
     * Header4 - Header 7 are app level entities (for total 2 apps, app1 and app2)
     */
    header4 = anAuditHeader().build();
    header4.setAccountId(ACCOUNT_ID);
    header4.setCreatedAt(1559076765500l);
    EntityAuditRecord record4 = EntityAuditRecord.builder()
                                    .appId("app1")
                                    .affectedResourceType(ResourceType.SERVICE.name())
                                    .affectedResourceId("s1")
                                    .operationType(Type.CREATE.name())
                                    .entityName("")
                                    .entityType(EntityType.PCF_SERVICE_SPECIFICATION.name())
                                    .entityId("sp1")
                                    .affectedResourceName("service_01")
                                    .affectedResourceOperation(Type.UPDATE.name())
                                    .build();
    EntityAuditRecord record4_1 = EntityAuditRecord.builder()
                                      .appId("app1")
                                      .affectedResourceType(ResourceType.ENVIRONMENT.name())
                                      .affectedResourceId("e1")
                                      .operationType(Type.CREATE.name())
                                      .entityName("")
                                      .entityType(EntityType.INFRASTRUCTURE_MAPPING.name())
                                      .entityId("im1")
                                      .affectedResourceName("env_01")
                                      .affectedResourceOperation(Type.UPDATE.name())
                                      .build();
    header4.setEntityAuditRecords(Arrays.asList(record4, record4_1));

    header5 = anAuditHeader().build();
    header5.setCreatedAt(1559076765600l);
    header5.setAccountId(ACCOUNT_ID);
    EntityAuditRecord record5 = EntityAuditRecord.builder()
                                    .appId("app2")
                                    .affectedResourceType(ResourceType.WORKFLOW.name())
                                    .affectedResourceId("wf1")
                                    .operationType(Type.UPDATE.name())
                                    .entityName("w_1")
                                    .entityType(ResourceType.WORKFLOW.name())
                                    .entityId("w_1")
                                    .affectedResourceName("w_1")
                                    .affectedResourceOperation(Type.UPDATE.name())
                                    .build();
    EntityAuditRecord record5_1 = EntityAuditRecord.builder()
                                      .appId("app2")
                                      .affectedResourceType(ResourceType.PROVISIONER.name())
                                      .affectedResourceId("pr1")
                                      .operationType(Type.DELETE.name())
                                      .entityName("pr_1")
                                      .entityType(ResourceType.PROVISIONER.name())
                                      .entityId("pr1")
                                      .affectedResourceName("pr_1")
                                      .affectedResourceOperation(Type.DELETE.name())
                                      .build();
    header5.setEntityAuditRecords(Arrays.asList(record5, record5_1));

    header6 = anAuditHeader().build();
    header6.setAccountId(ACCOUNT_ID);
    header6.setCreatedAt(1559076765700l);
    EntityAuditRecord record6 = EntityAuditRecord.builder()
                                    .appId("app2")
                                    .affectedResourceType(ResourceType.PIPELINE.name())
                                    .affectedResourceId("p1")
                                    .operationType(Type.UPDATE.name())
                                    .entityName("p_1")
                                    .entityType(ResourceType.PIPELINE.name())
                                    .entityId("p1")
                                    .affectedResourceName("p_1")
                                    .affectedResourceOperation(Type.UPDATE.name())
                                    .build();
    EntityAuditRecord record6_1 = EntityAuditRecord.builder()
                                      .appId("app1")
                                      .affectedResourceType(ResourceType.SERVICE.name())
                                      .affectedResourceId("s2")
                                      .operationType(Type.CREATE.name())
                                      .entityName("s_1")
                                      .entityType(EntityType.SERVICE.name())
                                      .entityId("s2")
                                      .affectedResourceName("s_1")
                                      .affectedResourceOperation(Type.CREATE.name())
                                      .build();
    header6.setEntityAuditRecords(Arrays.asList(record6, record6_1));

    header7 = anAuditHeader().build();
    header7.setAccountId(ACCOUNT_ID);
    header7.setCreatedAt(1559076765800l);
    EntityAuditRecord record7 = EntityAuditRecord.builder()
                                    .appId("app2")
                                    .affectedResourceType(ResourceType.ENVIRONMENT.name())
                                    .affectedResourceId("e2")
                                    .operationType(Type.DELETE.name())
                                    .entityName("vc_1")
                                    .entityType(EntityType.VERIFICATION_CONFIGURATION.name())
                                    .entityId("vc1")
                                    .affectedResourceName("e_2")
                                    .affectedResourceOperation(Type.UPDATE.name())
                                    .build();
    header7.setEntityAuditRecords(Arrays.asList(record7));

    wingsPersistence.save(Arrays.asList(header0, header1, header2, header3, header4, header5, header6, header7));
  }

  @After
  public void cleanup() throws Exception {
    wingsPersistence.delete(header0);
    wingsPersistence.delete(header1);
    wingsPersistence.delete(header2);
    wingsPersistence.delete(header3);
    wingsPersistence.delete(header4);
    wingsPersistence.delete(header5);
    wingsPersistence.delete(header6);
    wingsPersistence.delete(header7);
  }

  /**
   * No AccountLevel or AppLevel criteria are provided
   * TopLevel criteria are given (accountId, createdAt, operationType)
   * @throws Exception
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testTopLevelCriteria_1() throws Exception {
    AuditPreference auditPreference =
        (AuditPreference) auditPreferenceHelper.parseJsonIntoPreference(topLevelCriteriaFilter);
    assertTopLevelCriteria(auditPreference);
    assertThat(auditPreference.isIncludeAccountLevelResources()).isFalse();
    assertThat(auditPreference.isIncludeAppLevelResources()).isFalse();

    // 1. Only UPDATE AND CREATE
    PageRequest<AuditHeader> pageRequest = getAuditHeaderPageRequest(auditPreference);
    PageResponse<AuditHeader> pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse())
        .containsExactlyInAnyOrder(header0, header1, header2, header4, header5, header6, header7);

    // 2. Only UPDATE
    auditPreference.setOperationTypes(Arrays.asList(Type.UPDATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse())
        .containsExactlyInAnyOrder(header0, header2, header4, header5, header6, header7);

    // 3. UPDATE, CREATE, DELETE
    auditPreference.setOperationTypes(Arrays.asList(Type.UPDATE.name(), Type.CREATE.name(), Type.DELETE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse())
        .containsExactlyInAnyOrder(header0, header1, header2, header3, header4, header5, header6, header7);

    // 4 No opType mentioned, means fetch all, similar to 3rd
    auditPreference.setOperationTypes(null);
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse())
        .containsExactlyInAnyOrder(header0, header1, header2, header3, header4, header5, header6, header7);
  }

  /**
   * TopLevel (accountId, createdAt, operationType) and AccountLevel criteria are givem
   * @throws Exception
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void test_OnlyAccountAndTopLevelCriteriaGiven() throws Exception {
    AuditPreference auditPreference =
        (AuditPreference) auditPreferenceHelper.parseJsonIntoPreference(accFilterCriteriaJson);
    assertTopLevelCriteria(auditPreference);
    assertThat(auditPreference.isIncludeAccountLevelResources()).isTrue();
    assertAccountLevelFilter(auditPreference);

    // 1, Account filter is included but empty. (fetch all AccountLevel Entities)
    auditPreference.setAccountAuditFilter(AccountAuditFilter.builder().resourceIds(null).resourceTypes(null).build());
    auditPreference.setOperationTypes(null);
    PageRequest<AuditHeader> pageRequest = getAuditHeaderPageRequest(auditPreference);
    PageResponse<AuditHeader> pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header0, header1, header2, header3);

    // 2, Account filter : {ResourceTypes : "CLOUD_PROVIDER"}, 2 RECORDS EXPECTED
    auditPreference.setAccountAuditFilter(AccountAuditFilter.builder()
                                              .resourceIds(null)
                                              .resourceTypes(Arrays.asList(ResourceType.CLOUD_PROVIDER.name()))
                                              .build());
    auditPreference.setOperationTypes(null);
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header0, header1);

    // 3, Account filter : {ResourceTypes : "CLOUD_PROVIDER"} + opType = {UPDATE} 2 RECORDS EXPECTED
    auditPreference.setAccountAuditFilter(AccountAuditFilter.builder()
                                              .resourceIds(null)
                                              .resourceTypes(Arrays.asList(ResourceType.CLOUD_PROVIDER.name()))
                                              .build());
    auditPreference.setOperationTypes(Arrays.asList(Type.UPDATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header0);

    // 4, AccFilterCriteria contains resourceIds. should fetch exact resource ignoring resourceTypes.
    auditPreference.setAccountAuditFilter(AccountAuditFilter.builder()
                                              .resourceIds(Arrays.asList("gitId1", "bambooId1"))
                                              .resourceTypes(Arrays.asList(ResourceType.CLOUD_PROVIDER.name()))
                                              .build());
    auditPreference.setOperationTypes(null);
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header2, header3);

    // 5, AccFilterCriteria contains resourceId and opType = Delete,  should fetch exact 1 resource.
    auditPreference.setAccountAuditFilter(AccountAuditFilter.builder()
                                              .resourceIds(Arrays.asList("gitId1", "bambooId1"))
                                              .resourceTypes(Arrays.asList(ResourceType.CLOUD_PROVIDER.name()))
                                              .build());
    auditPreference.setOperationTypes(Arrays.asList(Type.DELETE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header3);
  }

  /**
   * TopLevel (accountId, createdAt, operationType) and AppLevel Criteria are given
   * @throws Exception
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void test_onlyAppLevelCriteria() throws Exception {
    AuditPreference auditPreference =
        (AuditPreference) auditPreferenceHelper.parseJsonIntoPreference(appFilterCriteriaJson);
    assertTopLevelCriteria(auditPreference);
    assertThat(auditPreference.isIncludeAppLevelResources()).isTrue();
    assertApplicationFilter(auditPreference);

    // 1. App filter is included but empty. (fetch all AppLevel Entities)
    auditPreference.setApplicationAuditFilter(ApplicationAuditFilter.builder().build());
    auditPreference.setOperationTypes(null);
    PageRequest<AuditHeader> pageRequest = getAuditHeaderPageRequest(auditPreference);
    PageResponse<AuditHeader> pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header4, header5, header6, header7);

    // 2. App filter : {ResourceTypes : ["SERVICE, PROVISIONER"], appId[], resourceId[]}, 3 RECORDS EXPECTED
    auditPreference.setApplicationAuditFilter(
        ApplicationAuditFilter.builder()
            .resourceIds(null)
            .resourceTypes(Arrays.asList(ResourceType.SERVICE.name(), ResourceType.PROVISIONER.name()))
            .build());
    auditPreference.setOperationTypes(null);
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header4, header5, header6);

    // 3. App filter : {ResourceTypes : ["SERVICE"], appId[app1], resourceId[]}, 2 RECORDS EXPECTED
    auditPreference.setApplicationAuditFilter(ApplicationAuditFilter.builder()
                                                  .resourceIds(null)
                                                  .appIds(Arrays.asList("app1"))
                                                  .resourceTypes(Arrays.asList(ResourceType.SERVICE.name()))
                                                  .build());
    auditPreference.setOperationTypes(null);
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header4, header6);

    // Fetch only CREATE
    auditPreference.setOperationTypes(Arrays.asList(Type.CREATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header6);

    // Fetch only UPDATE
    auditPreference.setOperationTypes(Arrays.asList(Type.UPDATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header4);

    // 4. AccFilterCriteria contains resourceIds. should fetch exact resource ignoring resourceTypes.
    auditPreference.setApplicationAuditFilter(ApplicationAuditFilter.builder()
                                                  .resourceIds(Arrays.asList("e2", "s2"))
                                                  .resourceTypes(Arrays.asList(ResourceType.WORKFLOW.name()))
                                                  .build());
    auditPreference.setOperationTypes(null);
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header6, header7);

    auditPreference.setOperationTypes(Arrays.asList(Type.CREATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header6);

    auditPreference.setOperationTypes(Arrays.asList(Type.UPDATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header7);
  }

  /**
   * TopLevel (accountId, createdAt, operationType) and AppLevel Criteria are given
   * @throws Exception
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void test_AppAndAccountLevelCriteria() throws Exception {
    AuditPreference auditPreference =
        (AuditPreference) auditPreferenceHelper.parseJsonIntoPreference(appAndAccountFilterCriteriaJson);
    assertTopLevelCriteria(auditPreference);
    assertThat(auditPreference.isIncludeAppLevelResources()).isTrue();
    assertApplicationFilter(auditPreference);
    assertAccountLevelFilter(auditPreference);

    // 1. Filter:  All AccountLevel records  and records for app app1.
    auditPreference.setApplicationAuditFilter(ApplicationAuditFilter.builder().appIds(Arrays.asList("app1")).build());
    auditPreference.setAccountAuditFilter(AccountAuditFilter.builder().build());
    auditPreference.setOperationTypes(null);
    PageRequest<AuditHeader> pageRequest = getAuditHeaderPageRequest(auditPreference);
    PageResponse<AuditHeader> pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse())
        .containsExactlyInAnyOrder(header0, header1, header2, header3, header4, header6);

    // 2. Filter:  All AccountLevel records  and records for app app1 "with action CREATE".
    auditPreference.setApplicationAuditFilter(ApplicationAuditFilter.builder().appIds(Arrays.asList("app1")).build());
    auditPreference.setAccountAuditFilter(
        AccountAuditFilter.builder().resourceTypes(Arrays.asList(ResourceType.CLOUD_PROVIDER.name())).build());
    auditPreference.setOperationTypes(null);
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header0, header1, header4, header6);

    // 2. Filter:  All AccountLevel records  and records for app app1 "with action CREATE".
    auditPreference.setApplicationAuditFilter(ApplicationAuditFilter.builder().appIds(Arrays.asList("app1")).build());
    auditPreference.setAccountAuditFilter(
        AccountAuditFilter.builder().resourceTypes(Arrays.asList(ResourceType.CLOUD_PROVIDER.name())).build());
    auditPreference.setOperationTypes(Arrays.asList(Type.CREATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header1, header6);

    // 3. Filter:  All AccountLevel records  and records for app app1 with action CREATE/UPDATE.
    auditPreference.setApplicationAuditFilter(ApplicationAuditFilter.builder().appIds(Arrays.asList("app1")).build());
    auditPreference.setAccountAuditFilter(AccountAuditFilter.builder().build());
    auditPreference.setOperationTypes(Arrays.asList(Type.CREATE.name(), Type.UPDATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header0, header1, header2, header4, header6);

    // 3. Filter:  All AccountLevel records  and records for app app1 with action CREATE/UPDATE.
    auditPreference.setApplicationAuditFilter(ApplicationAuditFilter.builder().appIds(Arrays.asList("app1")).build());
    auditPreference.setAccountAuditFilter(
        AccountAuditFilter.builder().resourceTypes(Arrays.asList(ResourceType.ARTIFACT_SERVER.name())).build());
    auditPreference.setOperationTypes(Arrays.asList(Type.CREATE.name(), Type.UPDATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header2, header4, header6);

    // 4. Filter:  All AccountLevel records  and records for app app1 with action CREATE
    auditPreference.setApplicationAuditFilter(
        ApplicationAuditFilter.builder().appIds(Arrays.asList("app1")).resourceIds(Arrays.asList("e2", "pr1")).build());
    auditPreference.setAccountAuditFilter(
        AccountAuditFilter.builder().resourceIds(Arrays.asList("cp1", "cp2")).build());
    auditPreference.setOperationTypes(Arrays.asList(Type.UPDATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header0, header7);

    auditPreference.setOperationTypes(Arrays.asList(Type.UPDATE.name(), Type.CREATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header0, header1, header7);

    auditPreference.setOperationTypes(Arrays.asList(Type.UPDATE.name(), Type.CREATE.name(), Type.DELETE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header0, header1, header7, header5);

    // 5.
    auditPreference.setApplicationAuditFilter(ApplicationAuditFilter.builder()
                                                  .appIds(Arrays.asList("app2"))
                                                  .resourceTypes(Arrays.asList(ResourceType.PROVISIONER.name()))
                                                  .build());

    auditPreference.setAccountAuditFilter(
        AccountAuditFilter.builder().resourceTypes(Arrays.asList(ResourceType.ARTIFACT_SERVER.name())).build());
    auditPreference.setOperationTypes(Arrays.asList(Type.CREATE.name(), Type.UPDATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header2);

    auditPreference.setOperationTypes(Arrays.asList(Type.DELETE.name(), Type.UPDATE.name()));
    pageRequest = getAuditHeaderPageRequest(auditPreference);
    pageResponse = wingsPersistence.query(AuditHeader.class, pageRequest, excludeAuthority);
    assertThat(pageResponse.getResponse()).containsExactlyInAnyOrder(header2, header5);
  }

  private void assertAccountLevelFilter(AuditPreference auditPreference) {
    assertThat(auditPreference.getAccountAuditFilter()).isNotNull();
    assertThat(auditPreference.getAccountAuditFilter().getResourceTypes()).isNotNull();
    assertThat(auditPreference.getAccountAuditFilter().getResourceIds()).isNotNull();

    assertThat(auditPreference.getAccountAuditFilter().getResourceIds()).hasSize(2);
    assertThat(auditPreference.getAccountAuditFilter().getResourceTypes()).hasSize(2);

    assertThat(auditPreference.getAccountAuditFilter().getResourceIds().containsAll(
                   Arrays.asList("AccresourceId1", "AccresourceId2")))
        .isTrue();
    assertThat(auditPreference.getAccountAuditFilter().getResourceTypes().containsAll(
                   Arrays.asList("AccresourceType1", "AccresourceType2")))
        .isTrue();
  }

  private void assertApplicationFilter(AuditPreference auditPreference) {
    assertThat(auditPreference.getApplicationAuditFilter()).isNotNull();
    assertThat(auditPreference.getApplicationAuditFilter().getAppIds()).isNotNull();
    assertThat(auditPreference.getApplicationAuditFilter().getResourceIds()).isNotNull();
    assertThat(auditPreference.getApplicationAuditFilter().getResourceTypes()).isNotNull();

    assertThat(auditPreference.getApplicationAuditFilter().getAppIds()).hasSize(2);
    assertThat(auditPreference.getApplicationAuditFilter().getResourceIds()).hasSize(2);
    assertThat(auditPreference.getApplicationAuditFilter().getResourceTypes()).hasSize(2);

    assertThat(auditPreference.getApplicationAuditFilter().getAppIds().containsAll(Arrays.asList("AppId1", "AppId2")))
        .isTrue();
    assertThat(auditPreference.getApplicationAuditFilter().getResourceIds().containsAll(
                   Arrays.asList("AppresourceId1", "AppresourceId2")))
        .isTrue();
    assertThat(auditPreference.getApplicationAuditFilter().getResourceTypes().containsAll(
                   Arrays.asList("AppresourceType1", "AppresourceType2")))
        .isTrue();
  }

  private void assertTopLevelCriteria(AuditPreference auditPreference) {
    assertThat(auditPreference).isNotNull();
    assertThat(auditPreference.getPreferenceType()).isEqualTo(PreferenceType.AUDIT_PREFERENCE.name());
    assertThat(auditPreference.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(auditPreference.getStartTime()).isEqualTo("1559076765000");
    assertThat(auditPreference.getEndTime()).isEqualTo("1560195705972");
    assertThat(auditPreference.getOperationTypes()).isNotNull();
    assertThat(auditPreference.getOperationTypes()).hasSize(2);
    assertThat(auditPreference.getOperationTypes().containsAll(Arrays.asList("CREATE", "UPDATE"))).isTrue();
  }

  private PageRequest<AuditHeader> getAuditHeaderPageRequest(AuditPreference auditPreference) {
    PageRequest<AuditHeader> pageRequest = null;
    try {
      pageRequest = auditPreferenceHelper.generatePageRequestFromAuditPreference(auditPreference, "0", "100");
    } catch (Exception e) {
      assertThat(true).isFalse();
    }
    return pageRequest;
  }
}
