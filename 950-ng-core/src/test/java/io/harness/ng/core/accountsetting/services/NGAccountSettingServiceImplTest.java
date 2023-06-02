/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

import io.harness.category.element.UnitTests;
import io.harness.ng.core.NGCoreTestBase;
import io.harness.ng.core.accountsetting.AccountSettingMapper;
import io.harness.ng.core.accountsetting.dto.AccountSettingResponseDTO;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.dto.AccountSettingsDTO;
import io.harness.ng.core.accountsetting.dto.ConnectorSettings;
import io.harness.ng.core.accountsetting.entities.AccountSettings;
import io.harness.repositories.accountsetting.AccountSettingRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class NGAccountSettingServiceImplTest extends NGCoreTestBase {
  @Mock AccountSettingMapper accountSettingMapper;
  @Mock AccountSettingRepository accountSettingRepository;
  @InjectMocks NGAccountSettingServiceImpl ngAccountSettingService;
  AccountSettingResponseDTO accountSettingResponseDTO;
  AccountSettingResponseDTO updatedAccountSettingResponseDTO;
  AccountSettingsDTO updatedAccountSettingDTO;
  AccountSettingsDTO accountSettingsDTO;
  AccountSettings accountSettings;
  String accountIdentifier = "mockedAccountId";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    accountSettingsDTO = AccountSettingsDTO.builder()
                             .accountIdentifier(accountIdentifier)
                             .type(AccountSettingType.CONNECTOR)
                             .config(ConnectorSettings.builder().builtInSMDisabled(false).build())
                             .build();

    updatedAccountSettingDTO = AccountSettingsDTO.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .type(AccountSettingType.CONNECTOR)
                                   .config(ConnectorSettings.builder().builtInSMDisabled(true).build())
                                   .build();

    updatedAccountSettingResponseDTO =
        AccountSettingResponseDTO.builder().accountSettings(updatedAccountSettingDTO).build();
    accountSettingResponseDTO = AccountSettingResponseDTO.builder().accountSettings(accountSettingsDTO).build();
    accountSettings = AccountSettings.builder()
                          .accountIdentifier(accountIdentifier)
                          .type(AccountSettingType.CONNECTOR)
                          .config(ConnectorSettings.builder().builtInSMDisabled(false).build())
                          .build();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void create() {
    doReturn(accountSettings).when(accountSettingMapper).toAccountSetting(any(), any());
    doReturn(accountSettings).when(accountSettingRepository).save(accountSettings);
    doReturn(accountSettingResponseDTO).when(accountSettingMapper).toResponseDTO(accountSettings);

    final AccountSettingResponseDTO accountSettingResponseDTO =
        ngAccountSettingService.create(accountSettingsDTO, accountIdentifier);
    Mockito.verify(accountSettingMapper, times(1)).toAccountSetting(eq(accountSettingsDTO), eq(accountIdentifier));
    Mockito.verify(accountSettingRepository, times(1)).save(eq(accountSettings));
    assertThat(accountSettingResponseDTO).isNotNull();
    assertThat(accountSettingResponseDTO.getAccountSettings()).isEqualTo(accountSettingsDTO);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void update() {
    doReturn(accountSettings).when(accountSettingMapper).toAccountSetting(any(), any());
    doReturn(accountSettings).when(accountSettingRepository).updateAccountSetting(accountSettings, accountIdentifier);
    doReturn(accountSettingResponseDTO).when(accountSettingMapper).toResponseDTO(accountSettings);

    final AccountSettingResponseDTO updated = ngAccountSettingService.update(accountSettingsDTO, accountIdentifier);
    Mockito.verify(accountSettingMapper, times(1)).toAccountSetting(eq(accountSettingsDTO), eq(accountIdentifier));
    Mockito.verify(accountSettingRepository, times(1)).updateAccountSetting(eq(accountSettings), eq(accountIdentifier));
    assertThat(updated).isNotNull();
    assertThat(updated.getAccountSettings()).isEqualTo(accountSettingsDTO);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void updateThrowExceptionOnNotFound() {
    doReturn(accountSettings).when(accountSettingMapper).toAccountSetting(any(), any());
    doThrow(new NotFoundException())
        .when(accountSettingRepository)
        .updateAccountSetting(accountSettings, accountIdentifier);
    doReturn(accountSettingResponseDTO).when(accountSettingMapper).toResponseDTO(accountSettings);
    assertThatThrownBy(() -> ngAccountSettingService.update(accountSettingsDTO, accountIdentifier))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void list() {
    List<AccountSettingsDTO> listResponse = new ArrayList<>();
    listResponse.add(accountSettingsDTO);
    List<AccountSettings> listResponseSetting = new ArrayList<>();
    listResponseSetting.add(accountSettings);
    doReturn(accountSettingsDTO).when(accountSettingMapper).toDTO(any());
    doReturn(listResponseSetting).when(accountSettingRepository).findAll(accountIdentifier, null, null, null);

    final List<AccountSettingsDTO> list = ngAccountSettingService.list(accountIdentifier, null, null, null);
    Mockito.verify(accountSettingMapper, times(1)).toDTO(any());
    Mockito.verify(accountSettingRepository, times(1)).findAll(accountIdentifier, null, null, null);
    assertThat(list).isNotNull();
  }
  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void get() {
    doReturn(accountSettingResponseDTO).when(accountSettingMapper).toResponseDTO(any());
    doReturn(accountSettings)
        .when(accountSettingRepository)
        .findByScopeIdentifiersAndType(accountIdentifier, null, null, AccountSettingType.CONNECTOR);

    final AccountSettingResponseDTO accountSettingResponseDTO1 =
        ngAccountSettingService.get(accountIdentifier, null, null, AccountSettingType.CONNECTOR);
    Mockito.verify(accountSettingMapper, times(1)).toResponseDTO(any());
    Mockito.verify(accountSettingRepository, times(1))
        .findByScopeIdentifiersAndType(accountIdentifier, null, null, AccountSettingType.CONNECTOR);
    assertThat(accountSettingResponseDTO1).isNotNull();
  }
}
