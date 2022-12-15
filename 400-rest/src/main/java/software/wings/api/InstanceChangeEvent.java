/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.queue.Queuable;

import software.wings.beans.infrastructure.instance.Instance;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Entity;

/**
 * This is a wrapper class of Instance to make it extend queuable.
 * This is used as request for capturing instance information.
 * @author rktummala on 08/24/17
 *
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "instanceChangeQueue", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class InstanceChangeEvent extends Queuable {
  private List<Instance> instanceList;
  private List<String> autoScalingGroupList;
  private String appId;
}
