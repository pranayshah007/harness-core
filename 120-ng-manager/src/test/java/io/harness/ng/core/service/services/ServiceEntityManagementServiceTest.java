/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services;

import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.Instance;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.service.services.exception.ActiveServiceInstancesPresentException;
import io.harness.repositories.instance.InstanceRepository;
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
  @Mock InstanceRepository instanceRepository;
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
    List<Instance> instanceDTOList = new ArrayList<>();
    instanceDTOList.add(getInstance());
    instanceDTOList.add(getInstance());
    when(instanceRepository.getInstancesCreatedBefore(
             eq(accountIdentifier), eq(orgIdentifier), eq(projectIdentifier), eq(identifier), anyLong()))
        .thenReturn(instanceDTOList);
    assertThatThrownBy(()
                           -> serviceEntityManagementService.deleteService(
                               accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", false))
        .isInstanceOf(ActiveServiceInstancesPresentException.class)
        .hasMessage(
            "Service [identifier] under Project[projectIdentifier], Organization [orgIdentifier] couldn't be deleted since there are currently 2 active instances for the service");
    verify(instanceService, never()).deleteAll(any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldDeleteServiceWhenNoInstances() {
    when(instanceRepository.getInstancesCreatedBefore(
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
    doReturn(true).when(serviceEntityManagementService).isNgSettingsFFEnabled(accountIdentifier);
    doReturn(true).when(serviceEntityManagementService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    List<Instance> instanceDTOList = new ArrayList<>();
    instanceDTOList.add(getInstance());
    instanceDTOList.add(getInstance());
    when(instanceRepository.getInstancesCreatedBefore(
             eq(accountIdentifier), eq(orgIdentifier), eq(projectIdentifier), eq(identifier), anyLong()))
        .thenReturn(instanceDTOList);
    when(serviceEntityService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, null, true))
        .thenReturn(true);
    serviceEntityManagementService.deleteService(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, "", true);
    verify(instanceService, times(1)).deleteAll(any());
  }

  private Instance getInstance() {
    return Instance.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .serviceIdentifier(identifier)
        .envIdentifier(identifier)
        .lastPipelineExecutionId("lastPipelineExecutionId")
        .infraIdentifier("infraIdentifier")
        .envName("envName")
        .envType(EnvironmentType.PreProduction)
        .infrastructureKind(KUBERNETES_DIRECT)
        .primaryArtifact(ArtifactDetails.builder().tag("buildId").build())
        .createdAt(0L)
        .deletedAt(10L)
        .createdAt(0L)
        .lastModifiedAt(0L)
        .instanceInfo(K8sInstanceInfo.builder().podName("podName").releaseName("releaseName").build())
        .build();
  }
}