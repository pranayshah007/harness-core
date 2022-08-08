/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static software.wings.audit.ResourceType.LOAD_BALANCER;

import software.wings.settings.SettingValue;

/**
 * Created by peeyushaggarwal on 9/15/16.
 */
public abstract class LoadBalancerConfig extends SettingValue {
  /**
   * Instantiates a new setting value.
   *
   * @param type the type
   */
  public LoadBalancerConfig(String type) {
    super(type);
  }

  @Override
  public String fetchResourceCategory() {
    return LOAD_BALANCER.name();
  }
}
