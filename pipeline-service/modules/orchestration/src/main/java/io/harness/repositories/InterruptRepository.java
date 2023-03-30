/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.interrupts.InterruptType;

import java.util.EnumSet;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

@OwnedBy(CDC)
@HarnessRepo
public interface InterruptRepository extends CrudRepository<Interrupt, String> {
  List<Interrupt> findByPlanExecutionIdAndStateInOrderByCreatedAtDesc(
      String planExecutionId, EnumSet<State> registered);

  List<Interrupt> findByPlanExecutionIdOrderByCreatedAtDesc(String planExecutionId);

  List<Interrupt> findByPlanExecutionIdAndStateInAndTypeInOrderByCreatedAtDesc(
      String planExecutionId, EnumSet<State> states, EnumSet<InterruptType> planLevelInterrupts);

  List<Interrupt> findByPlanExecutionIdAndNodeExecutionIdAndStateInOrderByCreatedAtDesc(
      String planExecutionId, String nodeExecutionId, EnumSet<State> states);
  List<Interrupt> findByPlanExecutionIdAndNodeExecutionIdAndTypeAndStateInOrderByCreatedAtDesc(
      String planExecutionId, String nodeExecutionId, InterruptType interruptType, EnumSet<State> states);

  List<Interrupt> findByPlanExecutionIdAndTypeIn(String planExecutionId, EnumSet<InterruptType> types);
}
