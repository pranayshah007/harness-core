/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import com.google.common.base.MoreObjects;
import dev.morphia.annotations.Id;

/**
 * Created by peeyushaggarwal on 7/13/16.
 */
public class InstanceCountByEnv {
  @Id private String envId;
  private int count;

  /**
   * Gets env id.
   *
   * @return the env id
   */
  public String getEnvId() {
    return envId;
  }

  /**
   * Sets env id.
   *
   * @param envId the env id
   */
  public void setEnvId(String envId) {
    this.envId = envId;
  }

  /**
   * Gets count.
   *
   * @return the count
   */
  public int getCount() {
    return count;
  }

  /**
   * Sets count.
   *
   * @param count the count
   */
  public void setCount(int count) {
    this.count = count;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("envId", envId).add("count", count).toString();
  }
}
