/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AwsEksListClustersResponseDTO {
  enum ListClustersCommandStatus { SUCCESS, FAILURE }
  String region;
  List<String> clusters;
  String errorMessage;
  ListClustersCommandStatus status;
}
