/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.SettingSource;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;

import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class SettingUtils {
  private static Object parseValue(String value, SettingValueType valueType) {
    switch (valueType) {
      case BOOLEAN:
        Boolean parsedValue = BooleanUtils.toBooleanObject(value);
        if (parsedValue == null) {
          throw new InvalidRequestException(
              String.format("Only boolean values are allowed. Received input [%s]", value));
        }
        return BooleanUtils.toBoolean(parsedValue);
      case NUMBER:
        try {
          return Double.valueOf(value);
        } catch (NumberFormatException exception) {
          throw new InvalidRequestException(String.format("Only numbers are allowed. Received input [%s]", value));
        }
      case STRING:
        return value;
      default:
        throw new InvalidRequestException(String.format("Value type [%s] is not supported.", valueType));
    }
  }

  private static void validateValueForAllowedValues(SettingDTO setting) {
    validateValueForAllowedValues(setting.getValue(), setting.getAllowedValues());
  }

  private static void validateValueForAllowedValues(String value, Set<String> allowedValues) {
    if (isNotEmpty(allowedValues) && !allowedValues.contains(value)) {
      throw new InvalidRequestException(String.format("The value [%s] is not allowed.", value));
    }
  }

  public static Object parseValue(SettingDTO setting) {
    String value = setting.getValue();
    SettingValueType valueType = setting.getValueType();
    return parseValue(value, valueType);
  }

  public static void validate(SettingDTO setting) {
    parseValue(setting);
    validateValueForAllowedValues(setting);
  }

  public static void validate(SettingConfiguration settingConfiguration) {
    if (isNotEmpty(settingConfiguration.getDefaultValue())) {
      parseValue(settingConfiguration.getDefaultValue(), settingConfiguration.getValueType());
      validateValueForAllowedValues(settingConfiguration.getDefaultValue(), settingConfiguration.getAllowedValues());
    }
  }

  public static SettingSource getSettingSource(Setting setting) {
    if (isEmpty(setting.getAccountIdentifier())) {
      return SettingSource.DEFAULT;
    }
    return getSettingSourceFromOrgAndProject(setting.getOrgIdentifier(), setting.getProjectIdentifier());
  }

  public static SettingSource getSettingSourceFromOrgAndProject(String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(orgIdentifier)) {
      if (isNotEmpty(projectIdentifier)) {
        return SettingSource.PROJECT;
      }
      return SettingSource.ORG;
    }
    return SettingSource.ACCOUNT;
  }

  public ScopeLevel getHighestScopeForSetting(Set<ScopeLevel> allowedScopes) {
    if (allowedScopes.contains(ScopeLevel.ACCOUNT)) {
      return ScopeLevel.ACCOUNT;
    } else if (allowedScopes.contains(ScopeLevel.ORGANIZATION)) {
      return ScopeLevel.ORGANIZATION;
    } else {
      return ScopeLevel.PROJECT;
    }
  }

  public Scope getParentScope(Scope currentScope) {
    if (isNotEmpty(currentScope.getProjectIdentifier())) {
      return Scope.builder()
          .accountIdentifier(currentScope.getAccountIdentifier())
          .orgIdentifier(currentScope.getOrgIdentifier())
          .build();
    } else if (isNotEmpty(currentScope.getOrgIdentifier())) {
      return Scope.builder().accountIdentifier(currentScope.getAccountIdentifier()).build();
    } else {
      return null;
    }
  }
}
