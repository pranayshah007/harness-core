/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.AKRITI;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EmbeddedUser;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ff.FeatureFlagService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.audit.AuditHeader;
import software.wings.audit.EntityAuditRecord;
import software.wings.beans.AuditPreference;
import software.wings.beans.EntityType;
import software.wings.beans.EntityYamlRecord;
import software.wings.beans.Event;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.yaml.YamlResourceService;
import software.wings.yaml.YamlPayload;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AuditServiceImplTest extends WingsBaseTest {
  @Mock private EntityHelper mockEntityHelper;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private YamlResourceService mockYamlResourceService;
  @Mock private WingsPersistence mockWingsPersistence;
  @Mock private EntityMetadataHelper entityMetadataHelper;
  @Inject @InjectMocks protected AuditServiceImpl auditServiceImpl;
  @Inject private AuditPreferenceHelper auditPreferenceHelper;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testSaveEntityYamlForAudit() {
    RestResponse mockRestResponse = mock(RestResponse.class);
    doReturn(mockRestResponse).when(mockYamlResourceService).obtainEntityYamlVersion(anyString(), any());
    YamlPayload mockResource = mock(YamlPayload.class);
    doReturn(mockResource).when(mockRestResponse).getResource();
    final String YAML_CONTENT = "YamlContent";
    doReturn(YAML_CONTENT).when(mockResource).getYaml();
    doReturn("YamlPath").when(mockEntityHelper).getFullYamlPathForEntity(any(), any());
    auditServiceImpl.saveEntityYamlForAudit(anApplication().build(),
        EntityAuditRecord.builder().appId(APP_ID).entityId(APP_ID).entityType(EntityType.APPLICATION.name()).build(),
        ACCOUNT_ID);
    ArgumentCaptor<EntityYamlRecord> captor = ArgumentCaptor.forClass(EntityYamlRecord.class);
    verify(mockWingsPersistence).save(captor.capture());
    EntityYamlRecord record = captor.getValue();
    assertThat(record).isNotNull();
    assertThat(record.getEntityType()).isEqualTo(EntityType.APPLICATION.name());
    assertThat(record.getYamlContent()).isEqualTo(YAML_CONTENT);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testSaveEntityYamlForUserGroupAudit() {
    RestResponse mockRestResponseUserGroup = mock(RestResponse.class);
    doReturn(mockRestResponseUserGroup).when(mockYamlResourceService).obtainEntityYamlVersion(anyString(), any());
    YamlPayload mockResourceUserGroup = mock(YamlPayload.class);
    doReturn(mockResourceUserGroup).when(mockRestResponseUserGroup).getResource();

    final String YAML_CONTENT = "YamlContent";
    final String USER_GROUP_NAME = "user_group";

    doReturn(YAML_CONTENT).when(mockResourceUserGroup).getYaml();
    doReturn(USER_GROUP_NAME + ".yaml").when(mockEntityHelper).getFullYamlPathForEntity(any(), any());
    auditServiceImpl.saveEntityYamlForAudit(UserGroup.builder().name(USER_GROUP_NAME).accountId(ACCOUNT_ID).build(),
        EntityAuditRecord.builder().appId(APP_ID).entityId(APP_ID).entityType(EntityType.USER_GROUP.name()).build(),
        ACCOUNT_ID);
    ArgumentCaptor<EntityYamlRecord> captor = ArgumentCaptor.forClass(EntityYamlRecord.class);
    verify(mockWingsPersistence).save(captor.capture());
    EntityYamlRecord record = captor.getValue();
    assertThat(record).isNotNull();
    assertThat(record.getEntityType()).isEqualTo(EntityType.USER_GROUP.name());
    assertThat(record.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(record.getYamlPath()).isEqualTo(USER_GROUP_NAME + ".yaml");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetLatestYamlRecordIdForEntity() {
    String yamlPath = "YAML_PATH";
    String id = "ID";
    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(mockWingsPersistence).createQuery(any());
    doReturn(mockQuery).when(mockQuery).filter(anyString(), any());
    doReturn(mockQuery).when(mockQuery).project(anyString(), anyBoolean());
    doReturn(mockQuery).when(mockQuery).order(any(Sort.class));
    EntityYamlRecord entityYamlRecord = EntityYamlRecord.builder().yamlPath(yamlPath).uuid(id).build();
    doReturn(entityYamlRecord).when(mockQuery).get();
    EntityAuditRecord entityAuditRecord = EntityAuditRecord.builder().build();
    auditServiceImpl.loadLatestYamlDetailsForEntity(entityAuditRecord, ACCOUNT_ID);
    assertThat(entityAuditRecord.getEntityOldYamlRecordId()).isEqualTo(id);
    assertThat(entityAuditRecord.getYamlPath()).isEqualTo(yamlPath);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testIsNonYamlEntity() {
    MockitoAnnotations.initMocks(this);
    checkNonYamlEntityForTemplate();
  }

  private void checkNonYamlEntityForTemplate() {
    assertThat(auditServiceImpl.isNonYamlEntity(EntityAuditRecord.builder()
                                                    .entityType(EntityType.TEMPLATE_FOLDER.name())
                                                    .affectedResourceType(EntityType.TEMPLATE_FOLDER.name())
                                                    .build()))
        .isEqualTo(true);
    assertThat(auditServiceImpl.isNonYamlEntity(EntityAuditRecord.builder()
                                                    .entityType(EntityType.TEMPLATE.name())
                                                    .affectedResourceType(EntityType.TEMPLATE.name())
                                                    .build()))
        .isEqualTo(false);
  }
  @Test
  @Owner(developers = AKRITI)
  @Category(UnitTests.class)
  public void checkLoginFeatureFlagEnabled() {
    String mockJsonFilter =
        "{\"preferenceType\":\"AUDIT_PREFERENCE\",\"offset\":0,\"lastNDays\":7,\"startTime\":1613028205502,\"endTime\":1613633005502,\"includeAccountLevelResources\":true,\"includeAppLevelResources\":true}";
    AuditPreference auditPreference = (AuditPreference) auditPreferenceHelper.parseJsonIntoPreference(mockJsonFilter);
    auditPreference.setAccountId(ACCOUNT_ID);
    when(mockFeatureFlagService.isEnabled(FeatureName.ENABLE_LOGIN_AUDITS, ACCOUNT_ID)).thenReturn(true);
    auditServiceImpl.changeAuditPreferenceForHomePage(auditPreference, ACCOUNT_ID);
    assertThat(auditPreference.getOperationTypes().contains("LOGIN")).isEqualTo(true);
    assertThat(auditPreference.getOperationTypes().contains("LOGIN_2FA")).isEqualTo(true);
  }

  @Test
  @Owner(developers = AKRITI)
  @Category(UnitTests.class)
  public void checkLoginFeatureFlagDisabled() {
    String mockJsonFilter =
        "{\"preferenceType\":\"AUDIT_PREFERENCE\",\"offset\":0,\"lastNDays\":7,\"startTime\":1613028205502,\"endTime\":1613633005502,\"includeAccountLevelResources\":true,\"includeAppLevelResources\":true}";
    AuditPreference auditPreference = (AuditPreference) auditPreferenceHelper.parseJsonIntoPreference(mockJsonFilter);
    auditPreference.setAccountId(ACCOUNT_ID);

    when(mockFeatureFlagService.isEnabled(FeatureName.ENABLE_LOGIN_AUDITS, ACCOUNT_ID)).thenReturn(false);
    auditServiceImpl.changeAuditPreferenceForHomePage(auditPreference, ACCOUNT_ID);
    assertThat(auditPreference.getOperationTypes().contains("LOGIN")).isEqualTo(false);
    assertThat(auditPreference.getOperationTypes().contains("LOGIN_2FA")).isEqualTo(false);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldAddDetailsIgnoreWhenUserHasApiNameAndEmptyUuid() {
    final String headerId = UUIDGenerator.generateUuid();
    final AuditHeader header = new AuditHeader();
    header.setCreatedBy(EmbeddedUser.builder().name("API").uuid(null).build());

    Query<AuditHeader> query = Mockito.mock(Query.class);
    when(mockWingsPersistence.createQuery(AuditHeader.class)).thenReturn(query);
    when(query.filter(AuditHeader.ID_KEY2, headerId)).thenReturn(query);
    when(query.get()).thenReturn(header);

    final Object entity = new Object();
    auditServiceImpl.addDetails(ACCOUNT_ID, entity, headerId, Event.Type.UPDATE);

    verify(entityMetadataHelper, never()).addUserDetails(eq(ACCOUNT_ID), any(), any());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldAddDetailsWhenUserNotApiNoMatterUuid() {
    final String headerId = UUIDGenerator.generateUuid();
    final AuditHeader header = new AuditHeader();
    header.setCreatedBy(EmbeddedUser.builder().name("Myself").uuid(null).build());

    Query<AuditHeader> query = Mockito.mock(Query.class);
    when(mockWingsPersistence.createQuery(AuditHeader.class)).thenReturn(query);
    when(query.filter(AuditHeader.ID_KEY2, headerId)).thenReturn(query);
    when(query.get()).thenReturn(header);

    final Object entity = new Object();
    auditServiceImpl.addDetails(ACCOUNT_ID, entity, headerId, Event.Type.UPDATE);

    verify(entityMetadataHelper).addUserDetails(eq(ACCOUNT_ID), any(), eq(header));
  }
}
