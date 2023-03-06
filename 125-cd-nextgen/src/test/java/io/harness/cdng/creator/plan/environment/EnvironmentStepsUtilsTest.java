/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.environment;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.steps.constants.ArtifactsStepV2Constants;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.environment.helper.EnvironmentStepsUtils;
import io.harness.exception.WingsException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EnvironmentStepsUtilsTest extends CategoryTest {
  private static final ResourceScope ACCOUNT_RESOURCE_SCOPE = ResourceScope.of("accountId", null, null);
  private static final ResourceScope ORG_RESOURCE_SCOPE = ResourceScope.of("accountId", "orgId", null);
  private static final ResourceScope PROJECT_RESOURCE_SCOPE = ResourceScope.of("accountId", "orgId", "projectId");
  private final Ambiance ambiance = buildAmbiance();

  @Mock private AccessControlClient accessControlClient;
  private AutoCloseable mocks;
  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);

    // deny by default
    doReturn(false).when(accessControlClient).hasAccess(any(), any(), any());
    doReturn(false).when(accessControlClient).hasAccess(any(), any(), any(), any());

    doThrow(new NGAccessDeniedException("access denied", WingsException.USER, List.of()))
        .when(accessControlClient)
        .checkForAccessOrThrow(any());

    doThrow(new NGAccessDeniedException("access denied", WingsException.USER, List.of()))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any());

    doThrow(new NGAccessDeniedException("access denied", WingsException.USER, List.of()))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());

    doThrow(new NGAccessDeniedException("access denied", WingsException.USER, List.of()))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any(), any());

    doThrow(new NGAccessDeniedException("access denied", WingsException.USER, List.of()))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any(), any(), any());

    doReturn(AccessCheckResponseDTO.builder()
                 .accessControlList(List.of(AccessControlDTO.builder().permitted(false).build()))
                 .build())
        .when(accessControlClient)
        .checkForAccess(any());
    doReturn(AccessCheckResponseDTO.builder()
                 .accessControlList(List.of(AccessControlDTO.builder().permitted(false).build()))
                 .build())
        .when(accessControlClient)
        .checkForAccess(any(), any());
    doReturn(AccessCheckResponseDTO.builder()
                 .accessControlList(List.of(AccessControlDTO.builder().permitted(false).build()))
                 .build())
        .when(accessControlClient)
        .checkForAccess(any(), any());
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void checkForEnvAccessOrThrow_allow_projectScope() {
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(Principal.class),
            eq(ResourceScope.builder()
                    .accountIdentifier("accountId")
                    .orgIdentifier("orgId")
                    .projectIdentifier("projectId")
                    .build()),
            eq(Resource.of("ENVIRONMENT", "my_env")), eq(CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION),
            anyString());
    EnvironmentStepsUtils.checkForEnvAccessOrThrow(
        accessControlClient, ambiance, ParameterField.createValueField("my_env"));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void checkForEnvAccessOrThrow_allow_orgScope() {
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(Principal.class),
            eq(ResourceScope.builder().accountIdentifier("accountId").orgIdentifier("orgId").build()),
            eq(Resource.of("ENVIRONMENT", "my_env")), eq(CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION),
            anyString());
    EnvironmentStepsUtils.checkForEnvAccessOrThrow(
        accessControlClient, ambiance, ParameterField.createValueField("org.my_env"));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void checkForEnvAccessOrThrow_allow_accountScope() {
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(Principal.class), eq(ResourceScope.builder().accountIdentifier("accountId").build()),
            eq(Resource.of("ENVIRONMENT", "my_env")), eq(CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION),
            anyString());
    EnvironmentStepsUtils.checkForEnvAccessOrThrow(
        accessControlClient, ambiance, ParameterField.createValueField("account.my_env"));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void checkForEnvAccessOrThrow_denied() {
    assertThatExceptionOfType(NGAccessDeniedException.class)
        .isThrownBy(()
                        -> EnvironmentStepsUtils.checkForEnvAccessOrThrow(
                            accessControlClient, ambiance, ParameterField.createValueField("my_env")))
        .withMessageContaining("access denied");

    assertThatExceptionOfType(NGAccessDeniedException.class)
        .isThrownBy(()
                        -> EnvironmentStepsUtils.checkForEnvAccessOrThrow(
                            accessControlClient, ambiance, ParameterField.createValueField("org.my_env")))
        .withMessageContaining("access denied");

    assertThatExceptionOfType(NGAccessDeniedException.class)
        .isThrownBy(()
                        -> EnvironmentStepsUtils.checkForEnvAccessOrThrow(
                            accessControlClient, ambiance, ParameterField.createValueField("account.my_env")))
        .withMessageContaining("access denied");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void checkForEnvsAccessOrThrow_emptyList() {
    EnvironmentStepsUtils.checkForAllEnvsAccessOrThrow(accessControlClient, ambiance, List.of());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void checkForEnvsAccessOrThrow_allowed_sameScope() {
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    doReturn(
        generateAccessCheckResponse(List.of("e1", "e2"), List.of(Boolean.TRUE, Boolean.TRUE), ACCOUNT_RESOURCE_SCOPE))
        .when(accessControlClient)
        .checkForAccess(any(Principal.class), anyList());

    EnvironmentStepsUtils.checkForAllEnvsAccessOrThrow(
        accessControlClient, ambiance, generateEnvironments("account", Set.of("e1", "e2")));
    verify(accessControlClient, times(1)).checkForAccess(any(Principal.class), captor.capture());

    List<PermissionCheckDTO> requestedPermissionsChecks =
        (List<PermissionCheckDTO>) captor.getAllValues().stream().flatMap(List::stream).collect(Collectors.toList());

    assertThat(requestedPermissionsChecks).hasSize(2);
    assertThat(
        requestedPermissionsChecks.stream().map(PermissionCheckDTO::getResourceScope).collect(Collectors.toList()))
        .containsExactly(ACCOUNT_RESOURCE_SCOPE, ACCOUNT_RESOURCE_SCOPE);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void checkForEnvsAccessOrThrow_partial_allowed_sameScope() {
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    doReturn(
        generateAccessCheckResponse(List.of("e1", "e2"), List.of(Boolean.TRUE, Boolean.FALSE), ACCOUNT_RESOURCE_SCOPE))
        .when(accessControlClient)
        .checkForAccess(any(Principal.class), anyList());

    assertThatExceptionOfType(NGAccessDeniedException.class)
        .isThrownBy(()
                        -> EnvironmentStepsUtils.checkForAllEnvsAccessOrThrow(
                            accessControlClient, ambiance, generateEnvironments("account", Set.of("e1", "e2"))))
        .withMessageContaining("Missing Access Permission for Following Environments: \n"
            + "[Scope = Account]: [e2]");
    verify(accessControlClient, times(1)).checkForAccess(any(Principal.class), captor.capture());

    List<PermissionCheckDTO> requestedPermissionsChecks =
        (List<PermissionCheckDTO>) captor.getAllValues().stream().flatMap(List::stream).collect(Collectors.toList());

    assertThat(requestedPermissionsChecks).hasSize(2);
    assertThat(
        requestedPermissionsChecks.stream().map(PermissionCheckDTO::getResourceScope).collect(Collectors.toList()))
        .containsExactly(ACCOUNT_RESOURCE_SCOPE, ACCOUNT_RESOURCE_SCOPE);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void checkForEnvsAccessOrThrow_partial_allowed_mixedScope() {
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);

    // mock response from acl client for each of the test environments
    AccessCheckResponseDTO account_response =
        generateAccessCheckResponse(List.of("e1", "e2"), List.of(Boolean.TRUE, Boolean.FALSE), ACCOUNT_RESOURCE_SCOPE);

    AccessCheckResponseDTO org_response =
        generateAccessCheckResponse(List.of("o1", "o2"), List.of(Boolean.FALSE, Boolean.TRUE), ORG_RESOURCE_SCOPE);

    AccessCheckResponseDTO project_response =
        generateAccessCheckResponse(List.of("p1", "p2"), List.of(Boolean.TRUE, Boolean.FALSE), PROJECT_RESOURCE_SCOPE);

    List<AccessControlDTO> response = new ArrayList<>(account_response.getAccessControlList());
    response.addAll(org_response.getAccessControlList());
    response.addAll(project_response.getAccessControlList());

    doReturn(AccessCheckResponseDTO.builder().accessControlList(response).build())
        .when(accessControlClient)
        .checkForAccess(any(Principal.class), anyList());

    // environments to check RBAC for
    List<Environment> acc_env = generateEnvironments("account", Set.of("e1", "e2"));
    List<Environment> org_env = generateEnvironments("org", Set.of("o1", "o2"));
    List<Environment> proj_env = generateEnvironments("project", Set.of("p1", "p2"));
    List<Environment> testEnvironments = new ArrayList<>(acc_env);
    testEnvironments.addAll(org_env);
    testEnvironments.addAll(proj_env);

    assertThatExceptionOfType(NGAccessDeniedException.class)
        .isThrownBy(
            () -> EnvironmentStepsUtils.checkForAllEnvsAccessOrThrow(accessControlClient, ambiance, testEnvironments))
        .withMessageContaining("[Scope = Account]: [e2]\n"
            + "[Scope = Organisation]: [o1]\n"
            + "[Scope = Project]: [p2]\n");
    verify(accessControlClient, times(1)).checkForAccess(any(Principal.class), captor.capture());

    List<PermissionCheckDTO> requestedPermissionsChecks =
        (List<PermissionCheckDTO>) captor.getAllValues().stream().flatMap(List::stream).collect(Collectors.toList());

    assertThat(requestedPermissionsChecks).hasSize(6);
    assertThat(
        requestedPermissionsChecks.stream().map(PermissionCheckDTO::getResourceScope).collect(Collectors.toList()))
        .containsExactly(ACCOUNT_RESOURCE_SCOPE, ACCOUNT_RESOURCE_SCOPE, ORG_RESOURCE_SCOPE, ORG_RESOURCE_SCOPE,
            PROJECT_RESOURCE_SCOPE, PROJECT_RESOURCE_SCOPE);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void checkForEnvsAccessOrThrow_allowed_mixedScope() {
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);

    // mock response from acl client for each of the test environments
    AccessCheckResponseDTO account_response =
        generateAccessCheckResponse(List.of("e1", "e2"), List.of(Boolean.TRUE, Boolean.TRUE), ACCOUNT_RESOURCE_SCOPE);

    AccessCheckResponseDTO org_response =
        generateAccessCheckResponse(List.of("o1", "o2"), List.of(Boolean.TRUE, Boolean.TRUE), ORG_RESOURCE_SCOPE);

    AccessCheckResponseDTO project_response =
        generateAccessCheckResponse(List.of("p1", "p2"), List.of(Boolean.TRUE, Boolean.TRUE), PROJECT_RESOURCE_SCOPE);

    List<AccessControlDTO> response = new ArrayList<>(account_response.getAccessControlList());
    response.addAll(org_response.getAccessControlList());
    response.addAll(project_response.getAccessControlList());

    doReturn(AccessCheckResponseDTO.builder().accessControlList(response).build())
        .when(accessControlClient)
        .checkForAccess(any(Principal.class), anyList());

    // environments to check RBAC for
    List<Environment> acc_env = generateEnvironments("account", Set.of("e1", "e2"));
    List<Environment> org_env = generateEnvironments("org", Set.of("o1", "o2"));
    List<Environment> proj_env = generateEnvironments("project", Set.of("p1", "p2"));
    List<Environment> testEnvironments = new ArrayList<>(acc_env);
    testEnvironments.addAll(org_env);
    testEnvironments.addAll(proj_env);

    EnvironmentStepsUtils.checkForAllEnvsAccessOrThrow(accessControlClient, ambiance, testEnvironments);

    verify(accessControlClient, times(1)).checkForAccess(any(Principal.class), captor.capture());

    List<PermissionCheckDTO> requestedPermissionsChecks =
        (List<PermissionCheckDTO>) captor.getAllValues().stream().flatMap(List::stream).collect(Collectors.toList());

    assertThat(requestedPermissionsChecks).hasSize(6);
    assertThat(
        requestedPermissionsChecks.stream().map(PermissionCheckDTO::getResourceScope).collect(Collectors.toList()))
        .containsExactly(ACCOUNT_RESOURCE_SCOPE, ACCOUNT_RESOURCE_SCOPE, ORG_RESOURCE_SCOPE, ORG_RESOURCE_SCOPE,
            PROJECT_RESOURCE_SCOPE, PROJECT_RESOURCE_SCOPE);
  }

  private List<Environment> generateEnvironments(String scope, Set<String> identifiers) {
    if ("account".equals(scope)) {
      return identifiers.stream()
          .map(i -> Environment.builder().accountId("accountId").identifier(i).build())
          .collect(Collectors.toList());
    } else if ("org".equals(scope)) {
      return identifiers.stream()
          .map(i -> Environment.builder().accountId("accountId").orgIdentifier("orgId").identifier(i).build())
          .collect(Collectors.toList());
    } else {
      return identifiers.stream()
          .map(i
              -> Environment.builder()
                     .accountId("accountId")
                     .orgIdentifier("orgId")
                     .projectIdentifier("projectId")
                     .identifier(i)
                     .build())
          .collect(Collectors.toList());
    }
  }

  private AccessCheckResponseDTO generateAccessCheckResponse(
      List<String> identifiers, List<Boolean> permitted, ResourceScope scope) {
    List<AccessControlDTO> accessControlDTOs = new ArrayList<>();
    for (int i = 0; i < identifiers.size(); i++) {
      accessControlDTOs.add(AccessControlDTO.builder()
                                .resourceIdentifier(identifiers.get(i))
                                .permitted(permitted.get(i))
                                .resourceScope(scope)
                                .build());
    }
    return AccessCheckResponseDTO.builder().accessControlList(accessControlDTOs).build();
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(generateUuid())
                   .setSetupId(generateUuid())
                   .setStepType(ArtifactsStepV2Constants.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .putAllSetupAbstractions(Map.of(SetupAbstractionKeys.accountId, "accountId", SetupAbstractionKeys.orgIdentifier,
            "orgId", SetupAbstractionKeys.projectIdentifier, "projectId"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                               .setPrincipal("dev")
                                               .setPrincipalType(PrincipalType.USER)
                                               .build())
                         .build())
        .build();
  }
}