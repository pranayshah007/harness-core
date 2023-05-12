/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.accountsetting.dto.AccountSettingResponseDTO;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.dto.AccountSettingsDTO;
import io.harness.ng.core.accountsetting.dto.ConnectorSettings;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AccountSettingResourceTest extends CategoryTest {
  @Mock NGAccountSettingService accountSettingService;
  @InjectMocks AccountSettingResource accountSettingResource;

  AccountSettingResponseDTO accountSettingResponseDTO;
  AccountSettingsDTO accountSettingsDTO;
  String accountIdentifier = "mockedAccountId";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    accountSettingsDTO = AccountSettingsDTO.builder()
                             .accountIdentifier(accountIdentifier)
                             .type(AccountSettingType.CONNECTOR)
                             .config(ConnectorSettings.builder().builtInSMDisabled(false).build())
                             .build();
    accountSettingResponseDTO = AccountSettingResponseDTO.builder().accountSettings(accountSettingsDTO).build();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void create() {
    doReturn(accountSettingResponseDTO).when(accountSettingService).create(any(), any());
    ResponseDTO<AccountSettingResponseDTO> accountSettingResponseDTOResponseDTO =
        accountSettingResource.create(accountSettingsDTO, accountIdentifier);
    Mockito.verify(accountSettingService, times(1)).create(any(), any());
    assertThat(accountSettingResponseDTOResponseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void update() {
    when(accountSettingService.update(any(), any())).thenReturn(accountSettingResponseDTO);
    ResponseDTO<AccountSettingResponseDTO> accountSettingResponseDTOResponseDTO =
        accountSettingResource.update(accountSettingsDTO, accountIdentifier);
    Mockito.verify(accountSettingService, times(1)).update(any(), any());
    assertThat(accountSettingResponseDTOResponseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void listByAccount() {
    List<AccountSettingsDTO> listResponse = new ArrayList<>();
    listResponse.add(accountSettingsDTO);
    when(accountSettingService.list(accountIdentifier, null, null, null)).thenReturn(listResponse);
    final ResponseDTO<List<AccountSettingsDTO>> listResponseDTO =
        accountSettingResource.list(accountIdentifier, null, null, null);
    Mockito.verify(accountSettingService, times(1)).list(eq(accountIdentifier), eq(null), eq(null), eq(null));
    assertThat(listResponseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void listByAccountAndType() {
    List<AccountSettingsDTO> listResponse = new ArrayList<>();
    listResponse.add(accountSettingsDTO);
    when(accountSettingService.list(accountIdentifier, null, null, AccountSettingType.CONNECTOR))
        .thenReturn(listResponse);
    final ResponseDTO<List<AccountSettingsDTO>> listResponseDTO =
        accountSettingResource.list(accountIdentifier, null, null, AccountSettingType.CONNECTOR);
    Mockito.verify(accountSettingService, times(1))
        .list(eq(accountIdentifier), eq(null), eq(null), eq(AccountSettingType.CONNECTOR));
    assertThat(listResponseDTO.getData()).isNotNull();
  }
}
