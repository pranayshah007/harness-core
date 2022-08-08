/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.AuditYamlService;
import io.harness.audit.api.impl.AuditPermissionValidator;
import io.harness.audit.beans.YamlDiffRecordDTO;
import io.harness.audit.entities.AuditEvent;
import io.harness.audit.entities.ResourceScope;
import io.harness.audit.entities.YamlDiffRecord;
import io.harness.audit.mapper.ResourceScopeMapper;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class AuditYamlResourceTest extends CategoryTest {
  private AuditService auditService;
  private AuditYamlService auditYamlService;
  private AuditPermissionValidator auditPermissionValidator;
  private AuditYamlResource auditYamlResource;

  @Before
  public void setup() {
    auditService = mock(AuditService.class);
    auditYamlService = mock(AuditYamlService.class);
    auditPermissionValidator = mock(AuditPermissionValidator.class);
    auditYamlResource = new AuditYamlResourceImpl(auditService, auditYamlService, auditPermissionValidator);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetWithAuditNotFound() {
    String accountIdentifier = randomAlphabetic(10);
    String auditId = randomAlphabetic(10);

    when(auditService.get(accountIdentifier, auditId)).thenReturn(Optional.empty());

    auditYamlResource.get(accountIdentifier, auditId);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String accountIdentifier = randomAlphabetic(10);
    String auditId = randomAlphabetic(10);
    ResourceScope resourceScope = ResourceScope.builder().accountIdentifier(accountIdentifier).build();
    YamlDiffRecord yamlDiffRecord = YamlDiffRecord.builder().oldYaml(randomAlphabetic(10)).build();

    when(auditService.get(accountIdentifier, auditId))
        .thenReturn(Optional.of(AuditEvent.builder().id(auditId).resourceScope(resourceScope).build()));
    doNothing().when(auditPermissionValidator).validate(accountIdentifier, ResourceScopeMapper.toDTO(resourceScope));
    when(auditYamlService.get(auditId)).thenReturn(yamlDiffRecord);

    ResponseDTO<YamlDiffRecordDTO> responseDTO = auditYamlResource.get(accountIdentifier, auditId);

    verify(auditPermissionValidator, times(1)).validate(accountIdentifier, ResourceScopeMapper.toDTO(resourceScope));
    assertNull(responseDTO.getData().getNewYaml());
    assertEquals(yamlDiffRecord.getOldYaml(), responseDTO.getData().getOldYaml());
    assertNull(responseDTO.getData().getNewYaml());
  }
}
