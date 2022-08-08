/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.channelDetails;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.encryption.Scope;
import io.harness.utils.IdentifierRefHelper;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class NotificationChannelUtils {
  io.harness.notification.NotificationRequest.UserGroup getUserGroups(
      String identifier, String accountId, String orgIdentifier, String projectIdentifier) {
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(identifier, accountId, orgIdentifier, projectIdentifier);

    if (identifierRef.getScope() == Scope.ACCOUNT) {
      return io.harness.notification.NotificationRequest.UserGroup.newBuilder()
          .setIdentifier(identifierRef.getIdentifier())
          .build();
    }
    if (identifierRef.getScope() == Scope.ORG) {
      return io.harness.notification.NotificationRequest.UserGroup.newBuilder()
          .setIdentifier(identifierRef.getIdentifier())
          .setOrgIdentifier(orgIdentifier)
          .build();
    }
    return io.harness.notification.NotificationRequest.UserGroup.newBuilder()
        .setIdentifier(identifierRef.getIdentifier())
        .setOrgIdentifier(orgIdentifier)
        .setProjectIdentifier(projectIdentifier)
        .build();
  }
}
