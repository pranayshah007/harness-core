package io.harness.ccm.graphql.core.recommendation;

import io.harness.ccm.commons.beans.recommendation.EC2InstanceUtilizationData;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class EC2InstanceUtilizationService {
  @Inject private EC2RecommendationDAO ec2RecommendationDAO;

  @Nullable
  public List<EC2InstanceUtilizationData> getEC2InstanceUtilizationData(
      @NonNull final String accountIdentifier, @NonNull final String instanceId) {
    return ec2RecommendationDAO.fetchInstanceDate(accountIdentifier, instanceId);
  }
}
