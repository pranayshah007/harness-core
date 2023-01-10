/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.utils;

import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.TEJAS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.ngsettings.SettingPlanConfig;
import io.harness.ngsettings.SettingSource;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

public class SettingUtilsTest extends CategoryTest {
  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  public String getRandomBooleanString() {
    String[] booleanStringArray = {"true", "false", "TRUE", "FALSE"};
    return booleanStringArray[RandomUtils.nextInt(0, booleanStringArray.length - 1)];
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testParseValueForBoolean() {
    Map<String, Boolean> expectedMap = new HashMap<>();
    expectedMap.put("true", true);
    expectedMap.put("TRUE", true);
    expectedMap.put("false", false);
    expectedMap.put("FALSE", false);
    expectedMap.forEach((value, expected) -> {
      SettingDTO settingDTO = SettingDTO.builder().value(value).valueType(SettingValueType.BOOLEAN).build();
      Object actual = SettingUtils.parseValue(settingDTO);
      assertThat(actual).isEqualTo(expected);
    });
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testParseValueForNumber() {
    Map<String, Object> expectedMap = new HashMap<>();
    expectedMap.put("10", 10.0d);
    expectedMap.put("10.5", 10.5d);
    expectedMap.forEach((value, expected) -> {
      SettingDTO settingDTO = SettingDTO.builder().value(value).valueType(SettingValueType.NUMBER).build();
      Object actual = SettingUtils.parseValue(settingDTO);
      assertThat(actual).isEqualTo(expected);
    });
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testParseValueForString() {
    String value = randomAlphabetic(10);
    SettingDTO settingDTO = SettingDTO.builder().value(value).valueType(SettingValueType.STRING).build();
    Object actual = SettingUtils.parseValue(settingDTO);
    assertThat(actual).isEqualTo(value);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateSetting() {
    String value = randomAlphabetic(10);
    String[] allowedValues = {value, randomAlphabetic(5), randomAlphabetic(3), randomAlphabetic(7)};
    SettingDTO settingDTO = SettingDTO.builder()
                                .value(value)
                                .valueType(SettingValueType.STRING)
                                .allowedValues(new HashSet<>(Arrays.asList(allowedValues)))
                                .build();
    SettingUtils.validate(settingDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateSettingForValueAndValueTypeMismatch() {
    String value = randomAlphabetic(10);
    SettingDTO settingDTO = SettingDTO.builder().value(value).valueType(SettingValueType.NUMBER).build();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(String.format("Only numbers are allowed. Received input [%s]", value));
    SettingUtils.validate(settingDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateSettingForAllowedValues() {
    String value = randomAlphabetic(10);
    String[] allowedValues = {randomAlphabetic(9), randomAlphabetic(5), randomAlphabetic(3), randomAlphabetic(7)};
    SettingDTO settingDTO = SettingDTO.builder()
                                .value(value)
                                .valueType(SettingValueType.STRING)
                                .allowedValues(new HashSet<>(Arrays.asList(allowedValues)))
                                .build();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(String.format("The value [%s] is not allowed.", value));
    SettingUtils.validate(settingDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateSettingConfiguration() {
    String value = randomAlphabetic(10);
    String[] allowedValues = {value, randomAlphabetic(5), randomAlphabetic(3), randomAlphabetic(7)};
    SettingConfiguration settingConfiguration = SettingConfiguration.builder()
                                                    .defaultValue(value)
                                                    .valueType(SettingValueType.STRING)
                                                    .allowedValues(new HashSet<>(Arrays.asList(allowedValues)))
                                                    .build();
    SettingUtils.validate(settingConfiguration);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateSettingConfigurationForValueAndValueTypeMismatch() {
    String value = randomAlphabetic(10);
    SettingConfiguration settingConfiguration =
        SettingConfiguration.builder().defaultValue(value).valueType(SettingValueType.NUMBER).build();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(String.format("Only numbers are allowed. Received input [%s]", value));
    SettingUtils.validate(settingConfiguration);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateSettingConfigurationForAllowedValues() {
    String value = randomAlphabetic(10);
    String[] allowedValues = {randomAlphabetic(9), randomAlphabetic(5), randomAlphabetic(3), randomAlphabetic(7)};
    SettingConfiguration settingConfiguration = SettingConfiguration.builder()
                                                    .defaultValue(value)
                                                    .valueType(SettingValueType.STRING)
                                                    .allowedValues(new HashSet<>(Arrays.asList(allowedValues)))
                                                    .build();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(String.format("The value [%s] is not allowed.", value));
    SettingUtils.validate(settingConfiguration);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void getSettingSourceForDefaultSource() {
    Setting setting = Setting.builder().value(randomAlphabetic(10)).build();
    SettingSource source = SettingUtils.getSettingSource(setting);
    assertThat(source).isEqualTo(SettingSource.DEFAULT);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void getSettingSourceForAccountSource() {
    Setting setting = Setting.builder().value(randomAlphabetic(10)).accountIdentifier(randomAlphabetic(10)).build();
    SettingSource source = SettingUtils.getSettingSource(setting);
    assertThat(source).isEqualTo(SettingSource.ACCOUNT);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void getSettingSourceForOrgSource() {
    Setting setting = Setting.builder()
                          .value(randomAlphabetic(10))
                          .accountIdentifier(randomAlphabetic(10))
                          .orgIdentifier(randomAlphabetic(10))
                          .build();
    SettingSource source = SettingUtils.getSettingSource(setting);
    assertThat(source).isEqualTo(SettingSource.ORG);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void getSettingSourceForProjectSource() {
    Setting setting = Setting.builder()
                          .value(randomAlphabetic(10))
                          .accountIdentifier(randomAlphabetic(10))
                          .orgIdentifier(randomAlphabetic(10))
                          .projectIdentifier(randomAlphabetic(10))
                          .build();
    SettingSource source = SettingUtils.getSettingSource(setting);
    assertThat(source).isEqualTo(SettingSource.PROJECT);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetHighestScopeForSettingAtAllScopes() {
    Set<ScopeLevel> allowedScopes =
        new HashSet<>(Arrays.asList(ScopeLevel.ACCOUNT, ScopeLevel.ORGANIZATION, ScopeLevel.PROJECT));
    ScopeLevel scopeLevel = SettingUtils.getHighestScopeForSetting(allowedScopes);
    assertThat(scopeLevel).isEqualTo(ScopeLevel.ACCOUNT);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetHighestScopeForSettingAtOrgAndProjectScopes() {
    Set<ScopeLevel> allowedScopes = new HashSet<>(Arrays.asList(ScopeLevel.ORGANIZATION, ScopeLevel.PROJECT));
    ScopeLevel scopeLevel = SettingUtils.getHighestScopeForSetting(allowedScopes);
    assertThat(scopeLevel).isEqualTo(ScopeLevel.ORGANIZATION);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetHighestScopeForSettingAtAProjectScopes() {
    Set<ScopeLevel> allowedScopes = new HashSet<>(Arrays.asList(ScopeLevel.PROJECT));
    ScopeLevel scopeLevel = SettingUtils.getHighestScopeForSetting(allowedScopes);
    assertThat(scopeLevel).isEqualTo(ScopeLevel.PROJECT);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetParentScopeForAccountScope() {
    Scope scope = Scope.of(randomAlphabetic(10), null, null);
    Scope parentScope = SettingUtils.getParentScope(scope);
    assertThat(parentScope).isEqualTo(null);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetParentScopeForOrgScope() {
    String accId = randomAlphabetic(10);
    Scope scope = Scope.of(accId, randomAlphabetic(10), null);
    Scope parentScope = SettingUtils.getParentScope(scope);
    assertThat(parentScope).isEqualTo(Scope.builder().accountIdentifier(accId).build());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetParentScopeForAProjectScope() {
    String accId = randomAlphabetic(10);
    String orgId = randomAlphabetic(10);
    Scope scope = Scope.of(accId, orgId, randomAlphabetic(10));
    Scope parentScope = SettingUtils.getParentScope(scope);
    assertThat(parentScope).isEqualTo(Scope.builder().accountIdentifier(accId).orgIdentifier(orgId).build());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testIsSettingEditableForAccountEdition() {
    Map<Edition, SettingPlanConfig> allowedPlans = new HashMap<>();
    allowedPlans.put(Edition.FREE, SettingPlanConfig.builder().editable(false).build());
    allowedPlans.put(Edition.ENTERPRISE, SettingPlanConfig.builder().editable(true).build());
    allowedPlans.put(Edition.COMMUNITY, SettingPlanConfig.builder().build());

    SettingConfiguration settingConfiguration = SettingConfiguration.builder().allowedPlans(allowedPlans).build();

    boolean isSettingEditable_Free =
        SettingUtils.isSettingEditableForAccountEdition(Edition.FREE, settingConfiguration);
    assertEquals(isSettingEditable_Free, false);

    boolean isSettingEditable_Enterprise =
        SettingUtils.isSettingEditableForAccountEdition(Edition.ENTERPRISE, settingConfiguration);
    assertTrue(isSettingEditable_Enterprise);

    boolean isSettingEditable_Community =
        SettingUtils.isSettingEditableForAccountEdition(Edition.COMMUNITY, settingConfiguration);
    assertTrue(isSettingEditable_Community);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testIsSettingEditableForAccountEdition_shouldFail() {
    Map<Edition, SettingPlanConfig> allowedPlans = new HashMap<>();
    allowedPlans.put(Edition.FREE, SettingPlanConfig.builder().editable(false).build());

    SettingConfiguration settingConfiguration = SettingConfiguration.builder().allowedPlans(allowedPlans).build();

    SettingUtils.isSettingEditableForAccountEdition(Edition.ENTERPRISE, settingConfiguration);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetDefaultValue() {
    String settingDefaultValue = randomAlphabetic(10);
    String freeDefaultValue = randomAlphabetic(10);

    Map<Edition, SettingPlanConfig> allowedPlans = new HashMap<>();
    allowedPlans.put(Edition.FREE, SettingPlanConfig.builder().defaultValue(freeDefaultValue).build());
    allowedPlans.put(Edition.ENTERPRISE, SettingPlanConfig.builder().build());

    SettingConfiguration settingConfiguration =
        SettingConfiguration.builder().defaultValue(settingDefaultValue).allowedPlans(allowedPlans).build();

    String defaultValue = SettingUtils.getDefaultValue(Edition.FREE, settingConfiguration);
    assertEquals(defaultValue, freeDefaultValue);

    defaultValue = SettingUtils.getDefaultValue(Edition.ENTERPRISE, settingConfiguration);
    assertEquals(defaultValue, settingDefaultValue);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetDefaultValue_shoulFail() {
    String settingDefaultValue = randomAlphabetic(10);
    String freeDefaultValue = randomAlphabetic(10);

    Map<Edition, SettingPlanConfig> allowedPlans = new HashMap<>();
    allowedPlans.put(Edition.FREE, SettingPlanConfig.builder().defaultValue(freeDefaultValue).build());

    SettingConfiguration settingConfiguration =
        SettingConfiguration.builder().defaultValue(settingDefaultValue).allowedPlans(allowedPlans).build();

    SettingUtils.getDefaultValue(Edition.ENTERPRISE, settingConfiguration);
  }
}
