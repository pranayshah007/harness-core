/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.graphql;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.app.MainConfiguration;
import software.wings.beans.User;
import software.wings.exception.WingsExceptionMapper;
import software.wings.features.RestApiFeature;
import software.wings.features.api.PremiumFeature;
import software.wings.graphql.datafetcher.DataLoaderRegistryHelper;
import software.wings.graphql.provider.GraphQLProvider;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserRestrictionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.ApiKeyService;
import software.wings.utils.ResourceTestRule;

import com.google.inject.name.Named;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.dataloader.DataLoaderRegistry;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class GraphQLResourceTest extends CategoryTest {
  private static final GraphQLProvider GRAPH_QL_PROVIDER = mockGraphQLProvider();
  private static final FeatureFlagService FEATURE_FLAG_SERVICE = mock(FeatureFlagService.class);
  private static final ApiKeyService API_KEY_SERVICE = mock(ApiKeyService.class);
  private static final DataLoaderRegistryHelper DATA_LOADER_REGISTRY_HELPER = mock(DataLoaderRegistryHelper.class);
  private static final GraphQLUtils GRAPH_QL_UTILS = mock(GraphQLUtils.class);
  @Named(RestApiFeature.FEATURE_NAME) private static final PremiumFeature PREMIUM_FEATURE = mock(PremiumFeature.class);
  private static HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
  private static final MainConfiguration MAIN_CONFIGURATION = mock(MainConfiguration.class);

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String USER_ID = "USER_ID";
  private static final String USERNAME = "USERNAME";
  private static final String QUERY = "QUERY";
  private static final String INCORRECT_QUERY = "INCORRECT_QUERY";

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new GraphQLResource(GRAPH_QL_PROVIDER, FEATURE_FLAG_SERVICE, API_KEY_SERVICE,
              DATA_LOADER_REGISTRY_HELPER, PREMIUM_FEATURE, GRAPH_QL_UTILS, MAIN_CONFIGURATION))
          .instance(new AbstractBinder() {
            @Override
            protected void configure() {
              bind(httpServletRequest).to(HttpServletRequest.class);
            }
          })
          .type(WingsExceptionMapper.class)
          .type(MultiPartFeature.class)
          .build();

  @Before
  public void setUp() throws IOException {
    UserThreadLocal.set(mockUser(true));
    when(DATA_LOADER_REGISTRY_HELPER.getDataLoaderRegistry()).thenReturn(new DataLoaderRegistry());
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldExecuteExternalWithoutExceptions() {
    when(FEATURE_FLAG_SERVICE.isEnabled(FeatureName.GRAPHQL_DEV, ACCOUNT_ID)).thenReturn(true);
    when(PREMIUM_FEATURE.isAvailableForAccount(ACCOUNT_ID)).thenReturn(true);
    // Media type: Plain text
    Response response = RESOURCES.client()
                            .target(format("/graphql?accountId=%s", ACCOUNT_ID))
                            .request()
                            .post(entity(QUERY, MediaType.TEXT_PLAIN));
    assertThat(response.getStatus()).isEqualTo(204);
    // Media type: Application JSON
    GraphQLQuery graphQLQuery = new GraphQLQuery();
    graphQLQuery.setQuery(QUERY);
    response = RESOURCES.client()
                   .target(format("/graphql?accountId=%s", ACCOUNT_ID))
                   .request()
                   .post(entity(graphQLQuery, MediaType.APPLICATION_JSON));
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldExecuteInternalWithoutExceptions() {
    when(FEATURE_FLAG_SERVICE.isEnabled(FeatureName.GRAPHQL_DEV, ACCOUNT_ID)).thenReturn(true);
    when(PREMIUM_FEATURE.isAvailableForAccount(ACCOUNT_ID)).thenReturn(true);
    // Media type: Plain text
    Response response = RESOURCES.client()
                            .target(format("/graphql/int?accountId=%s", ACCOUNT_ID))
                            .request()
                            .post(entity(QUERY, MediaType.TEXT_PLAIN));
    // Media type: Application JSON
    GraphQLQuery graphQLQuery = new GraphQLQuery();
    graphQLQuery.setQuery(QUERY);
    assertThat(response.getStatus()).isEqualTo(204);
    response = RESOURCES.client()
                   .target(format("/graphql/int?accountId=%s", ACCOUNT_ID))
                   .request()
                   .post(entity(graphQLQuery, MediaType.APPLICATION_JSON));
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldThrowUnauthorizedExceptionForIncorrectQuery() {
    when(FEATURE_FLAG_SERVICE.isEnabled(FeatureName.GRAPHQL_DEV, ACCOUNT_ID)).thenReturn(true);
    when(PREMIUM_FEATURE.isAvailableForAccount(ACCOUNT_ID)).thenReturn(true);
    GraphQLUtils graphQLUtils = new GraphQLUtils();
    when(GRAPH_QL_UTILS.getUnauthorizedException()).thenReturn(graphQLUtils.getUnauthorizedException());
    Response response = RESOURCES.client()
                            .target(format("/graphql?accountId=%s", ACCOUNT_ID))
                            .request()
                            .post(entity(INCORRECT_QUERY, MediaType.TEXT_PLAIN));
    assertThat(response.getStatus()).isEqualTo(401);
  }

  private User mockUser(boolean harnessSupportUser) {
    User dummyUser = new User();
    dummyUser.setUuid(USER_ID);
    dummyUser.setName(USERNAME);
    dummyUser.setUserRequestContext(mockUserRequestContext(harnessSupportUser));
    return dummyUser;
  }

  private UserRequestContext mockUserRequestContext(boolean harnessSupportUser) {
    return UserRequestContext.builder()
        .accountId(ACCOUNT_ID)
        .harnessSupportUser(harnessSupportUser)
        .userPermissionInfo(UserPermissionInfo.builder().accountId(ACCOUNT_ID).build())
        .userRestrictionInfo(UserRestrictionInfo.builder().build())
        .build();
  }

  private static GraphQLProvider mockGraphQLProvider() {
    GraphQLProvider graphQLProvider = mock(GraphQLProvider.class);
    GraphQL graphQL = mock(GraphQL.class);
    when(graphQLProvider.getPrivateGraphQL()).thenReturn(graphQL);
    when(graphQLProvider.getPublicGraphQL()).thenReturn(graphQL);
    when(graphQL.execute(any(ExecutionInput.class))).thenReturn(new ExecutionResult() {
      @Override
      public List<GraphQLError> getErrors() {
        return null;
      }

      @Override
      public <T> T getData() {
        return null;
      }

      @Override
      public Map<Object, Object> getExtensions() {
        return null;
      }

      @Override
      public Map<String, Object> toSpecification() {
        return null;
      }
    });
    return graphQLProvider;
  }
}
