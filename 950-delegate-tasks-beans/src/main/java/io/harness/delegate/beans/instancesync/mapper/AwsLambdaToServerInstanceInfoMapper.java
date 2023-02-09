/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.mapper;

import com.google.common.collect.ImmutableSet;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.GoogleFunctionServerInstanceInfo;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunction;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionWithActiveVersions;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import software.wings.beans.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaToServerInstanceInfoMapper {
  public List<ServerInstanceInfo> toServerInstanceInfoList(
          AwsLambdaFunctionWithActiveVersions awsLambdaFunctionWithActiveVersions, String region, String infraStructureKey) {
    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();

    if (CollectionUtils.isNotEmpty(awsLambdaFunctionWithActiveVersions.getVersions())) {
      serverInstanceInfoList = awsLambdaFunctionWithActiveVersions.getVersions()
                                   .stream()
                                   .map(version
                                       -> toServerInstanceInfo(AwsLambdaFunction.from(awsLambdaFunctionWithActiveVersions, version), region, infraStructureKey))
                                   .collect(Collectors.toList());
    }
    return serverInstanceInfoList;
  }

  public ServerInstanceInfo toServerInstanceInfo(
      AwsLambdaFunction awsLambdaFunction, String region, String infraStructureKey) {
    return AwsLambdaServerInstanceInfo.builder()
        .functionName(awsLambdaFunction.getFunctionName())
        .version(awsLambdaFunction.getVersion())
            .tags(MapUtils.emptyIfNull(awsLambdaFunction.getTags())
                    .entrySet()
                    .stream()
                    .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
                    .collect(toSet()))
            .aliases(ImmutableSet.copyOf(emptyIfNull(awsLambdaFunction.getAliases())))
                    .runtime(awsLambdaFunction.getRuntime())
            .handler(awsLambdaFunction.getHandler())
        .infrastructureKey(infraStructureKey)
            .memorySize(awsLambdaFunction.getMemorySize())
            .updatedTime(awsLambdaFunction.getLastModified())
            .description(awsLambdaFunction.getDescription())
        .build();
  }
}
