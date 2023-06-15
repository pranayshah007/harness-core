/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.PUNEET;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.WORKFLOW;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_TAGS;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.EntityType;
import software.wings.beans.HarnessTag;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.ResourceLookup;
import software.wings.resources.HarnessTagResource;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.EntityNameCache;
import software.wings.service.impl.HarnessTagServiceImpl;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.utils.WingsTestConstants;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDC)
public class HarnessTagServiceTest extends WingsBaseTest {
  private static final String TEST_ACCOUNT_ID = "TEST_ACCOUNT_ID";

  @Mock private MainConfiguration mainConfiguration;
  @Mock private ResourceLookupService resourceLookupService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock EntityNameCache entityNameCache;

  @Inject @InjectMocks @Spy private HarnessTagServiceImpl harnessTagService;

  @Inject private HPersistence persistence;
  @Inject private AccountService accountService;

  private String colorTagKey = "color";
  private HarnessTag colorTag = HarnessTag.builder().accountId(TEST_ACCOUNT_ID).key(colorTagKey).build();
  private static final String EXPR_FAIL_MESSAGE =
      "Only workflow variables, app defaults and account defaults can be added as expressions";

  @Before
  public void setUp() throws Exception {
    when(resourceLookupService.getWithResourceId(TEST_ACCOUNT_ID, "id")).thenReturn(getResourceLookupWithId("id"));

    Account account = anAccount()
                          .withUuid(TEST_ACCOUNT_ID)
                          .withAccountName(WingsTestConstants.ACCOUNT_NAME)
                          .withCompanyName(WingsTestConstants.COMPANY_NAME)
                          .withLicenseInfo(getLicenseInfo())
                          .build();
    accountService.save(account, false);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void smokeTest() {
    harnessTagService.create(colorTag);
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag).isNotNull();
    assertThat(savedTag.getUuid()).isNotEmpty();
    assertThat(savedTag).hasFieldOrPropertyWithValue("key", colorTagKey);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void invalidKeyTest() {
    try {
      harnessTagService.create(HarnessTag.builder().accountId(TEST_ACCOUNT_ID).key(" ").build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Tag name cannot be blank");
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void invalidKeyLengthTest() {
    try {
      harnessTagService.create(
          HarnessTag.builder()
              .accountId(TEST_ACCOUNT_ID)
              .key(
                  "aaaaaaaaaaaaaaaaadasdsda12342453sadfasaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaasdfasfasfaaaaaaaaaaaaaaaaa")
              .build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Max allowed size for tag name is 128");
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void invalidAllowedValueTest() {
    try {
      HashSet<String> allowedValues = new HashSet<>();
      allowedValues.add(null);
      harnessTagService.create(
          HarnessTag.builder().accountId(TEST_ACCOUNT_ID).key("test").allowedValues(allowedValues).build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Tag value cannot be null");
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void invalidAllowedValuesLengthTest() {
    try {
      HashSet<String> allowedValues = new HashSet<>();
      allowedValues.add(
          "aaaaaaaaaaaaaaaaadasdsda12342453sadfasaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaasdfasfasfaaaaaaaaaaaaaaaaa"
          + "aaaaaaaaaaaaaaaaadasdsda12342453sadfasaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaasdfasfasfaaaaaaaaaaaaaaaaa");

      harnessTagService.create(
          HarnessTag.builder().accountId(TEST_ACCOUNT_ID).key("test").allowedValues(allowedValues).build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Max allowed size for tag value is 256");
    }
  }

  @Test
  @Owner(developers = PUNEET)

  @Category(UnitTests.class)
  public void updateTest() {
    harnessTagService.create(colorTag);
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    HashSet<String> allowedValues = new HashSet<>();
    allowedValues.add("red");
    allowedValues.add("green");
    allowedValues.add("blue");
    savedTag.setAllowedValues(allowedValues);
    harnessTagService.update(savedTag);
    HarnessTag savedTag1 = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag1.getAllowedValues()).isEqualTo(allowedValues);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void deleteTagTest() {
    harnessTagService.create(colorTag);
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag).isNotNull();
    harnessTagService.delete(colorTag);
    HarnessTag savedTag1 = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag1).isNull();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void attachTagSmokeTest() {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(SERVICE)
                                    .key(colorTagKey)
                                    .value("red")
                                    .build());
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag).isNotNull();
    PageRequest<HarnessTagLink> request = new PageRequest<>();
    request.addFilter("accountId", EQ, TEST_ACCOUNT_ID);
    request.addFilter("key", EQ, colorTagKey);
    request.addFilter("value", EQ, "red");
    PageResponse<HarnessTagLink> resources = harnessTagService.listResourcesWithTag(TEST_ACCOUNT_ID, request);

    assertThat(resources).isNotNull();
    assertThat(resources.getResponse()).hasSize(1);
    assertThat(resources.getResponse().get(0).getKey()).isEqualTo(colorTagKey);
    assertThat(resources.getResponse().get(0).getValue()).isEqualTo("red");
    assertThat(resources.getResponse().get(0).getEntityType()).isEqualTo(SERVICE);
    assertThat(resources.getResponse().get(0).getEntityId()).isEqualTo("id");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void updateTagValueTest() {
    HarnessTagLink tagLink = HarnessTagLink.builder()
                                 .accountId(TEST_ACCOUNT_ID)
                                 .appId(APP_ID)
                                 .entityId("id")
                                 .entityType(SERVICE)
                                 .key(colorTagKey)
                                 .value("red")
                                 .build();

    harnessTagService.attachTag(tagLink);

    PageRequest<HarnessTagLink> requestColorRed = new PageRequest<>();
    requestColorRed.addFilter("accountId", EQ, TEST_ACCOUNT_ID);
    requestColorRed.addFilter("key", EQ, colorTagKey);
    requestColorRed.addFilter("value", EQ, "red");
    PageResponse<HarnessTagLink> resources = harnessTagService.listResourcesWithTag(TEST_ACCOUNT_ID, requestColorRed);
    assertThat(resources).isNotNull();
    assertThat(resources.getResponse()).hasSize(1);
    assertThat(resources.getResponse().get(0).getKey()).isEqualTo(colorTagKey);
    assertThat(resources.getResponse().get(0).getValue()).isEqualTo("red");
    assertThat(resources.getResponse().get(0).getEntityType()).isEqualTo(SERVICE);
    assertThat(resources.getResponse().get(0).getEntityId()).isEqualTo("id");

    tagLink.setValue("blue");
    harnessTagService.attachTag(tagLink);

    resources = harnessTagService.listResourcesWithTag(TEST_ACCOUNT_ID, requestColorRed);
    assertThat(resources.getResponse()).isEmpty();

    PageRequest<HarnessTagLink> requestColorBlue = new PageRequest<>();
    requestColorBlue.addFilter("accountId", EQ, TEST_ACCOUNT_ID);
    requestColorBlue.addFilter("key", EQ, colorTagKey);
    requestColorRed.addFilter("value", EQ, "blue");
    resources = harnessTagService.listResourcesWithTag(TEST_ACCOUNT_ID, requestColorBlue);

    assertThat(resources).isNotNull();
    assertThat(resources.getResponse()).hasSize(1);
    assertThat(resources.getResponse().get(0).getKey()).isEqualTo(colorTagKey);
    assertThat(resources.getResponse().get(0).getValue()).isEqualTo("blue");
    assertThat(resources.getResponse().get(0).getEntityType()).isEqualTo(SERVICE);
    assertThat(resources.getResponse().get(0).getEntityId()).isEqualTo("id");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void tryToDeleteInUseTagTest() {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(SERVICE)
                                    .key(colorTagKey)
                                    .value("red")
                                    .build());
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag).isNotNull();
    PageRequest<HarnessTagLink> request = new PageRequest<>();
    request.addFilter("accountId", EQ, TEST_ACCOUNT_ID);
    request.addFilter("key", EQ, colorTagKey);
    PageResponse<HarnessTagLink> resources = harnessTagService.listResourcesWithTag(TEST_ACCOUNT_ID, request);

    try {
      harnessTagService.delete(colorTag);
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Tag is in use. Cannot delete");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void tryToDeleteInUseAllowedValueTest() {
    colorTag.setAllowedValues(Sets.newHashSet("red"));
    harnessTagService.create(colorTag);
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(SERVICE)
                                    .key(colorTagKey)
                                    .value("red")
                                    .build());
    colorTag.setAllowedValues(Collections.emptySet());

    try {
      harnessTagService.update(colorTag);
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo(
              "Allowed values must contain all in used values. Value [red] is missing in current allowed values list");
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void getInUseValuesTest() {
    when(resourceLookupService.getWithResourceId(TEST_ACCOUNT_ID, "id1")).thenReturn(getResourceLookupWithId("id1"));
    when(resourceLookupService.getWithResourceId(TEST_ACCOUNT_ID, "id2")).thenReturn(getResourceLookupWithId("id2"));

    HarnessTagLink tagLinkRed = HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id1")
                                    .entityType(SERVICE)
                                    .key(colorTagKey)
                                    .value("red")
                                    .build();

    harnessTagService.attachTag(tagLinkRed);

    HarnessTagLink tagLinkBlue = HarnessTagLink.builder()
                                     .accountId(TEST_ACCOUNT_ID)
                                     .appId(APP_ID)
                                     .entityId("id2")
                                     .entityType(SERVICE)
                                     .key(colorTagKey)
                                     .value("blue")
                                     .build();

    harnessTagService.attachTag(tagLinkBlue);

    HarnessTag tag = harnessTagService.getTagWithInUseValues(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(tag.getKey()).isEqualTo(colorTagKey);
    assertThat(tag.getInUseValues()).contains("red");
    assertThat(tag.getInUseValues()).containsAll(ImmutableSet.of("red", "blue"));
  }

  private ResourceLookup getResourceLookupWithId(String resourceId) {
    return ResourceLookup.builder()
        .appId(APP_ID)
        .resourceType(EntityType.SERVICE.name())
        .resourceId(resourceId)
        .build();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testAttachTagWithEmptyValue() {
    HarnessTagLink tagLink = HarnessTagLink.builder()
                                 .accountId(TEST_ACCOUNT_ID)
                                 .appId(APP_ID)
                                 .entityId("id")
                                 .entityType(SERVICE)
                                 .key(colorTagKey)
                                 .value("")
                                 .build();

    harnessTagService.attachTag(tagLink);
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag).isNotNull();
    PageRequest<HarnessTagLink> request = new PageRequest<>();
    request.addFilter("accountId", EQ, TEST_ACCOUNT_ID);
    request.addFilter("key", EQ, colorTagKey);
    request.addFilter("value", EQ, "");
    PageResponse<HarnessTagLink> resources = harnessTagService.listResourcesWithTag(TEST_ACCOUNT_ID, request);

    assertThat(resources).isNotNull();
    assertThat(resources.getResponse()).hasSize(1);
    assertThat(resources.getResponse().get(0).getKey()).isEqualTo(colorTagKey);
    assertThat(resources.getResponse().get(0).getValue()).isEqualTo("");
    assertThat(resources.getResponse().get(0).getEntityType()).isEqualTo(SERVICE);
    assertThat(resources.getResponse().get(0).getEntityId()).isEqualTo("id");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testAttachTagWithNullValue() {
    HarnessTagLink tagLink = HarnessTagLink.builder()
                                 .accountId(TEST_ACCOUNT_ID)
                                 .appId(APP_ID)
                                 .entityId("id")
                                 .entityType(SERVICE)
                                 .key(colorTagKey)
                                 .build();

    try {
      harnessTagService.attachTag(tagLink);
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo("Tag value cannot be null");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testInvalidTagKey() {
    testInvalidTagKeyUtil("", "Tag name cannot be blank");
    testInvalidTagKeyUtil("  ", "Tag name cannot be blank");
    testInvalidTagKeyUtil(" _key", "Tag name/value cannot begin with .-_/");
    testInvalidTagKeyUtil(" -key", "Tag name/value cannot begin with .-_/");
    testInvalidTagKeyUtil(" .key", "Tag name/value cannot begin with .-_/");
    testInvalidTagKeyUtil(" /key", "Tag name/value cannot begin with .-_/");
    testInvalidTagKeyUtil(" tag + key",
        "Tag name/value can contain only abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_ /");
    testInvalidTagKeyUtil("harness.io/abc", "Unauthorized: harness.io is a reserved Tag name prefix");
    testInvalidTagKeyUtil(" ${workflow.variables.tag1}",
        "Tag name/value can contain only abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_ /");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidTagKey() {
    testValidTagKeyUtil(" tagKey", "tagKey");
    testValidTagKeyUtil(" tag Key", "tag Key");
    testValidTagKeyUtil(" tag 9 Key ", "tag 9 Key");
    testValidTagKeyUtil(" tag 9 / Key ", "tag 9 / Key");
    testValidTagKeyUtil(" tag 9 _ Key ", "tag 9 _ Key");
    testValidTagKeyUtil(" tag 9 . Key ", "tag 9 . Key");
    testValidTagKeyUtil(" tag 9 Key - ", "tag 9 Key -");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testInvalidTagValue() {
    testInvalidTagValueUtil(null, "Tag value cannot be null");
    testInvalidTagValueUtil(" _value", "Tag name/value cannot begin with .-_/");
    testInvalidTagValueUtil(" -value", "Tag name/value cannot begin with .-_/");
    testInvalidTagValueUtil(" .value", "Tag name/value cannot begin with .-_/");
    testInvalidTagValueUtil(" /value", "Tag name/value cannot begin with .-_/");
    testInvalidTagValueUtil(" tag + key",
        "Tag name/value can contain only abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_ /");
    testInvalidTagValueUtil("${workflow.variables.tag1}",
        "Tag name/value can contain only abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_ /");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateSystemTag() {
    HarnessTag tag = HarnessTag.builder().accountId(TEST_ACCOUNT_ID).key("system/key").build();
    harnessTagService.create(tag);
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, "system/key");
    assertThat(savedTag).isNotNull();
    assertThat(savedTag.getUuid()).isNotEmpty();
    assertThat(savedTag).hasFieldOrPropertyWithValue("key", "system/key");

    try {
      tag = HarnessTag.builder().accountId(TEST_ACCOUNT_ID).key("system/key1").build();
      harnessTagService.createTag(tag, false, false);
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo("Unauthorized: User need to have MANAGE_TAGS permission to create system tags");
    }
  }

  private void testInvalidTagKeyUtil(String key, String expectedExceptionMessage) {
    try {
      HarnessTag tag = HarnessTag.builder().accountId(TEST_ACCOUNT_ID).key(key).build();
      harnessTagService.create(tag);
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo(expectedExceptionMessage);
    }
  }

  private void testValidTagKeyUtil(String key, String expectedKey) {
    HarnessTag tag =
        HarnessTag.builder().accountId(TEST_ACCOUNT_ID).key(key).allowedValues(Sets.newHashSet("")).build();
    harnessTagService.create(tag);
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, expectedKey);
    assertThat(savedTag).isNotNull();
    assertThat(savedTag.getUuid()).isNotEmpty();
    assertThat(savedTag).hasFieldOrPropertyWithValue("key", expectedKey);
  }

  private void testInvalidTagValueUtil(String value, String expectedExceptionMessage) {
    try {
      HarnessTag tag =
          HarnessTag.builder().accountId(TEST_ACCOUNT_ID).key("key").allowedValues(Sets.newHashSet(value)).build();
      harnessTagService.create(tag);
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo(expectedExceptionMessage);
    }
  }

  @Test
  @Owner(developers = ANSHUL)

  @Category(UnitTests.class)
  public void testUpdateTagAllowedValues() {
    harnessTagService.create(colorTag);
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag.getAllowedValues()).isEqualTo(null);

    savedTag.setAllowedValues(Sets.newHashSet("red"));
    harnessTagService.update(savedTag);
    HarnessTag savedTag1 = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag1.getAllowedValues()).isEqualTo(Sets.newHashSet("red"));

    savedTag.setAllowedValues(null);
    harnessTagService.update(savedTag);
    HarnessTag savedTag2 = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag2.getAllowedValues()).isEqualTo(null);

    savedTag.setAllowedValues(Sets.newHashSet("red", "blue", "green"));
    harnessTagService.update(savedTag);
    HarnessTag savedTag3 = harnessTagService.get(TEST_ACCOUNT_ID, colorTagKey);
    assertThat(savedTag3.getAllowedValues()).isEqualTo(Sets.newHashSet("red", "blue", "green"));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdateAllowedValuesDifferentFromInUseValues() {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(SERVICE)
                                    .key(colorTagKey)
                                    .value("red")
                                    .build());

    colorTag.setAllowedValues(Collections.emptySet());
    try {
      harnessTagService.update(colorTag);
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo(
              "Allowed values must contain all in used values. Value [red] is missing in current allowed values list");
    }

    colorTag.setAllowedValues(Sets.newHashSet("green"));
    try {
      harnessTagService.update(colorTag);
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo(
              "Allowed values must contain all in used values. Value [red] is missing in current allowed values list");
    }

    colorTag.setAllowedValues(Sets.newHashSet("red"));
    colorTag = harnessTagService.update(colorTag);

    colorTag.getAllowedValues().add("green");
    colorTag = harnessTagService.update(colorTag);

    colorTag.setAllowedValues(Sets.newHashSet("blue"));
    try {
      harnessTagService.update(colorTag);
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo(
              "Allowed values must contain all in used values. Value [red] is missing in current allowed values list");
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void attachTagWithWorkflowVariableAsKey() {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(WORKFLOW)
                                    .key("${workflow.variables.myVar}")
                                    .value("")
                                    .build());
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, "${workflow.variables.myVar}");
    assertThat(savedTag).isNotNull();
    assertThat(savedTag.getUuid()).isNotEmpty();
    assertThat(savedTag).hasFieldOrPropertyWithValue("key", "${workflow.variables.myVar}");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void attachTagWithAccountDefaultAsKey() {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(WORKFLOW)
                                    .key("${account.defaults.def1}")
                                    .value("")
                                    .build());
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, "${account.defaults.def1}");
    assertThat(savedTag).isNotNull();
    assertThat(savedTag.getUuid()).isNotEmpty();
    assertThat(savedTag).hasFieldOrPropertyWithValue("key", "${account.defaults.def1}");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void attachTagWithAppDefaultAsKey() {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(WORKFLOW)
                                    .key("${app.defaults.def1}")
                                    .value("")
                                    .build());
    HarnessTag savedTag = harnessTagService.get(TEST_ACCOUNT_ID, "${app.defaults.def1}");
    assertThat(savedTag).isNotNull();
    assertThat(savedTag.getUuid()).isNotEmpty();
    assertThat(savedTag).hasFieldOrPropertyWithValue("key", "${app.defaults.def1}");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void attachTagWithInvalidExpressionAsKey() {
    try {
      harnessTagService.attachTag(HarnessTagLink.builder()
                                      .accountId(TEST_ACCOUNT_ID)
                                      .appId(APP_ID)
                                      .entityId("id")
                                      .entityType(WORKFLOW)
                                      .key("${app1.name}")
                                      .value("")
                                      .build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo(EXPR_FAIL_MESSAGE);
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void attachTagWithExpressionAsKeyAndValueShouldFail() {
    try {
      harnessTagService.attachTag(HarnessTagLink.builder()
                                      .accountId(TEST_ACCOUNT_ID)
                                      .appId(APP_ID)
                                      .entityId("id")
                                      .entityType(WORKFLOW)
                                      .key("${workflow.variables.myTag}")
                                      .value("abc")
                                      .build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo("Tag value should be empty as key contains expression");
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void attachTagWithWorkflowVariableAsValue() {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(WORKFLOW)
                                    .key("env")
                                    .value("${workflow.variables.myVar}")
                                    .build());

    HarnessTag tag = harnessTagService.getTagWithInUseValues(TEST_ACCOUNT_ID, "env");
    assertThat(tag.getKey()).isEqualTo("env");
    assertThat(tag.getInUseValues()).containsAll(ImmutableSet.of("${workflow.variables.myVar}"));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void attachTagWithAppNameAsKey() {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(WORKFLOW)
                                    .key("${app.name}")
                                    .value("")
                                    .build());
    HarnessTag tag = harnessTagService.getTagWithInUseValues(TEST_ACCOUNT_ID, "${app.name}");
    assertThat(tag.getKey()).isEqualTo("${app.name}");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void attachTagWithInvalidCharacterInKey() {
    try {
      harnessTagService.attachTag(HarnessTagLink.builder()
                                      .accountId(TEST_ACCOUNT_ID)
                                      .appId(APP_ID)
                                      .entityId("id")
                                      .entityType(WORKFLOW)
                                      .key("env")
                                      .value("\\sds")
                                      .build());
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo(
              "Tag name/value can contain only abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_ /$ {}");
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void attachPipelineDefaultsAsTagKeyToWorkflow() {
    try {
      harnessTagService.attachTag(HarnessTagLink.builder()
                                      .accountId(TEST_ACCOUNT_ID)
                                      .appId(APP_ID)
                                      .entityId("id")
                                      .entityType(WORKFLOW)
                                      .key("${pipeline.name}")
                                      .value("")
                                      .build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo("Pipeline defaults cannot be used as tags in workflow");
    }
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void attachPipelineDefaultsAsTagValueToWorkflow() {
    try {
      harnessTagService.attachTag(HarnessTagLink.builder()
                                      .accountId(TEST_ACCOUNT_ID)
                                      .appId(APP_ID)
                                      .entityId("id")
                                      .entityType(WORKFLOW)
                                      .key("env")
                                      .value("${pipeline.name}")
                                      .build());
      fail("Expected an InvalidRequestException to be thrown");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo("Pipeline defaults cannot be used as tags in workflow");
    }
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkIfPermissionCorrectForCreate() throws NoSuchMethodException {
    Method method = HarnessTagResource.class.getDeclaredMethod("create", String.class, HarnessTag.class);
    AuthRule annotation = method.getAnnotation(AuthRule.class);
    assertThat(annotation.permissionType()).isEqualTo(MANAGE_TAGS);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkIfPermissionCorrectForUpdate() throws NoSuchMethodException {
    Method method = HarnessTagResource.class.getDeclaredMethod("update", String.class, String.class, HarnessTag.class);
    AuthRule annotation = method.getAnnotation(AuthRule.class);
    assertThat(annotation.permissionType()).isEqualTo(MANAGE_TAGS);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkIfPermissionCorrectForDelete() throws NoSuchMethodException {
    Method method = HarnessTagResource.class.getDeclaredMethod("delete", String.class, String.class);
    AuthRule annotation = method.getAnnotation(AuthRule.class);
    assertThat(annotation.permissionType()).isEqualTo(MANAGE_TAGS);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void failAttachExpressionAsTagKeyToEntitiesOtherThanWorkflowPipeline() {
    assertThatThrownBy(()
                           -> harnessTagService.attachTag(HarnessTagLink.builder()
                                                              .accountId(TEST_ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .entityId("id")
                                                              .entityType(SERVICE)
                                                              .key("env")
                                                              .value("${pipeline.name}")
                                                              .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Tag name/value can contain only abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_ /");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void failAttachTagWithUnsupportedEntityType() {
    assertThatThrownBy(()
                           -> harnessTagService.attachTag(HarnessTagLink.builder()
                                                              .accountId(TEST_ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .entityId("id")
                                                              .entityType(EntityType.ARTIFACT)
                                                              .key("env")
                                                              .value("${pipeline.name}")
                                                              .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported entityType specified. ARTIFACT");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void attachTagWithExistingTagLink() {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(WORKFLOW)
                                    .key("tagKey")
                                    .value("tagValue")
                                    .build());

    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(TEST_ACCOUNT_ID)
                                    .appId(APP_ID)
                                    .entityId("id")
                                    .entityType(WORKFLOW)
                                    .key("tagKey")
                                    .value("changedValue")
                                    .build());

    HarnessTag savedTag = harnessTagService.getTagWithInUseValues(TEST_ACCOUNT_ID, "tagKey");
    assertThat(savedTag).isNotNull();
    assertThat(savedTag.getUuid()).isNotEmpty();
    assertThat(savedTag).hasFieldOrPropertyWithValue("key", "tagKey");
    // inUseValues are still one
    assertThat(savedTag.getInUseValues().size()).isEqualTo(1);
  }
}
