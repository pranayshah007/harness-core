/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc;

import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.services.ec2.model.Instance;
import java.util.List;
import java.util.Set;

public interface AwsEC2HelperService {
  List<Instance> listEc2Instances(
      AwsCrossAccountAttributes awsCrossAccountAttributes, Set<String> instanceIds, String region);
}
