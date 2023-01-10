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

// ConvertReleaseHook converts scm.ReleaseHook to protobuf object
func ConvertReleaseHook(h *scm.ReleaseHook) (*pb.ReleaseHook, error) {
	if h == nil {
		return nil, nil
	}

	repo, err := ConvertRepo(&h.Repo)
	if err != nil {
		return nil, err
	}

	sender, err := convertUser(&h.Sender)
	if err != nil {
		return nil, err
	}

	release, err := convertRelease(&h.Release)
	if err != nil {
		return nil, err
	}

	return &pb.ReleaseHook{
		Action:  convertAction(h.Action),
		Repo:    repo,
		Release: release,
		Sender:  sender,
	}, nil
}

// convertRelease converts scm.release to protobuf object
func convertRelease(c *scm.Release) (*pb.Release, error) {
	createTS := timestamppb.New(c.Created)
	publishTS := timestamppb.New(c.Published)
	return &pb.Release{
		Title:       c.Title,
		Description: c.Description,
		Link:        c.Link,
		Tag:         c.Tag,
		Draft:       c.Draft,
		Created:     createTS,
		Published:   publishTS,
	}, nil
}
