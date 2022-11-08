/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ec2.service;

import io.harness.batch.processing.cloudevents.aws.ec2.service.request.EC2RecommendationRequest;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.EC2RecommendationResponse;

public interface AWSEC2RecommendationService {
  EC2RecommendationResponse getRecommendations(EC2RecommendationRequest request);
}
