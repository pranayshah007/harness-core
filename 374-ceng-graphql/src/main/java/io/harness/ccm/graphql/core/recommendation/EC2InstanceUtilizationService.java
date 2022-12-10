/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import io.harness.ccm.commons.beans.recommendation.EC2InstanceUtilizationData;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * This service is a helper class between the API classes and DAOs.
 */
@Singleton
@Slf4j
public class EC2InstanceUtilizationService {
  @Inject private EC2RecommendationDAO ec2RecommendationDAO;

  /**
   * This method will fetch the util data from the timescale table 'utilization_data' from the instance-id.
   * @param accountIdentifier
   * @param instanceId
   * @return
   */
  @Nullable
  public List<EC2InstanceUtilizationData> getEC2InstanceUtilizationData(
      @NonNull final String accountIdentifier, @NonNull final String instanceId) {
    List<EC2InstanceUtilizationData> ec2InstanceUtilizationData =
        ec2RecommendationDAO.fetchInstanceData(accountIdentifier, instanceId);
    Collections.sort(ec2InstanceUtilizationData, Comparator.comparingLong(EC2InstanceUtilizationData::getStarttime));
    ec2InstanceUtilizationData = ec2InstanceUtilizationData.stream()
                                     .map(utilData -> {
                                       utilData.setAvgcpu(roundOffTwoDecimalPlace(utilData.getAvgcpu()));
                                       utilData.setAvgmemory(roundOffTwoDecimalPlace(utilData.getAvgmemory()));
                                       utilData.setMaxcpu(roundOffTwoDecimalPlace(utilData.getMaxcpu()));
                                       utilData.setMaxmemory(roundOffTwoDecimalPlace(utilData.getMaxmemory()));
                                       return utilData;
                                     })
                                     .collect(Collectors.toList());
    return ec2InstanceUtilizationData;
  }

  public double roundOffTwoDecimalPlace(final double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }
}
