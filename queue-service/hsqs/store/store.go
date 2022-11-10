// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

//go:generate mockgen -destination=mocks/store.go -package=mocks -source store.go

package store

import "context"

type Store interface {
	Enqueue(ctx context.Context, request EnqueueRequest) (*EnqueueResponse, error)

	Dequeue(ctx context.Context, request DequeueRequest) ([]*DequeueResponse, error)

	Ack(ctx context.Context, request AckRequest) (*AckResponse, error)

	UnAck(ctx context.Context, request UnAckRequest) (*UnAckResponse, error)

	Register(ctx context.Context, request RegisterTopicMetadata) error

	// TODO : more apis
	// CheckStatus(taskId string) Status (QUEUED | PROCESSING | FAILED | ....)
}
