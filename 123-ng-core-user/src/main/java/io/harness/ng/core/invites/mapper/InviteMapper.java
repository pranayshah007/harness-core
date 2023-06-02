/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.invites.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.invites.dto.CreateInviteDTO;
import io.harness.ng.core.invites.dto.InviteDTO;
import io.harness.ng.core.invites.entities.Invite;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.validator.routines.EmailValidator;

@UtilityClass
@OwnedBy(HarnessTeam.PL)
public class InviteMapper {
  public static InviteDTO writeDTO(Invite invite) {
    if (invite == null) {
      return null;
    }
    return InviteDTO.builder()
        .id(invite.getId())
        .name(invite.getName())
        .email(invite.getEmail())
        .accountIdentifier(invite.getAccountIdentifier())
        .orgIdentifier(invite.getOrgIdentifier())
        .projectIdentifier(invite.getProjectIdentifier())
        .roleBindings(invite.getRoleBindings())
        .userGroups(invite.getUserGroups())
        .inviteType(invite.getInviteType())
        .approved(invite.getApproved())
        .build();
  }

  public static Invite toInvite(InviteDTO inviteDTO, NGAccess ngAccess) {
    if (inviteDTO == null) {
      return null;
    }
    return Invite.builder()
        .id(inviteDTO.getId())
        .name(inviteDTO.getName())
        .email(inviteDTO.getEmail())
        .roleBindings(inviteDTO.getRoleBindings() == null ? new ArrayList<>() : inviteDTO.getRoleBindings())
        .userGroups(inviteDTO.getUserGroups())
        .inviteType(inviteDTO.getInviteType())
        .accountIdentifier(ngAccess.getAccountIdentifier())
        .orgIdentifier(ngAccess.getOrgIdentifier())
        .projectIdentifier(ngAccess.getProjectIdentifier())
        .approved(inviteDTO.getApproved())
        .build();
  }

  public static List<Invite> toInviteList(
      CreateInviteDTO createInviteDTO, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(createInviteDTO.getUsers())) {
      return new ArrayList<>();
    }

    EmailValidator emailValidator = EmailValidator.getInstance();
    List<String> emailIdList = createInviteDTO.getUsers();
    List<Invite> invites = new ArrayList<>();
    for (String emailId : emailIdList) {
      if (emailValidator.isValid(emailId)) {
        invites.add(Invite.builder()
                        .email(emailId)
                        .roleBindings(createInviteDTO.getRoleBindings())
                        .userGroups(createInviteDTO.getUserGroups())
                        .inviteType(createInviteDTO.getInviteType())
                        .approved(Boolean.FALSE)
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build());
      } else {
        throw new InvalidRequestException(
            "Unable to add the user as the email address is invalid. Please enter a valid email address.");
      }
    }
    return invites;
  }
}
