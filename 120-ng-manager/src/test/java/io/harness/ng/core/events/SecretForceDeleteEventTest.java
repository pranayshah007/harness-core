/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.events;

import static io.harness.audit.ResourceTypeConstants.SECRET;
import static io.harness.ng.core.events.SecretForceDeleteEvent.SECRET_FORCE_DELETED;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretForceDeleteEventTest extends CategoryTest {
  private String ACC_ID = randomAlphabetic(10);
  private String ORG_ID = randomAlphabetic(10);
  private String PRO_ID = randomAlphabetic(10);
  private String ID = randomAlphabetic(10);
  private SecretDTOV2 secret = SecretDTOV2.builder()
                                   .identifier(ID)
                                   .orgIdentifier(ORG_ID)
                                   .projectIdentifier(PRO_ID)
                                   .type(SecretType.SecretText)
                                   .spec(SecretTextSpecDTO.builder()
                                             .secretManagerIdentifier("secretManagerId")
                                             .valueType(ValueType.Inline)
                                             .value("value")
                                             .build())
                                   .build();

  private SecretForceDeleteEvent deleteEvent;
  @Before
  public void before() {
    deleteEvent = new SecretForceDeleteEvent(ACC_ID, secret);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetResource() {
    Resource resource = deleteEvent.getResource();
    assertThat(resource.getIdentifier()).isEqualTo(secret.getIdentifier());
    assertThat(resource.getType()).isEqualTo(SECRET);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void getEventType() {
    assertThat(deleteEvent.getEventType()).isEqualTo(SECRET_FORCE_DELETED);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    ResourceScope resourceScope = deleteEvent.getResourceScope();
    assertThat(resourceScope.getScope()).isEqualTo("project");

    ProjectScope projectScope = (ProjectScope) resourceScope;
    assertThat(projectScope.getAccountIdentifier()).isEqualTo(ACC_ID);
    assertThat(projectScope.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(projectScope.getProjectIdentifier()).isEqualTo(PRO_ID);
  }
}
