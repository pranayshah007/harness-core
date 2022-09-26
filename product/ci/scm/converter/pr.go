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

// ConvertPRHook converts scm.PullRequestHook to protobuf object
func ConvertPRHook(h *scm.PullRequestHook) (*pb.PullRequestHook, error) {
	if h == nil {
		return nil, nil
	}

	repo, err := ConvertRepo(&h.Repo)
	if err != nil {
		return nil, err
	}
	pr, err := ConvertPR(&h.PullRequest)
	if err != nil {
		return nil, err
	}
	sender, err := convertUser(&h.Sender)
	if err != nil {
		return nil, err
	}

	return &pb.PullRequestHook{
		Action: convertAction(h.Action),
		Repo:   repo,
		Pr:     pr,
		Sender: sender,
	}, nil
}

// convertPR converts scm.PullRequest to protobuf object
func ConvertPR(pr *scm.PullRequest) (*pb.PullRequest, error) {
	author, err := convertUser(&pr.Author)
	if err != nil {
		return nil, err
	}
	createTS := timestamppb.New(pr.Created)
	updateTS := timestamppb.New(pr.Updated)

	var labels []*pb.Label
	for _, l := range pr.Labels {
		labels = append(labels, convertLabel(l))
	}
	return &pb.PullRequest{
		Number:   int64(pr.Number),
		Title:    pr.Title,
		Body:     pr.Body,
		Sha:      pr.Sha,
		Ref:      pr.Ref,
		Source:   pr.Source,
		Target:   pr.Target,
		Fork:     pr.Fork,
		Link:     pr.Link,
		Closed:   pr.Closed,
		Merged:   pr.Merged,
		Base:     convertReference(pr.Base),
		Head:     convertReference(pr.Head),
		Author:   author,
		Created:  createTS,
		Updated:  updateTS,
		Labels:   labels,
		MergeSha: pr.Merge,
	}, nil
}
