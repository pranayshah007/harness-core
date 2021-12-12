package io.harness.enforcement.utils;

import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;

import io.harness.configuration.DeployVariant;
import io.harness.enforcement.beans.metadata.AvailabilityRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RateLimitRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.licensing.Edition;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SuggestionMessageUtils {
  private static final String COLON = ":";
  private static final String SEMI_COLON = ";";
  private static final String UPGRADE_PLAN = ". Plan to upgrade: ";

  private static String deployVersion = System.getenv().get(DEPLOY_VERSION);

  public String generateSuggestionMessage(String message, Edition edition,
      Map<Edition, RestrictionMetadataDTO> restrictionMetadataDTOMap, String enableDefinition) {
    if (DeployVariant.isCommunity(deployVersion)) {
      return message;
    }

    StringBuilder suggestionMessage = new StringBuilder();
    suggestionMessage.append(message).append(UPGRADE_PLAN);
    List<Edition> superiorEditions = Edition.getSuperiorEdition(edition);
    for (Edition superiorEdition : superiorEditions) {
      RestrictionMetadataDTO restrictionMetadataDTO = restrictionMetadataDTOMap.get(superiorEdition);

      if (RestrictionType.AVAILABILITY.equals(restrictionMetadataDTO.getRestrictionType())) {
        AvailabilityRestrictionMetadataDTO enableDisableRestriction =
            (AvailabilityRestrictionMetadataDTO) restrictionMetadataDTO;
        if (enableDisableRestriction.isEnabled()) {
          suggestionMessage.append(superiorEdition.name()).append(COLON).append(enableDefinition).append(SEMI_COLON);
        }
      } else if (RestrictionType.STATIC_LIMIT.equals(restrictionMetadataDTO.getRestrictionType())) {
        StaticLimitRestrictionMetadataDTO staticLimitRestrictionMetadataDTO =
            (StaticLimitRestrictionMetadataDTO) restrictionMetadataDTO;
        suggestionMessage.append(superiorEdition.name())
            .append(COLON)
            .append(staticLimitRestrictionMetadataDTO.getLimit())
            .append(SEMI_COLON);
      } else if (RestrictionType.RATE_LIMIT.equals(restrictionMetadataDTO.getRestrictionType())) {
        RateLimitRestrictionMetadataDTO rateLimitRestrictionMetadataDTO =
            (RateLimitRestrictionMetadataDTO) restrictionMetadataDTO;
        suggestionMessage.append(superiorEdition.name())
            .append(COLON)
            .append(rateLimitRestrictionMetadataDTO.getLimit())
            .append(SEMI_COLON);
      }
    }
    return suggestionMessage.toString();
  }
}
