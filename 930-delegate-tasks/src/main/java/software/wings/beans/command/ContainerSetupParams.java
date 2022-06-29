/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import io.harness.k8s.model.ImageDetails;

import software.wings.beans.dto.ContainerTask;

import com.mongodb.DBObject;
import java.util.Objects;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.PreLoad;

@Data
@FieldNameConstants(innerTypeName = "ContainerSetupParamsKeys")
public class ContainerSetupParams {
  // The field used by Morphia to store the name of the class.
  public static final String MORPHIA_CLASS_NAME_FIELD = "className";

  // ClassName replacement for EcsContainerTask
  private static final String CONTAINER_TASK_CLASS_NAME_ECS_ORIGINAL =
      "software.wings.beans.container.EcsContainerTask";
  private static final String CONTAINER_TASK_CLASS_NAME_ECS_DTO = "software.wings.beans.dto.EcsContainerTask";

  // ClassName replacement for KubernetesContainerTask
  private static final String CONTAINER_TASK_CLASS_NAME_K8S_ORIGINAL =
      "software.wings.beans.container.KubernetesContainerTask";
  private static final String CONTAINER_TASK_CLASS_NAME_K8S_DTO = "software.wings.beans.dto.KubernetesContainerTask";

  private String serviceName;
  private String clusterName;
  private String appName;
  private String envName;
  private ImageDetails imageDetails;
  private ContainerTask containerTask;
  private String infraMappingId;
  private int serviceSteadyStateTimeout;

  /**
   * This function is called by Morphia after reading the raw storage entity from Mongo and before parsing it.
   *
   * This is required as we have some old persistent data in Mongo (stateExecutionInstance) that was serialized
   * with the original .container.ContainerTask classes which were replaced with the new .dto.ContainerTask classes.
   *
   * @param containerSetupParamsDBObject the raw object as read from Mongo.
   */
  @PreLoad
  public void PreLoad(final DBObject containerSetupParamsDBObject) {
    // Do nothing if there's no containerTask field
    if (containerSetupParamsDBObject == null
        || !containerSetupParamsDBObject.containsField(ContainerSetupParamsKeys.containerTask)) {
      return;
    }

    DBObject containerTaskDBObject =
        (DBObject) containerSetupParamsDBObject.get(ContainerSetupParamsKeys.containerTask);

    // Do nothing if there's no className field
    if (containerTaskDBObject == null || !containerTaskDBObject.containsField(MORPHIA_CLASS_NAME_FIELD)) {
      return;
    }

    String originalContainerTaskClassName = (String) containerTaskDBObject.get(MORPHIA_CLASS_NAME_FIELD);

    // Explicitly replace task names to avoid any unintended issues
    if (Objects.equals(originalContainerTaskClassName, CONTAINER_TASK_CLASS_NAME_ECS_ORIGINAL)) {
      containerTaskDBObject.put(MORPHIA_CLASS_NAME_FIELD, CONTAINER_TASK_CLASS_NAME_ECS_DTO);
    } else if (Objects.equals(originalContainerTaskClassName, CONTAINER_TASK_CLASS_NAME_K8S_ORIGINAL)) {
      containerTaskDBObject.put(MORPHIA_CLASS_NAME_FIELD, CONTAINER_TASK_CLASS_NAME_K8S_DTO);
    }
  }
}
