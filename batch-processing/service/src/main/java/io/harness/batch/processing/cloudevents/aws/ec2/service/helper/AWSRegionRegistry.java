/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ec2.service.helper;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AWSRegionRegistry {
  private final String DEFAULT_REGION = "us-east-1";
  static final Map<String, String> regionMap = ImmutableMap.<String, String>builder()
                                                   .put("AWS GovCloud (US)", "us-gov-west-1")
                                                   .put("AWS GovCloud (US-East)", "us-gov-east-1")
                                                   .put("US East (N. Virginia)", "us-east-1")
                                                   .put("US East (Ohio)", "us-east-2")
                                                   .put("US West (N. California)", "us-west-1")
                                                   .put("US West (Oregon)", "us-west-2")
                                                   .put("EU (Ireland)", "eu-west-1")
                                                   .put("EU (London)", "eu-west-2")
                                                   .put("EU (Paris)", "eu-west-3")
                                                   .put("EU (Frankfurt)", "eu-central-1")
                                                   .put("EU (Stockholm)", "eu-north-1")
                                                   .put("EU (Milan)", "eu-south-1")
                                                   .put("Asia Pacific (Hong Kong)", "ap-east-1")
                                                   .put("Asia Pacific (Mumbai)", "ap-south-1")
                                                   .put("Asia Pacific (Singapore)", "ap-southeast-1")
                                                   .put("Asia Pacific (Sydney)", "ap-southeast-2")
                                                   .put("Asia Pacific (Jakarta)", "ap-southeast-3")
                                                   .put("Asia Pacific (Tokyo)", "ap-northeast-1")
                                                   .put("Asia Pacific (Seoul)", "ap-northeast-2")
                                                   .put("Asia Pacific (Osaka)", "ap-northeast-3")
                                                   .put("South America (Sao Paulo)", "sa-east-1")
                                                   .put("China (Beijing)", "cn-north-1")
                                                   .put("China (Ningxia)", "cn-northwest-1")
                                                   .put("Canada (Central)", "ca-central-1")
                                                   .put("Middle East (Bahrain)", "me-south-1")
                                                   .put("Africa (Cape Town)", "af-south-1")
                                                   .put("US ISO East", "us-iso-east-1")
                                                   .put("US ISOB East (Ohio)", "us-isob-east-1")
                                                   .put("US ISO West", "us-iso-west-1")
                                                   .build();

  public String getRegionNameFromDisplayName(String displayName) {
    if (regionMap.containsKey(displayName)) {
      return regionMap.get(displayName);
    }
    return DEFAULT_REGION;
  }
}
