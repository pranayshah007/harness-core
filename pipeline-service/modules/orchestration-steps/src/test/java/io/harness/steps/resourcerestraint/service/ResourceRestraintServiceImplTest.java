/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.repositories.ResourceRestraintRepository;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.dao.DuplicateKeyException;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceRestraintServiceImplTest {
  private static final String RESOURCE_CONSTRAINT_ID = "RESOURCE_CONSTRAINT_ID";

  @InjectMocks private ResourceRestraintServiceImpl rrService;
  @Mock private ResourceRestraintRepository resourceRestraintRepository;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetNullWhenResourceConstraintNotFound() {
    when(resourceRestraintRepository.findById(RESOURCE_CONSTRAINT_ID)).thenReturn(Optional.empty());
    assertThat(rrService.get(RESOURCE_CONSTRAINT_ID)).isNull();
    verify(resourceRestraintRepository).findById(RESOURCE_CONSTRAINT_ID);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetNullWhenAccountNullAndResourceConstraintNotFound() {
    when(resourceRestraintRepository.findById(RESOURCE_CONSTRAINT_ID)).thenReturn(Optional.empty());
    assertThat(rrService.get(RESOURCE_CONSTRAINT_ID)).isNull();
    verify(resourceRestraintRepository).findById(RESOURCE_CONSTRAINT_ID);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSaveResourceRestraint() {
    ResourceRestraint resourceRestraint = ResourceRestraint.builder().build();
    ResourceRestraint result = rrService.save(resourceRestraint);
    verify(resourceRestraintRepository).save(resourceRestraint);
    assertThat(resourceRestraint).isEqualTo(result);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSaveThrowInvalidRequestWhenCatchIgnoreDuplicateKey() {
    when(resourceRestraintRepository.save(notNull(ResourceRestraint.class))).thenThrow(new DuplicateKeyException(""));
    assertThatThrownBy(() -> rrService.save(ResourceRestraint.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The resource constraint name cannot be reused.")
        .hasFieldOrPropertyWithValue("reportTargets", WingsException.USER);
  }
}
