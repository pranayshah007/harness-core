// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Package stream defines the live log streaming interface.
package stream

import (
	"context"
	"errors"
	"io"
	"time"
)

// ErrNotFound is returned when a stream is not registered
// with the Streamer.
var ErrNotFound = errors.New("stream: not found")

// Stream defines the live log streaming interface.
type Stream interface {
	// Create creates the log stream for the string key.
	Create(context.Context, string) error

	// Delete deletes the log stream for the string key.
	Delete(context.Context, string) error

	// Ping pings the stream backend to see it it's available.
	Ping(context.Context) error

	// Write writes to the log stream.
	// TODO(bradrydzewski) change *Line to a proper slice.
	Write(context.Context, string, ...*Line) error

	// Tail tails the log stream.
	Tail(context.Context, string) (<-chan *Line, <-chan error)

	// Info returns internal stream information.
	Info(context.Context) *Info

	// CopyTo copies the contents of the stream to the writer
	CopyTo(ctx context.Context, key string, rc io.WriteCloser) error

	// Exists checks whether the key is present in the stream or not.
	Exists(ctx context.Context, key string) error

	// ListPrefix returns a list of keys starting with the given prefix in the stream.
	ListPrefix(ctx context.Context, prefix string, scanBatch int64) ([]string, error)
}

// Line represents a line in the logs.
type Line struct {
	Level     string            `json:"level"`
	Number    int               `json:"pos"`
	Message   string            `json:"out"`
	Timestamp time.Time         `json:"time"`
	Args      map[string]string `json:"args"`
}

// Info provides internal stream information. This can be
// used to monitor the number of registered streams and
// subscribers.
type Info struct {
	// Streams is a key-value pair mapping the unique key to
	// the count of subscribers
	Streams map[string]Stats `json:"streams"`
}

// Stats provides statistics about an individual stream,
// including the size of the stream, the number of
// subscribers and the TTL. These values will be -1 if
// not set.
type Stats struct {
	Size int    `json:"size"`
	Subs int    `json:"subscribers"`
	TTL  string `json:"ttl"` // Unix time
}
