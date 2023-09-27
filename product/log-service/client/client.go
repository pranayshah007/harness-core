// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package client

import (
	"context"
	"io"
	"time"

	"github.com/harness/harness-core/product/log-service/stream"
)

// Error represents a json-encoded API error.
type Error struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

func (e *Error) Error() string {
	return e.Message
}

// Link represents a signed link.
type Link struct {
	Value   string        `json:"link"`
	Expires time.Duration `json:"expires"`
}

// Client defines a log service client.
type Client interface {
	// Upload uploads the file to remote storage.
	Upload(ctx context.Context, key string, r io.Reader) error

	// UploadLink returns a secure link that can be used to
	// upload a file to remote storage.
	UploadLink(ctx context.Context, key string) (*Link, error)

	// UploadUsingLink uses a link generated from UploadLink to upload
	// direectly to the data store.
	UploadUsingLink(ctx context.Context, link string, r io.Reader) error

	// Download downloads the file from remote storage.
	Download(ctx context.Context, key string) (io.ReadCloser, error)

	// DownloadLink returns a secure link that can be used to
	// download a file from remote storage.
	DownloadLink(ctx context.Context, key string) (*Link, error)

	// Open opens the data stream.
	Open(ctx context.Context, key string) error

	// Close closes the data stream.
	Close(ctx context.Context, key string) error

	// Write writes logs to the data stream.
	Write(ctx context.Context, key string, lines []*stream.Line) error

	// Tail tails the data stream.
	Tail(ctx context.Context, key string) (<-chan string, <-chan error)

	// Info returns the stream information.
	Info(ctx context.Context) (*stream.Info, error)

	// Healthz returns the health information.
	Healthz(ctx context.Context) error
}
