/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.utils;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.notification.exception.NotificationException;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

public class NotificationSettingsHelperTest extends CategoryTest {
  @Mock NGSettingsClient ngSettingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> response;
  private NotificationSettingsHelper notificationSettingsHelper;
  private MockedStatic<NGRestUtils> restUtilsMockedStatic;
  private String accountId = "accountId";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    notificationSettingsHelper = new NotificationSettingsHelper();
    restUtilsMockedStatic = mockStatic(NGRestUtils.class);
    FieldUtils.writeField(notificationSettingsHelper, "ngSettingsClient", ngSettingsClient, true);
    when(ngSettingsClient.getSetting(anyString(), anyString(), any(), any())).thenReturn(response);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void getRecipientsWithValidDomain_recipientsDoesNotHasTargetUrl_filteredRecipientList() {
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("harness.io, office.com").valueType(SettingValueType.STRING).build();
    when(NGRestUtils.getResponse(response)).thenReturn(settingValueResponseDTO);

    List<String> validRecipients =
        notificationSettingsHelper.getRecipientsWithValidDomain(List.of("abc@harness.io", "abc@harness.com"), accountId,
            SettingIdentifiers.EMAIL_NOTIFICATION_DOMAIN_ALLOWLIST);
    assertThat(validRecipients.size()).isEqualTo(1);
    assertThat(validRecipients.get(0)).isEqualTo("abc@harness.io");
  }

  @Test(expected = NotificationException.class)
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void validateRecipient_recipientDoesNotHasTargetUrl_willThrowExceprion() {
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("harness.io,office.com").valueType(SettingValueType.STRING).build();
    when(NGRestUtils.getResponse(response)).thenReturn(settingValueResponseDTO);
    notificationSettingsHelper.validateRecipient(
        "abc@harness.com", accountId, SettingIdentifiers.EMAIL_NOTIFICATION_DOMAIN_ALLOWLIST);
  }
}
