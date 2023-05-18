/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.setupusage;

import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.settings.SettingVariableTypes.SERVICE_VARIABLE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.secrets.setupusage.builders.ServiceVariableSetupUsageBuilder;
import io.harness.security.encryption.EncryptionType;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.EntityType;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.ServiceVariableType;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ServiceVariableSetupUsageBuilderTest extends WingsBaseTest {
  @Mock ServiceVariableService serviceVariableService;
  @Inject @InjectMocks ServiceVariableSetupUsageBuilder serviceVariableSetupUsageBuilder;
  @Inject private HPersistence persistence;
  private List<ServiceVariable> serviceVariables;
  private Account account;
  private EncryptedData encryptedData;
  private EncryptionDetail encryptionDetail;

  @Before
  public void setup() {
    initMocks(this);
    account = getAccount(AccountType.PAID);
    account.setUuid(persistence.save(account));
    serviceVariables = new ArrayList<>();

    encryptedData = EncryptedData.builder()
                        .encryptionKey("plainTextKey")
                        .encryptedValue("encryptedValue".toCharArray())
                        .encryptionType(EncryptionType.LOCAL)
                        .type(SettingVariableTypes.SECRET_TEXT)
                        .kmsId(UUIDGenerator.generateUuid())
                        .enabled(true)
                        .accountId(account.getUuid())
                        .name("xyz")
                        .build();

    String encryptedDataId = persistence.save(encryptedData);
    encryptedData.setUuid(encryptedDataId);

    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .templateId(UUIDGenerator.generateUuid())
                                          .envId(GLOBAL_ENV_ID)
                                          .entityType(EntityType.SERVICE)
                                          .entityId(UUIDGenerator.generateUuid())
                                          .parentServiceVariableId(UUIDGenerator.generateUuid())
                                          .overrideType(ServiceVariable.OverrideType.ALL)
                                          .accountId(account.getUuid())
                                          .name("service_variable_1")
                                          .encryptedValue(encryptedDataId)
                                          .type(ServiceVariableType.ENCRYPTED_TEXT)
                                          .build();

    serviceVariables.add(persistence.get(ServiceVariable.class, persistence.save(serviceVariable)));

    ServiceTemplate serviceTemplate = ServiceTemplate.Builder.aServiceTemplate()
                                          .withName(UUIDGenerator.generateUuid())
                                          .withServiceId(UUIDGenerator.generateUuid())
                                          .build();
    String serviceTemplateId = persistence.save(serviceTemplate);

    serviceVariable.setUuid(null);
    serviceVariable.setName("service_variable_2");
    serviceVariable.setEntityType(EntityType.SERVICE_TEMPLATE);
    serviceVariable.setEntityId(serviceTemplateId);
    serviceVariables.add(persistence.get(ServiceVariable.class, persistence.save(serviceVariable)));

    encryptionDetail =
        EncryptionDetail.builder().secretManagerName("secretManagerName").encryptionType(EncryptionType.LOCAL).build();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBuildSecretSetupUsage_withoutServiceTemplate() {
    PageResponse<ServiceVariable> serviceVariablePageResponse = mock(PageResponse.class);
    when(serviceVariablePageResponse.getResponse()).thenReturn(serviceVariables);
    when(serviceVariableService.list(any())).thenReturn(serviceVariablePageResponse);

    Map<String, Set<EncryptedDataParent>> parentByParentIds = new HashMap<>();
    parentByParentIds.put(serviceVariables.get(0).getUuid(), new HashSet<>());
    parentByParentIds.put(serviceVariables.get(1).getUuid(), new HashSet<>());

    Set<SecretSetupUsage> secretSetupUsages = serviceVariableSetupUsageBuilder.buildSecretSetupUsages(
        account.getUuid(), encryptedData.getUuid(), parentByParentIds, encryptionDetail);

    assertThat(secretSetupUsages).hasSize(2);
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getEntityId).collect(Collectors.toSet()))
        .isEqualTo(parentByParentIds.keySet());
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getType).collect(Collectors.toSet()))
        .isEqualTo(Sets.newHashSet(SERVICE_VARIABLE));
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getFieldName).collect(Collectors.toSet()))
        .isEqualTo(Sets.newHashSet(ServiceVariableKeys.encryptedValue));
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getEntity).collect(Collectors.toSet()))
        .isEqualTo(new HashSet<>(serviceVariables));
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBuildSecretSetupUsage_withServiceTemplate_shouldFail() {
    serviceVariables.get(1).setEntityId(UUIDGenerator.generateUuid());
    PageResponse<ServiceVariable> serviceVariablePageResponse = mock(PageResponse.class);
    when(serviceVariablePageResponse.getResponse()).thenReturn(serviceVariables);
    when(serviceVariableService.list(any())).thenReturn(serviceVariablePageResponse);

    Map<String, Set<EncryptedDataParent>> parentByParentIds = new HashMap<>();
    parentByParentIds.put(serviceVariables.get(0).getUuid(), new HashSet<>());
    parentByParentIds.put(serviceVariables.get(1).getUuid(), new HashSet<>());

    serviceVariableSetupUsageBuilder.buildSecretSetupUsages(
        account.getUuid(), encryptedData.getUuid(), parentByParentIds, encryptionDetail);
  }
}
