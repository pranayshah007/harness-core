// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package converter

import (
	"fmt"
	"github.com/drone/go-scm/scm"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// ConvertIssueCommentHook converts scm.IssueCommentHook to protobuf object
func ConvertIssueCommentHook(h *scm.IssueCommentHook) (*pb.IssueCommentHook, error) {
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

	issue, err := convertIssue(&h.Issue)
	if err != nil {
		return nil, err
	}

	comment, err := convertComment(&h.Comment)
	if err != nil {
		return nil, err
	}

	return &pb.IssueCommentHook{
		Action:  convertAction(h.Action),
		Repo:    repo,
		Comment: comment,
		Issue:   issue,
		Sender:  sender,
	}, nil
}

// ConvertPullRequestCommentHook converts scm.PullRequestCommentHook to protobuf object
func ConvertPullRequestCommentHook(h *scm.PullRequestCommentHook) (*pb.IssueCommentHook, error) {
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

	issue, err := convertPRToIssue(&h.PullRequest)
	if err != nil {
		return nil, err
	}

	comment, err := convertComment(&h.Comment)
	if err != nil {
		return nil, err
	}

	return &pb.IssueCommentHook{
		Action:  convertAction(h.Action),
		Repo:    repo,
		Comment: comment,
		Issue:   issue,
		Sender:  sender,
	}, nil
}

// convertComment converts scm.comment to protobuf object
func convertComment(c *scm.Comment) (*pb.Comment, error) {
	user, err := convertUser(&c.Author)
	if err != nil {
		return nil, err
	}

	createTS := timestamppb.New(c.Created)
	updateTS := timestamppb.New(c.Updated)

	return &pb.Comment{
		Id:      int32(c.ID),
		Body:    c.Body,
		User:    user,
		Created: createTS,
		Updated: updateTS,
	}, nil
}

// convertIssue converts scm.issue to protobuf object
func convertIssue(i *scm.Issue) (*pb.Issue, error) {
	user, err := convertUser(&i.Author)
	if err != nil {
		return nil, err
	}

	createTS := timestamppb.New(i.Created)
	updateTS := timestamppb.New(i.Updated)

	pr, err := ConvertPR(&i.PullRequest)
	if err != nil {
		return nil, err
	}

	var labels []string

	labels = append(labels, i.Labels...)

	return &pb.Issue{
		Number:  int32(i.Number),
		Title:   i.Title,
		Body:    i.Body,
		Link:    i.Link,
		Labels:  labels,
		Closed:  i.Closed,
		Locked:  i.Locked,
		User:    user,
		Created: createTS,
		Updated: updateTS,
		Pr:      pr,
	}, nil
}

// convertPRToIssue converts scm.PullRequest to protobuf object
func convertPRToIssue(i *scm.PullRequest) (*pb.Issue, error) {
	user, err := convertUser(&i.Author)
	if err != nil {
		return nil, fmt.Errorf("error converting user: %w", err)
	}

	createTS := timestamppb.New(i.Created)
	updateTS := timestamppb.New(i.Updated)

	pr, err := ConvertPR(i)
	if err != nil {
		return nil, fmt.Errorf("error converting PR: %w", err)
	}

	return &pb.Issue{
		Number:  int32(i.Number),
		Title:   i.Title,
		Body:    i.Body,
		Link:    i.Link,
		Closed:  i.Closed,
		User:    user,
		Created: createTS,
		Updated: updateTS,
		Pr:      pr,
	}, nil
}
