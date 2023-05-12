/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.repositories.ResourceRestraintRepository;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DuplicateKeyException;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceRestraintServiceImplTest extends OrchestrationStepsTestBase {
  private static final String RESOURCE_CONSTRAINT_ID = "RESOURCE_CONSTRAINT_ID";

  @InjectMocks private ResourceRestraintServiceImpl rrService;
  @Inject private ResourceRestraintRepository resourceRestraintRepository;
  @Mock private ResourceRestraintRepository resourceRestraintRepositoryMock;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetNullWhenResourceConstraintNotFound() {
    when(resourceRestraintRepositoryMock.findById(RESOURCE_CONSTRAINT_ID)).thenReturn(Optional.empty());
    assertThat(rrService.get(RESOURCE_CONSTRAINT_ID)).isNull();
    verify(resourceRestraintRepositoryMock).findById(RESOURCE_CONSTRAINT_ID);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetNullWhenAccountNullAndResourceConstraintNotFound() {
    when(resourceRestraintRepositoryMock.findById(RESOURCE_CONSTRAINT_ID)).thenReturn(Optional.empty());
    assertThat(rrService.get(RESOURCE_CONSTRAINT_ID)).isNull();
    verify(resourceRestraintRepositoryMock).findById(RESOURCE_CONSTRAINT_ID);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSaveResourceRestraint() {
    ResourceRestraint resourceRestraint = ResourceRestraint.builder().build();
    ResourceRestraint result = rrService.save(resourceRestraint);
    verify(resourceRestraintRepositoryMock).save(resourceRestraint);
    assertThat(resourceRestraint).isEqualTo(result);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSaveThrowInvalidRequestWhenCatchIgnoreDuplicateKey() {
    when(resourceRestraintRepositoryMock.save(notNull(ResourceRestraint.class)))
        .thenThrow(new DuplicateKeyException(""));
    assertThatThrownBy(() -> rrService.save(ResourceRestraint.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The resource constraint name cannot be reused.")
        .hasFieldOrPropertyWithValue("reportTargets", WingsException.USER);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldDeleteResourceRestraint() {
    String restraint1 = UUIDGenerator.generateUuid();
    String restraint2 = UUIDGenerator.generateUuid();
    String restraint3 = UUIDGenerator.generateUuid();
    ResourceRestraint restraintForAccount1 =
        ResourceRestraint.builder().uuid(restraint1).accountId("ACCOUNT1").name("r1").build();
    ResourceRestraint restraint2ForAccount1 =
        ResourceRestraint.builder().uuid(restraint2).accountId("ACCOUNT1").name("r2").build();
    ResourceRestraint restraint1ForAccount2 =
        ResourceRestraint.builder().uuid(restraint3).accountId("ACCOUNT2").name("r3").build();
    resourceRestraintRepository.save(restraintForAccount1);
    resourceRestraintRepository.save(restraint2ForAccount1);
    resourceRestraintRepository.save(restraint1ForAccount2);
    on(rrService).set("resourceRestraintRepository", resourceRestraintRepository);

    List<ResourceRestraint> resourceRestraintList =
        resourceRestraintRepository.findByUuidIn(Set.of(restraint1, restraint2, restraint3));
    assertThat(resourceRestraintList.size()).isEqualTo(3);

    rrService.deleteAllRestraintForGivenAccount("ACCOUNT1");
    resourceRestraintList = resourceRestraintRepository.findByUuidIn(Set.of(restraint1, restraint2, restraint3));
    assertThat(resourceRestraintList.size()).isEqualTo(1);
  }
}
