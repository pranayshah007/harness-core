/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector;

import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;

import com.google.common.base.Preconditions;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class K8sServiceAccountInfoResponse implements DelegateTaskNotifyResponseData {
  private String username;
  private List<String> groups;
  private DelegateMetaInfo delegateMetaInfo;

  public String getName() {
    return getUsernameArray()[3];
  }

  public String getNamespace() {
    return getUsernameArray()[2];
  }

  private String[] getUsernameArray() {
    Preconditions.checkState(username != null, "serviceAccount username is null");
    // "username": "system:serviceaccount:harness-delegate:default"
    final String[] usernameArray = username.split(":");

    Preconditions.checkState(
        usernameArray.length == 4, String.format("serviceAccount username [%s] is not of size 4", username));

    return usernameArray;
  }
}
