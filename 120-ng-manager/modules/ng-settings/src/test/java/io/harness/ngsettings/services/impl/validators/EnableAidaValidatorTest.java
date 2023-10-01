/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl.validators;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eula.service.EulaService;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngsettings.services.SettingsService;
import io.harness.rule.Owner;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class EnableAidaValidatorTest extends CategoryTest {
  @Mock EulaService eulaService;
  @Mock SettingsService settingsService;
  private EnableAidaValidator enableAidaValidator;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  private static final String ACCOUNT_ID = "accountIdentifier";
  private static final String SETTING_ID = "aida";

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.openMocks(this);
    enableAidaValidator = new EnableAidaValidator();
    FieldUtils.writeField(enableAidaValidator, "settingsService", settingsService, true);
    FieldUtils.writeField(enableAidaValidator, "eulaService", eulaService, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testAidaEnablementOnProjectScope_whenDisabledOnAccountScope_throwsError() throws InvalidRequestException {
    SettingDTO settingDTO = SettingDTO.builder()
                                .category(SettingCategory.EULA)
                                .defaultValue(Boolean.toString(false))
                                .name("AIDA")
                                .identifier(SETTING_ID)
                                .value(Boolean.toString(true))
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .build();
    when(settingsService.get(SETTING_ID, ACCOUNT_ID, null, null))
        .thenReturn(SettingValueResponseDTO.builder().value(Boolean.toString(false)).build());
    enableAidaValidator.validate(ACCOUNT_ID, settingDTO, settingDTO);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testAidaEnablementOnAccountScope_whenEulaIsNotSigned_throwsError() throws InvalidRequestException {
    SettingDTO settingDTO = SettingDTO.builder()
                                .category(SettingCategory.EULA)
                                .defaultValue(Boolean.toString(false))
                                .name("AIDA")
                                .identifier(SETTING_ID)
                                .value(Boolean.toString(true))
                                .build();
    when(eulaService.isSigned(any(), anyString())).thenReturn(false);
    enableAidaValidator.validate(ACCOUNT_ID, settingDTO, settingDTO);
  }
}
