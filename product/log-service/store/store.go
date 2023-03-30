// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Package store defines the log storage interface.
package store

import (
	"context"
	"io"
	"time"
)

// Store defines the log stoage interface.
type Store interface {
	// Download downloads the blob from the datastore.
	Download(ctx context.Context, key string) (io.ReadCloser, error)

	// DownloadLink returns a pre-signed download link.
	DownloadLink(ctx context.Context, key string, expire time.Duration) (string, error)

	// Upload uploads the blob to the datastore.
	Upload(ctx context.Context, key string, r io.Reader) error

	// UploadLink returns a pre-singed upload link.
	UploadLink(ctx context.Context, key string, expire time.Duration) (string, error)

	// Delete deletes the blob from the datastore.
	Delete(ctx context.Context, key string) error

	// Ping pings the store for readiness
	Ping() error
}
