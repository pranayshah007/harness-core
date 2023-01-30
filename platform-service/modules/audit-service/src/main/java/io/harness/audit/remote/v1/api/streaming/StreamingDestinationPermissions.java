/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.remote.v1.api.streaming;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class StreamingDestinationPermissions {
  public static final String VIEW_STREAMING_DESTINATION_PERMISSION = "core_streamingDestination_view";
  public static final String EDIT_STREAMING_DESTINATION_PERMISSION = "core_streamingDestination_edit";
  public static final String DELETE_STREAMING_DESTINATION_PERMISSION = "core_streamingDestination_delete";
}
