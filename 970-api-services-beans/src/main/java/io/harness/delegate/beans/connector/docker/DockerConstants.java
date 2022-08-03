/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.docker;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDC)
public class DockerConstants {
  // auth types
  public static final String USERNAME_PASSWORD = "UsernamePassword";
  public static final String ANONYMOUS = "Anonymous";
  public static final String DOCKER_HUB = "DockerHub";
  public static final String HARBOR = "Harbor";
  public static final String QUAY = "Quay";
  public static final String OTHER = "Other";
}
