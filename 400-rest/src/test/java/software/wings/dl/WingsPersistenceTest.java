/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.dl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.UNKNOWN;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.security.encryption.EncryptionType.KMS;

import static software.wings.security.UserThreadLocal.userGuard;
import static software.wings.settings.SettingVariableTypes.JENKINS;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.beans.EncryptedData;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder;
import io.harness.beans.SortOrder.OrderType;
import io.harness.category.element.UnitTests;
import io.harness.mongo.SampleEntity.SampleEntityKeys;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.Base;
import software.wings.beans.EntityType;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.User;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.KmsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.WingsTestConstants;

import com.google.common.base.MoreObjects;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.AbstractMultivaluedMap;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

/**
 * The Class WingsPersistenceTest.
 */

/**
 * The type Wings persistence test.
 *
 * @author Rishi
 */
@Slf4j
public class WingsPersistenceTest extends WingsBaseTest {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private KmsService kmsService;
  @Inject private SecretManager secretManager;
  @Inject private EncryptionService encryptionService;

  /**
   * Should query by in operator.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldQueryByINOperator() {
    TestEntity entity = new TestEntity();
    entity.setFieldA("fieldA11");
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldA("fieldA12");
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldA("fieldA21");
    wingsPersistence.save(entity);

    PageRequest<TestEntity> req = new PageRequest<>();
    req.addFilter("fieldA", Operator.IN, new Object[] {"fieldA11", "fieldA21"});
    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req, excludeAuthority);
    assertThat(res).isNotNull();
    assertThat(res.size()).isEqualTo(2);
  }

  /**
   * Should query by in operator.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldQueryListByINOperator() {
    TestEntity entity = new TestEntity();
    entity.setFieldList(Lists.newArrayList("fieldList11", "fieldList12", "fieldList13"));
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldList(Lists.newArrayList("fieldList21"));
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldList(Lists.newArrayList("fieldList31", "fieldList32"));
    wingsPersistence.save(entity);

    PageRequest<TestEntity> req = new PageRequest<>();
    req.addFilter("fieldList", Operator.IN, "fieldList11", "fieldList13", "fieldList21");
    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req, excludeAuthority);
    assertThat(res).isNotNull();
    assertThat(res.size()).isEqualTo(2);
  }

  /**
   * Should query by in operator.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldQueryListObjectByINOperator() {
    TestEntityB b11 = new TestEntityB("b11");
    wingsPersistence.save(b11);
    TestEntityB b12 = new TestEntityB("b12");
    wingsPersistence.save(b12);
    TestEntityB b21 = new TestEntityB("b21");
    wingsPersistence.save(b21);
    TestEntityB b22 = new TestEntityB("b22");
    wingsPersistence.save(b22);
    TestEntityB b31 = new TestEntityB("b31");
    wingsPersistence.save(b31);

    TestEntity entity = new TestEntity();
    entity.setTestEntityBList(Lists.newArrayList(b11, b12));
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setTestEntityBList(Lists.newArrayList(b21, b22));
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setTestEntityBList(Lists.newArrayList(b31));
    wingsPersistence.save(entity);

    TestEntityB b111 = new TestEntityB();
    b111.setUuid(b11.getUuid());

    TestEntityB b311 = new TestEntityB();
    b311.setUuid(b31.getUuid());

    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class,
        aPageRequest().addFilter("testEntityBList", Operator.HAS, b111, b311).build(), excludeAuthority);

    assertThat(res).isNotNull();
    assertThat(res.size()).isEqualTo(2);
  }

  /**
   * Should paginate filter sort.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPaginateFilterSort() {
    createEntitiesForPagination();

    PageRequest<TestEntity> req = new PageRequest<>();
    req.setLimit("2");
    req.setOffset("1");
    req.addFilter("fieldA", Operator.CONTAINS, "fieldA1");

    SortOrder order = new SortOrder();
    order.setFieldName("fieldA");
    order.setOrderType(OrderType.DESC);
    req.addOrder(order);

    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req, excludeAuthority);

    assertPaginationResult(res);
  }

  /**
   * Should take query params.
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldTakeQueryParams() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put("search[0][field]", Lists.newArrayList("fieldA"));
    queryParams.put("search[0][op]", Lists.newArrayList("CONTAINS"));
    queryParams.put("search[0][value]", Lists.newArrayList("fieldA1"));

    queryParams.put("sort[0][field]", Lists.newArrayList("fieldA"));
    queryParams.put("sort[0][direction]", Lists.newArrayList("DESC"));

    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    createEntitiesForPagination();

    PageRequest<TestEntity> req = new PageRequest<>();
    req.setLimit("2");
    req.setOffset("1");
    req.setUriInfo(uriInfo);

    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req, excludeAuthority);

    assertPaginationResult(res);
  }

  /**
   * Should take query params in simplified form.
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldTakeQueryParamsInSimplifiedForm() {
    UriInfo uriInfo = mock(UriInfo.class);
    MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
    queryParams.put("fieldA", Lists.newArrayList("fieldA13", "fieldA14"));

    queryParams.put("sort[0][field]", Lists.newArrayList("fieldA"));
    queryParams.put("sort[0][direction]", Lists.newArrayList("DESC"));

    when(uriInfo.getQueryParameters()).thenReturn(queryParams);
    createEntitiesForPagination();

    PageRequest<TestEntity> req = new PageRequest<>();
    req.setUriInfo(uriInfo);

    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req, excludeAuthority);

    assertThat(res).isNotNull().hasSize(2);
  }

  /**
   * Should take query params with in op.
   */
  // Query will look like search[0][value]=fieldA1&search[0][value]=fieldA2
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldTakeQueryParamsWithInOp() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put("search[0][field]", Lists.newArrayList("fieldA"));
    queryParams.put("search[0][op]", Lists.newArrayList("IN"));
    queryParams.put("search[0][value]", Lists.newArrayList("fieldA11", "fieldA12"));

    queryParams.put("sort[0][field]", Lists.newArrayList("fieldA"));
    queryParams.put("sort[0][direction]", Lists.newArrayList("DESC"));

    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    createEntitiesForPagination();

    PageRequest<TestEntity> req = new PageRequest<>();
    req.setUriInfo(uriInfo);

    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req, excludeAuthority);

    assertThat(res).isNotNull().hasSize(2);
    assertThat(res.get(0)).isNotNull();
    assertThat(res.get(0).getFieldA()).isEqualTo("fieldA12");
    assertThat(res.get(1)).isNotNull();
    assertThat(res.get(1).getFieldA()).isEqualTo("fieldA11");
  }

  /**
   * Should work with query with number values.
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldWorkWithQueryWithNumberValues() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put("search[0][field]", Lists.newArrayList(SampleEntityKeys.createdAt));
    queryParams.put("search[0][op]", Lists.newArrayList("GT"));
    queryParams.put("search[0][value]", Lists.newArrayList("10"));

    queryParams.put("sort[0][field]", Lists.newArrayList("fieldA"));
    queryParams.put("sort[0][direction]", Lists.newArrayList("DESC"));

    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    createEntitiesForPagination();

    PageRequest<TestEntity> req = new PageRequest<>();
    req.setLimit("2");
    req.setOffset("1");
    req.setUriInfo(uriInfo);

    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req, excludeAuthority);

    assertThat(res).isNotNull().hasSize(2);
    assertThat(res.getResponse()).extracting(TestEntity::getFieldA).containsExactly("fieldA15", "fieldA14");
  }

  /**
   * Should take query params in simplified form.
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldTakeQueryParamsForReferenceArrays() {
    TestEntityB testEntityB1 = new TestEntityB();
    testEntityB1.setFieldB("fieldB1");
    wingsPersistence.save(testEntityB1);

    TestEntityB testEntityB2 = new TestEntityB();
    testEntityB2.setFieldB("fieldB2");
    wingsPersistence.save(testEntityB2);

    TestEntityB testEntityB3 = new TestEntityB();
    testEntityB3.setFieldB("fieldB3");
    wingsPersistence.save(testEntityB3);

    TestEntityC testEntityC1 = new TestEntityC();
    testEntityC1.setTestEntityBs(asList(testEntityB1, testEntityB2));
    wingsPersistence.save(testEntityC1);

    TestEntityC testEntityC2 = new TestEntityC();
    testEntityC2.setTestEntityBs(asList(testEntityB1, testEntityB3));
    wingsPersistence.save(testEntityC2);

    UriInfo uriInfo = mock(UriInfo.class);
    MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
    queryParams.put("testEntityBs", Lists.newArrayList(testEntityB1.getUuid()));

    when(uriInfo.getQueryParameters()).thenReturn(queryParams);

    PageRequest<TestEntityC> req = new PageRequest<>();
    req.setUriInfo(uriInfo);

    PageResponse<TestEntityC> res = wingsPersistence.query(TestEntityC.class, req, excludeAuthority);

    assertThat(res).isNotNull().hasSize(2);

    queryParams.clear();
    queryParams.put("testEntityBs", Lists.newArrayList(testEntityB2.getUuid()));

    res = wingsPersistence.query(TestEntityC.class, req, excludeAuthority);

    assertThat(res).isNotNull().hasSize(1);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldConvertToQuery() {
    TestEntity entity = new TestEntity();
    entity.setFieldList(Lists.newArrayList("f11", "f12"));
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldList(Lists.newArrayList("f21"));
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldList(Lists.newArrayList("f31"));
    wingsPersistence.save(entity);

    PageRequest<TestEntity> req = new PageRequest<>();
    req.addFilter("fieldList", Operator.IN, "f11", "f12", "f21");
    Query<TestEntity> query = wingsPersistence.convertToQuery(TestEntity.class, req, excludeAuthority);
    assertThat(query).isNotNull();

    List<TestEntity> testEntities = query.asList();
    assertThat(testEntities).isNotNull();
    assertThat(testEntities.size()).isEqualTo(2);
  }

  /**
   * Should update map
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldUpdateMap() {
    TestEntity entity = new TestEntity();
    entity.setFieldA("fieldA11");
    Map<String, String> map = new HashMap<>();
    map.put("abc", "123");
    entity.setMapField(map);
    wingsPersistence.save(entity);

    TestEntity entity1 = wingsPersistence.get(TestEntity.class, entity.getUuid());

    Map<String, String> map2 = new HashMap<>();
    map2.put("abc2", "1234");

    Map fieldMap = new HashMap<>();
    fieldMap.put("mapField", map2);
    wingsPersistence.updateFields(TestEntity.class, entity.getUuid(), fieldMap);

    TestEntity entity2 = wingsPersistence.get(TestEntity.class, entity.getUuid());
    assertThat(entity2).isNotNull();
    assertThat(entity2.getMapField()).isEqualTo(map2);
  }

  /**
   * Should update map entry
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldUpdateMapEntry() {
    TestEntity entity = new TestEntity();
    entity.setFieldA("fieldA11");
    Map<String, String> map = new HashMap<>();
    map.put("abc", "123");
    map.put("def", "123");
    entity.setMapField(map);
    wingsPersistence.save(entity);

    wingsPersistence.get(TestEntity.class, entity.getUuid());

    Query<TestEntity> query = wingsPersistence.createQuery(TestEntity.class).filter(ID_KEY, entity.getUuid());

    UpdateOperations<TestEntity> operations = wingsPersistence.createUpdateOperations(TestEntity.class);
    operations.set("mapField.abc", "1234");
    operations.set("mapField.abc2", "2345");
    operations.unset("mapField.def");

    wingsPersistence.update(query, operations);
    TestEntity entity2 = wingsPersistence.get(TestEntity.class, entity.getUuid());
    assertThat(entity2).isNotNull();
    assertThat(entity2.getMapField())
        .isNotNull()
        .containsEntry("abc", "1234")
        .containsEntry("abc2", "2345")
        .doesNotContainKeys("def");
  }

  /**
   * Should query map
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldQueryMap() {
    TestEntity entity = new TestEntity();
    entity.setFieldA("fieldA11");
    Map<String, String> map = new HashMap<>();
    map.put("abc", "123");
    map.put("ghi", "345");
    entity.setMapField(map);
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldA("fieldA11");
    Map<String, String> map2 = new HashMap<>();
    map2.put("def", "234");
    map2.put("ghi", "345");
    entity.setMapField(map2);
    wingsPersistence.save(entity);

    PageRequest<TestEntity> req =
        aPageRequest()
            .addFilter(null, Operator.OR,
                new Object[] {SearchFilter.builder().fieldName("mapField.abc").op(Operator.EXISTS).build(),
                    SearchFilter.builder().fieldName("mapField.def").op(Operator.EXISTS).build()})
            .build();

    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req, excludeAuthority);
    assertThat(res).isNotNull().doesNotContainNull().hasSize(2).extracting("mapField").contains(map, map2);
  }

  private void assertPaginationResult(PageResponse<TestEntity> res) {
    assertThat(res).isNotNull().hasSize(2);
    assertThat(res.size()).isEqualTo(2);
    assertThat(res.getTotal()).isEqualTo(5);
    assertThat(res.getStart()).isEqualTo(1);
    assertThat(res.get(0)).isNotNull();
    assertThat(res.get(0).getFieldA()).isEqualTo("fieldA14");
    assertThat(res.get(1)).isNotNull();
    assertThat(res.get(1).getFieldA()).isEqualTo("fieldA13");
  }

  private void createEntitiesForPagination() {
    TestEntity entity = new TestEntity();
    entity.setFieldA("fieldA11");
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldA("fieldA12");
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldA("fieldA13");
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldA("fieldA14");
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldA("fieldA15");
    wingsPersistence.save(entity);

    entity = new TestEntity();
    entity.setFieldA("fieldA21");
    wingsPersistence.save(entity);
  }

  /**
   * Should query Count only
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldQueryCountOnly() {
    createEntitiesForPagination();

    PageRequest<TestEntity> req = new PageRequest<>();
    req.setLimit("2");
    req.setOffset("1");
    req.addFilter("fieldA", Operator.CONTAINS, "fieldA1");
    req.addOrder("fieldA", OrderType.DESC);

    req.setOptions(asList(PageRequest.Option.COUNT));
    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req);
    assertThat(res).isNotNull();
    assertThat(res.getTotal()).isNotNull().isEqualTo(5);
    assertThat(res.getResponse()).isNullOrEmpty();
  }

  /**
   * Should query Count only
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldQueryListOnly() {
    createEntitiesForPagination();

    PageRequest<TestEntity> req = new PageRequest<>();
    req.addFilter("fieldA", Operator.CONTAINS, "fieldA1");

    SortOrder order = new SortOrder();
    order.setFieldName("fieldA");
    order.setOrderType(OrderType.DESC);
    req.addOrder(order);

    req.setOptions(asList(PageRequest.Option.LIST));
    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req, excludeAuthority);
    assertThat(res).isNotNull();
    assertThat(res.getTotal()).isNull();
    assertThat(res.getResponse()).isNotNull().hasSize(5);
  }

  /**
   * Should query Count only
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldQueryCountAndList() {
    createEntitiesForPagination();

    PageRequest<TestEntity> req = new PageRequest<>();
    req.addFilter("fieldA", Operator.CONTAINS, "fieldA1");
    req.addOrder("fieldA", OrderType.DESC);

    req.setOptions(asList(PageRequest.Option.LIST, PageRequest.Option.COUNT));
    PageResponse<TestEntity> res = wingsPersistence.query(TestEntity.class, req, excludeAuthority);
    assertThat(res).isNotNull();
    assertThat(res.getTotal()).isNotNull().isEqualTo(5);
    assertThat(res.getResponse()).isNotNull().hasSize(5);
  }

  /**
   * Should save referenced object.
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldSaveReferencedObject() {
    TestEntityB entityB = new TestEntityB();
    entityB.setFieldB("fieldB1");
    wingsPersistence.save(entityB);

    log.debug("Done with TestEntityB save");
    TestEntity entity = new TestEntity();
    entity.setFieldA("fieldA1");
    entity.setTestEntityB(entityB);
    wingsPersistence.save(entity);
    log.debug("Done with TestInternalEntity save");
    assertThat(entity)
        .isNotNull()
        .hasFieldOrPropertyWithValue("fieldA", "fieldA1")
        .hasFieldOrPropertyWithValue("testEntityB", entityB);

    entity = wingsPersistence.get(TestEntity.class, entity.getUuid());
    assertThat(entity)
        .isNotNull()
        .hasFieldOrPropertyWithValue("fieldA", "fieldA1")
        .hasFieldOrPropertyWithValue("testEntityB", entityB);
    log.debug("Done with TestInternalEntity get");
  }

  /**
   * A couple of tests to check on encrypted behavior.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldStoreAndRetrieveEncryptedPassword() throws IOException {
    try (UserThreadLocal.Guard guard = userGuard(null)) {
      String rand = String.valueOf(Math.random());
      String password = "06b13aea6f5f13ec69577689a899bbaad69eeb2f";
      User user = User.Builder.anUser().uuid("uuid").email("admin@harness.io").name("admin").build();
      UserThreadLocal.userGuard(user);
      JenkinsConfig jenkinsConfig = JenkinsConfig.builder()
                                        .jenkinsUrl("https://jenkins.wings.software")
                                        .accountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                                        .username("wingsbuild")
                                        .authMechanism(JenkinsConfig.USERNAME_DEFAULT_TEXT)
                                        .password(password.toCharArray())
                                        .build();
      SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                              .withAccountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                                              .withCategory(SettingCategory.CONNECTOR)
                                              .withName("Jenkins Config" + rand)
                                              .withValue(jenkinsConfig)
                                              .build();
      wingsPersistence.save(settingAttribute);
      SettingAttribute result = wingsPersistence.get(SettingAttribute.class, settingAttribute.getUuid());
      assertThat(result).isNotNull().usingRecursiveComparison().isEqualTo(settingAttribute);
      SettingAttribute undecryptedResult = wingsPersistence.get(SettingAttribute.class, settingAttribute.getUuid());
      assertThat(undecryptedResult).isNotNull();
      assertThat(Arrays.equals(password.toCharArray(), ((JenkinsConfig) undecryptedResult.getValue()).getPassword()))
          .isFalse();

      // decrypt and compare
      encryptionService.decrypt((EncryptableSetting) result.getValue(),
          secretManager.getEncryptionDetails((EncryptableSetting) result.getValue(), null, null), false);
      assertThat(new String(((JenkinsConfig) result.getValue()).getPassword())).isEqualTo(password);

      wingsPersistence.delete(settingAttribute);
      result = wingsPersistence.get(SettingAttribute.class, settingAttribute.getUuid());
      assertThat(result).isNull();
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldUpdateEncryptedPassword() {
    String rand = String.valueOf(Math.random());
    String originalPassword = "06b13aea6f5f13ec69577689a899bbaad69eeb2f";
    JenkinsConfig jenkinsConfig = JenkinsConfig.builder()
                                      .jenkinsUrl("https://jenkins.wings.software")
                                      .accountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                                      .username("wingsbuild")
                                      .password(originalPassword.toCharArray())
                                      .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                                            .withCategory(SettingCategory.CONNECTOR)
                                            .withName("Jenkins Config" + rand)
                                            .withValue(jenkinsConfig)
                                            .build();
    String settingId = wingsPersistence.save(settingAttribute);
    char[] newPassword = "newPass".toCharArray();
    jenkinsConfig.setPassword(newPassword);
    settingAttribute.setValue(jenkinsConfig);
    wingsPersistence.updateField(SettingAttribute.class, settingId, "value", jenkinsConfig);
    SettingAttribute result = wingsPersistence.get(SettingAttribute.class, settingId);
    char[] password = ((JenkinsConfig) result.getValue()).getPassword();
    assertThat(Arrays.equals(newPassword, password)).isFalse();
    SettingAttribute undecryptedResult = wingsPersistence.get(SettingAttribute.class, settingAttribute.getUuid());
    assertThat(undecryptedResult).isNotNull();
    assertThat(Arrays.equals(newPassword, ((JenkinsConfig) undecryptedResult.getValue()).getPassword())).isFalse();

    encryptionService.decrypt((EncryptableSetting) result.getValue(),
        secretManager.getEncryptionDetails((EncryptableSetting) result.getValue(), null, null), false);
    assertThat(Arrays.equals(newPassword, ((JenkinsConfig) result.getValue()).getPassword())).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldStoreAndRetrieveEncryptedConfigValue() {
    String rand = String.valueOf(Math.random());
    char[] password = "bar".toCharArray();
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .accountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                                          .templateId(TEMPLATE_ID)
                                          .envId(ENV_ID)
                                          .entityType(EntityType.SERVICE)
                                          .entityId("0Or07BsmSBiF0sOZY80HRg")
                                          .name("foo" + rand)
                                          .value(password)
                                          .type(ServiceVariableType.ENCRYPTED_TEXT)
                                          .build();
    serviceVariable.setAppId("myapp");

    String serviceVariableId = wingsPersistence.save(serviceVariable);
    ServiceVariable result = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
    assertThat(password).isEqualTo(result.getEncryptedValue().toCharArray());
    assertThat(result.getValue()).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void shouldStoreAndUpdateEncryptedConfigValue() {
    String rand = String.valueOf(Math.random());
    String secretId = wingsPersistence.save(EncryptedData.builder()
                                                .encryptionType(KMS)
                                                .encryptionKey("key")
                                                .encryptedValue("value".toCharArray())
                                                .kmsId("kmsId")
                                                .name("name")
                                                .type(JENKINS)
                                                .accountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                                                .build());
    char[] password = "amazonkms:".concat(secretId).toCharArray();
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .accountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                                          .templateId(TEMPLATE_ID)
                                          .envId(ENV_ID)
                                          .entityType(EntityType.SERVICE)
                                          .entityId("0Or07BsmSBiF0sOZY80HRg")
                                          .name("foo" + rand)
                                          .value(password)
                                          .type(ServiceVariableType.ENCRYPTED_TEXT)
                                          .build();
    serviceVariable.setAppId("myapp");

    String serviceVariableId = wingsPersistence.save(serviceVariable);
    ServiceVariable result = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
    assertThat(secretId).isEqualTo(result.getEncryptedValue());
    assertThat(result.getValue()).isNull();

    char[] newPassword = "foo".toCharArray();
    Map<String, Object> updateMap = new HashMap<>();
    updateMap.put(ServiceVariableKeys.value, newPassword);
    updateMap.put(ServiceVariableKeys.type, result.getType());

    wingsPersistence.updateFields(ServiceVariable.class, serviceVariableId, updateMap);

    result = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
    assertThat(newPassword).isEqualTo(result.getEncryptedValue().toCharArray());
    assertThat(result.getValue()).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void shouldStoreAndUpdateEncryptedConfigValueUsingSave() {
    String rand = String.valueOf(Math.random());
    char[] password = "bar".toCharArray();
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .accountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                                          .templateId(TEMPLATE_ID)
                                          .envId(ENV_ID)
                                          .entityType(EntityType.SERVICE)
                                          .entityId("0Or07BsmSBiF0sOZY80HRg")
                                          .name("foo" + rand)
                                          .value(password)
                                          .type(ServiceVariableType.ENCRYPTED_TEXT)
                                          .build();
    serviceVariable.setAppId("myapp");

    String serviceVariableId = wingsPersistence.save(serviceVariable);
    ServiceVariable result = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
    assertThat(password).isEqualTo(result.getEncryptedValue().toCharArray());
    assertThat(result.getValue()).isNull();

    char[] newPassword = "foo".toCharArray();
    serviceVariable.setValue(newPassword);
    wingsPersistence.save(serviceVariable);
    result = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
    assertThat(newPassword).isEqualTo(result.getEncryptedValue().toCharArray());
    assertThat(result.getValue()).isNull();

    char[] updatedPassword = "joe".toCharArray();
    serviceVariable.setValue(updatedPassword);
    serviceVariable.setName("abc");
    serviceVariable.setUuid("uuid");
    wingsPersistence.saveIgnoringDuplicateKeys(Collections.singletonList(serviceVariable));
    result = wingsPersistence.get(ServiceVariable.class, "uuid");
    log.info(result.getName());
    assertThat(updatedPassword).isEqualTo(result.getEncryptedValue().toCharArray());
    assertThat(result.getValue()).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void shouldStoreAndRemoveEncryptedConfigValue() {
    String rand = String.valueOf(Math.random());
    char[] password = "bar".toCharArray();
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .accountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                                          .templateId(TEMPLATE_ID)
                                          .envId(ENV_ID)
                                          .entityType(EntityType.SERVICE)
                                          .entityId("0Or07BsmSBiF0sOZY80HRg")
                                          .name("foo" + rand)
                                          .value(password)
                                          .type(ServiceVariableType.ENCRYPTED_TEXT)
                                          .build();
    serviceVariable.setAppId("myapp");

    String serviceVariableId = wingsPersistence.save(serviceVariable);
    ServiceVariable result = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
    assertThat(password).isEqualTo(result.getEncryptedValue().toCharArray());
    assertThat(result.getValue()).isNull();

    Set<String> fieldsToRemove = new HashSet<>();
    fieldsToRemove.add(ServiceVariableKeys.value);
    wingsPersistence.updateFields(ServiceVariable.class, serviceVariableId, new HashMap<>(), fieldsToRemove);

    result = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
    assertThat(result.getEncryptedValue()).isNull();
    assertThat(result.getValue()).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldStoreAndRetrieveUnencryptedConfigValue() {
    String rand = String.valueOf(Math.random());
    char[] password = "bar".toCharArray();
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .accountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                                          .templateId(TEMPLATE_ID)
                                          .envId(ENV_ID)
                                          .entityType(EntityType.SERVICE)
                                          .entityId("0Or07BsmSBiF0sOZY80HRg")
                                          .name("foo" + rand)
                                          .value(password)
                                          .type(ServiceVariableType.TEXT)
                                          .build();
    serviceVariable.setAppId("myapp");

    String serviceVariableId = wingsPersistence.save(serviceVariable);
    ServiceVariable result = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
    assertThat(password).isEqualTo(result.getValue());
    ServiceVariable undecryptedResult = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
    assertThat(undecryptedResult).isNotNull();
    assertThat(password).isEqualTo(undecryptedResult.getValue());
  }
  /**
   * The Class TestInternalEntity.
   */
  public static class TestEntity extends Base {
    /**
     * The Test entity b.
     */
    @Reference TestEntityB testEntityB;
    private String fieldA;
    private List<String> fieldList;
    private Map<String, String> mapField;
    @Reference List<TestEntityB> testEntityBList;
    /**
     * Gets field a.
     *
     * @return the field a
     */
    public String getFieldA() {
      return fieldA;
    }

    /**
     * Sets field a.
     *
     * @param fieldA the field a
     */
    public void setFieldA(String fieldA) {
      this.fieldA = fieldA;
    }

    public List<String> getFieldList() {
      return fieldList;
    }

    public void setFieldList(List<String> fieldList) {
      this.fieldList = fieldList;
    }

    /**
     * Gets map field.
     *
     * @return the map field
     */
    public Map<String, String> getMapField() {
      return mapField;
    }

    /**
     * Sets map field.
     *
     * @param mapField the map field
     */
    public void setMapField(Map<String, String> mapField) {
      this.mapField = mapField;
    }

    /**
     * Gets test entity b.
     *
     * @return the test entity b
     */
    public TestEntityB getTestEntityB() {
      return testEntityB;
    }

    /**
     * Sets test entity b.
     *
     * @param testEntityB the test entity b
     */
    public void setTestEntityB(TestEntityB testEntityB) {
      this.testEntityB = testEntityB;
    }

    public List<TestEntityB> getTestEntityBList() {
      return testEntityBList;
    }

    public void setTestEntityBList(List<TestEntityB> testEntityBList) {
      this.testEntityBList = testEntityBList;
    }

    @Override
    public String toString() {
      return "TestInternalEntity{"
          + "testEntityB=" + testEntityB + ", fieldA='" + fieldA + '\'' + ", fieldList=" + fieldList
          + ", mapField=" + mapField + ", testEntityBList=" + testEntityBList + '}';
    }
  }

  /**
   * The Class TestInternalEntity.
   */
  public static class TestEntityB extends Base {
    private String fieldB;

    public TestEntityB() {}

    public TestEntityB(String fieldB) {
      this.fieldB = fieldB;
      setUuid(generateUuid());
    }

    /**
     * Gets field b.
     *
     * @return the field b
     */
    public String getFieldB() {
      return fieldB;
    }

    /**
     * Sets field b.
     *
     * @param fieldB the field b
     */
    public void setFieldB(String fieldB) {
      this.fieldB = fieldB;
    }

    @Override
    public String toString() {
      return "TestEntityB [fieldB=" + fieldB + "]";
    }
  }

  /**
   * The Class TestInternalEntity.
   */
  public static class TestEntityC extends Base {
    @Reference(idOnly = true) private List<TestEntityB> testEntityBs;

    /**
     * Gets test entity bs.
     *
     * @return the test entity bs
     */
    public List<TestEntityB> getTestEntityBs() {
      return testEntityBs;
    }

    /**
     * Sets test entity bs.
     *
     * @param testEntityBs the test entity bs
     */
    public void setTestEntityBs(List<TestEntityB> testEntityBs) {
      this.testEntityBs = testEntityBs;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("testEntityBs", testEntityBs).toString();
    }
  }
}
