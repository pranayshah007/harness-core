/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.gitrestraint.services;

import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.distribution.constraint.Consumer.State.FINISHED;
import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.data.structure.UUIDGenerator;
import io.harness.gitopsprovider.entity.GitRestraintInstance;
import io.harness.repositories.GitRestraintInstanceRepository;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.GITOPS)
public class GitRestraintInstanceServiceImplTest extends CDNGTestBase {
  @InjectMocks private GitRestraintInstanceServiceImpl service;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Inject private GitRestraintInstanceRepository repository;
  @Inject private MongoTemplate mongoTemplate;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    on(service).set("gitRestraintInstanceRepository", repository);
    on(service).set("mongoTemplate", mongoTemplate);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldFinishActiveAndUnblockExistingInstances() {
    doReturn("resumeId").when(waitNotifyEngine).doneWith(any(), any());
    String restraint1 = UUIDGenerator.generateUuid();
    String restraint2 = UUIDGenerator.generateUuid();
    GitRestraintInstance restraintForAccount1 = GitRestraintInstance.builder()
                                                    .state(ACTIVE)
                                                    .uuid(restraint1)
                                                    .releaseEntityId("releaseEntity1")
                                                    .resourceUnit("accountIdToken1")
                                                    .build();
    GitRestraintInstance restraintForAccount2 = GitRestraintInstance.builder()
                                                    .state(BLOCKED)
                                                    .uuid(restraint2)
                                                    .releaseEntityId("releaseEntity2")
                                                    .resourceUnit("accountIdToken1")
                                                    .build();
    repository.save(restraintForAccount1);
    repository.save(restraintForAccount2);

    service.finishInstance(restraint1);
    service.updateBlockedConstraints("accountIdToken1");

    GitRestraintInstance updated1 = repository.findById(restraint1).get();
    GitRestraintInstance updated2 = repository.findById(restraint2).get();
    verify(waitNotifyEngine).doneWith(any(), any());
    assertThat(updated1.getState()).isEqualTo(FINISHED);
    assertThat(updated2.getState()).isEqualTo(ACTIVE);
  }
}
