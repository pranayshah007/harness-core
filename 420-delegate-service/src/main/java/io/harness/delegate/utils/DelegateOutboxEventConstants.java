/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(DEL)
@UtilityClass
public class DelegateOutboxEventConstants {
  public static final String DELEGATE_UPSERT_EVENT = "DelegateGroupUpsertEvent";
  public static final String DELEGATE_DELETE_EVENT = "DelegateGroupDeleteEvent";
  public static final String DELEGATE_REGISTER_EVENT = "DelegateRegisterEvent";
  public static final String DELEGATE_UNREGISTER_EVENT = "DelegateUnRegisterEvent";

  public static final String DELEGATE_CONFIGURATION_CREATE_EVENT = "DelegateProfileCreated";
  public static final String DELEGATE_CONFIGURATION_DELETE_EVENT = "DelegateProfileDeleted";
  public static final String DELEGATE_CONFIGURATION_UPDATE_EVENT = "DelegateProfileUpdated";

  public static final String DELEGATE_TOKEN_CREATE_EVENT = "DelegateNgTokenCreateEvent";
  public static final String DELEGATE_TOKEN_REVOKE_EVENT = "DelegateNgTokenRevokeEvent";
}
