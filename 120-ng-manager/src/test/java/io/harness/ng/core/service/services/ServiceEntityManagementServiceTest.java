/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services;

import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class ServiceEntityManagementServiceTest extends CategoryTest {
  @Mock ServiceEntityService serviceEntityService;
  @Mock InstanceService instanceService;
  @Spy @Inject @InjectMocks ServiceEntityManagementServiceImpl serviceEntityManagementService;

  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String identifier = "identifier";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenInstancesRunning() {
    List<InstanceDTO> instanceDTOList = new ArrayList<>();
    instanceDTOList.add(getInstance());
    instanceDTOList.add(getInstance());
    when(instanceService.getActiveInstancesByServiceId(
             eq(accountIdentifier), eq(orgIdentifier), eq(projectIdentifier), eq(identifier), anyLong()))
        .thenReturn(instanceDTOList);
    assertThatThrownBy(()
                           -> serviceEntityManagementService.deleteService(
                               accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Service [identifier] under Project[projectIdentifier], Organization [orgIdentifier] couldn't be deleted since there are currently 2 active instances for the service");
    verify(instanceService, never()).deleteAll(any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldDeleteServiceWhenNoInstances() {
    when(instanceService.getActiveInstancesByServiceId(
             eq(accountIdentifier), eq(orgIdentifier), eq(projectIdentifier), eq(identifier), anyLong()))
        .thenReturn(null);
    serviceEntityManagementService.deleteService(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", false);
    verify(serviceEntityService).delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, null, false);
    verify(instanceService, never()).deleteAll(any());
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void shouldForceDeleteServiceInstances() {
    doReturn(true).when(serviceEntityManagementService).isForceDeleteFFEnabled(accountIdentifier);
    doReturn(true).when(serviceEntityManagementService).isNgSettingsFFEnabled(accountIdentifier);
    doReturn(true).when(serviceEntityManagementService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    List<InstanceDTO> instanceDTOList = new ArrayList<>();
    instanceDTOList.add(getInstance());
    instanceDTOList.add(getInstance());
    when(instanceService.getActiveInstancesByServiceId(
             eq(accountIdentifier), eq(orgIdentifier), eq(projectIdentifier), eq(identifier), anyLong()))
        .thenReturn(instanceDTOList);
    when(serviceEntityService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, null, true))
        .thenReturn(true);
    serviceEntityManagementService.deleteService(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", true);
    verify(instanceService, times(1)).deleteAll(any());
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void shouldForceDeleteServiceInstanceFalse() {
    doReturn(false).when(serviceEntityManagementService).isForceDeleteFFEnabled(accountIdentifier);
    doReturn(true).when(serviceEntityManagementService).isNgSettingsFFEnabled(accountIdentifier);
    doReturn(true).when(serviceEntityManagementService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    List<InstanceDTO> instanceDTOList = new ArrayList<>();
    instanceDTOList.add(getInstance());
    instanceDTOList.add(getInstance());
    when(instanceService.getActiveInstancesByServiceId(
             eq(accountIdentifier), eq(orgIdentifier), eq(projectIdentifier), eq(identifier), anyLong()))
        .thenReturn(instanceDTOList);
    when(serviceEntityService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, null, true))
        .thenReturn(true);
    assertThatThrownBy(()
                           -> serviceEntityManagementService.deleteService(
                               accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Parameter forcedDelete cannot be true. Force Delete is not enabled for account [accountIdentifier]");
    verify(instanceService, never()).deleteAll(any());
  }

  private InstanceDTO getInstance() {
    return InstanceDTO.builder()
        .instanceKey(identifier)
        .accountIdentifier(accountIdentifier)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .envIdentifier(identifier)
        .serviceIdentifier(identifier)
        .build();
  }
}