package io.harness.ccm.graphql.core.recommendation;

import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation;
import io.harness.ccm.graphql.core.converter.EC2RecommendationDTOConverter;
import io.harness.ccm.graphql.dto.recommendation.EC2RecommendationDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.NonNull;

@Singleton
public class EC2RecommendationService {
  @Inject private EC2RecommendationDAO ec2RecommendationDAO;
  @Inject private EC2RecommendationDTOConverter dtoConverter;

  @Nullable
  public EC2RecommendationDTO getEC2RecommendationById(@NonNull final String accountIdentifier, String id) {
    final Optional<EC2Recommendation> ec2Recommendation =
        ec2RecommendationDAO.fetchEC2RecommendationById(accountIdentifier, id);

    if (!ec2Recommendation.isPresent()) {
      return EC2RecommendationDTO.builder().build();
    }
    EC2Recommendation recommendation = ec2Recommendation.get();
    return dtoConverter.convertFromEntity(recommendation);
  }
}
