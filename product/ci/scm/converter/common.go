// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package converter

import (
	"github.com/drone/go-scm/scm"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// convertAction converts scm.Action to protobuf object
func convertAction(a scm.Action) pb.Action {
	switch a {
	case scm.ActionCreate:
		return pb.Action_CREATE
	case scm.ActionEdit:
		return pb.Action_EDIT
	case scm.ActionUpdate:
		return pb.Action_UPDATE
	case scm.ActionDelete:
		return pb.Action_DELETE
	case scm.ActionOpen:
		return pb.Action_OPEN
	case scm.ActionReopen:
		return pb.Action_REOPEN
	case scm.ActionClose:
		return pb.Action_CLOSE
	case scm.ActionLabel:
		return pb.Action_LABEL
	case scm.ActionUnlabel:
		return pb.Action_UNLABEL
	case scm.ActionSync:
		return pb.Action_SYNC
	case scm.ActionMerge:
		return pb.Action_MERGE
	case scm.ActionPublish:
		return pb.Action_PUBLISH
	case scm.ActionUnpublish:
		return pb.Action_UNPUBLISH
	case scm.ActionPrerelease:
		return pb.Action_PRERELEASE
	case scm.ActionRelease:
		return pb.Action_RELEASE
	case scm.ActionUnknown:
       	return pb.Action_UNKNOWN
	case scm.ActionReviewReady:
		return pb.Action_REVIEWREADY
	default:
		return pb.Action_UNKNOWN
	}
}

// convertUser converts scm.User to protobuf object
func convertUser(u *scm.User) (*pb.User, error) {
	createTS := timestamppb.New(u.Created)
	updateTS := timestamppb.New(u.Updated)
	return &pb.User{
		Id: 	 u.ID,
		Login:   u.Login,
		Name:    u.Name,
		Email:   u.Email,
		Avatar:  u.Avatar,
		Created: createTS,
		Updated: updateTS,
	}, nil
}

// convertReference converts scm.Reference to protobuf object
func convertReference(r scm.Reference) *pb.Reference {
	return &pb.Reference{
		Name: r.Name,
		Path: r.Path,
		Sha:  r.Sha,
	}
}

// convertPerm converts *scm.Perm to protobuf object
func convertPerm(p *scm.Perm) *pb.Perm {
	if p == nil {
		return nil
	}
	return &pb.Perm{
		Pull:  p.Pull,
		Push:  p.Push,
		Admin: p.Admin,
	}
}

// convertLabel converts *scm.Label to protobuf object
func convertLabel(l scm.Label) *pb.Label {
	return &pb.Label{
		Name:  l.Name,
		Color: l.Color,
	}
}

// ConvertRepo converts scm.Repository to protobuf object
func ConvertRepo(r *scm.Repository) (*pb.Repository, error) {
	createTS := timestamppb.New(r.Created)
	updateTS := timestamppb.New(r.Updated)

	return &pb.Repository{
		Id:        r.ID,
		Namespace: r.Namespace,
		Name:      r.Name,
		Perm:      convertPerm(r.Perm),
		Branch:    r.Branch,
		Private:   r.Private,
		Clone:     r.Clone,
		CloneSsh:  r.CloneSSH,
		Link:      r.Link,
		Created:   createTS,
		Updated:   updateTS,
	}, nil
}

// ConvertRepo converts scm.Repository to protobuf object
func ConvertsAndMinimiseRepo(r *scm.Repository) (*pb.Repository, error) {
	return &pb.Repository{
		Namespace: r.Namespace,
		Name:      r.Name,
	}, nil
}

// convertSignature converts scm.Signature to protobuf object
func convertSignature(s *scm.Signature) (*pb.Signature, error) {
	date := timestamppb.New(s.Date)
	return &pb.Signature{
		Name:   s.Name,
		Email:  s.Email,
		Date:   date,
		Login:  s.Login,
		Avatar: s.Avatar,
	}, nil
}

// ConvertCommit converts scm.Commit to protobuf object
func ConvertCommit(c *scm.Commit) (*pb.Commit, error) {
	author, err := convertSignature(&c.Author)
	if err != nil {
		return nil, err
	}
	committer, err := convertSignature(&c.Committer)
	if err != nil {
		return nil, err
	}
	return &pb.Commit{
		Sha:       c.Sha,
		Message:   c.Message,
		Author:    author,
		Committer: committer,
		Link:      c.Link,
	}, nil
}
