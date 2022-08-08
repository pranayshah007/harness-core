/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import com.google.inject.Singleton;

@Singleton
public class ContainerFamilyCommandProviderFactory {
  public ContainerFamilyCommandProvider getProvider(ContainerFamily containerFamily) {
    switch (containerFamily) {
      case TOMCAT:
        return new TomcatContainerFamilyCommandProvider();
      case JBOSS:
        return new JbossContainerFamilyCommandProvider();
      default:
        throw new IllegalArgumentException(
            String.format("No command provider found for container family type '%s'.", containerFamily));
    }
  }
}
