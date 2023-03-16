/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public enum Action {
  CREATE,
  UPDATE,
  RESTORE,
  DELETE,
  FORCE_DELETE,
  UPSERT,
  INVITE,
  RESEND_INVITE,
  REVOKE_INVITE,
  ADD_COLLABORATOR,
  REMOVE_COLLABORATOR,
  CREATE_TOKEN,
  REVOKE_TOKEN,
  LOGIN,
  LOGIN2FA,
  UNSUCCESSFUL_LOGIN,

  // Deprecated
  ADD_MEMBERSHIP,
  REMOVE_MEMBERSHIP,

  // SLO Event
  ERROR_BUDGET_RESET,
  // START, END, PAUSE, RESUME, ABORT,TIMEOUT are used as PipelineExecution Actions
  START,
  END,
  PAUSE,
  RESUME,
  ABORT,
  TIMEOUT
}
