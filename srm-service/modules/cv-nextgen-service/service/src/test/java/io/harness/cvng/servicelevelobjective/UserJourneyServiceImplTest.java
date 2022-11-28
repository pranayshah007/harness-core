/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective;

import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.UserJourneyDTO;
import io.harness.cvng.servicelevelobjective.beans.UserJourneyResponse;
import io.harness.cvng.servicelevelobjective.services.api.UserJourneyService;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UserJourneyServiceImplTest extends CvNextGenTestBase {
  @Inject UserJourneyService userJourneyService;
  String identifier;
  String name;
  ProjectParams projectParams;
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();

    identifier = "user-journey";
    name = "user-journey";
    projectParams = ProjectParams.builder()
                        .accountIdentifier(builderFactory.getContext().getAccountId())
                        .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                        .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                        .build();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_Success() {
    UserJourneyDTO userJourneyDTO = createUserJourneyBuilder();
    UserJourneyResponse userJourneyResponse = userJourneyService.create(projectParams, userJourneyDTO);
    assertThat(userJourneyResponse.getUserJourneyDTO()).isEqualTo(userJourneyDTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetAll_Success() {
    UserJourneyDTO userJourneyDTO = createUserJourneyBuilder();
    userJourneyService.create(projectParams, userJourneyDTO);
    PageResponse<UserJourneyResponse> userJourneyPageResponse =
        userJourneyService.getUserJourneys(projectParams, 0, 10);
    assertThat(userJourneyPageResponse.getContent().size()).isEqualTo(1);
    assertThat(userJourneyPageResponse.getContent().get(0).getUserJourneyDTO()).isEqualTo(userJourneyDTO);
  }

  private UserJourneyDTO createUserJourneyBuilder() {
    return builderFactory.getUserJourneyDTOBuilder();
  }
}
