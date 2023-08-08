// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Package memory provides an in-memory log stream.
package memory

import (
	"context"
	"encoding/json"
	"io"
	"strings"
	"sync"

	"github.com/harness/harness-core/product/log-service/stream"
)

var _ stream.Stream = (*Streamer)(nil)

type Streamer struct {
	sync.Mutex

	streams map[string]*memoryStream
}

// New returns a new in-memory log streamer.
func New() *Streamer {
	return &Streamer{
		streams: make(map[string]*memoryStream),
	}
}

func (s *Streamer) Create(ctx context.Context, key string) error {
	s.Lock()
	s.streams[key] = newStream()
	s.Unlock()
	return nil
}

// Ping to an in memory stream is always successful
func (s *Streamer) Ping(ctx context.Context) error {
	return nil
}

func (s *Streamer) Delete(ctx context.Context, key string) error {
	s.Lock()
	c, ok := s.streams[key]
	if ok {
		delete(s.streams, key)
	}
	s.Unlock()
	if !ok {
		return stream.ErrNotFound
	}
	return c.close()
}

func (s *Streamer) Write(ctx context.Context, key string, line ...*stream.Line) error {
	s.Lock()
	w, ok := s.streams[key]
	s.Unlock()
	if !ok {
		return stream.ErrNotFound
	}
	return w.write(line...)
}

func (s *Streamer) Tail(ctx context.Context, key string) (<-chan *stream.Line, <-chan error) {
	s.Lock()
	stream, ok := s.streams[key]
	s.Unlock()
	if !ok {
		return nil, nil
	}
	return stream.subscribe(ctx)
}

func (s *Streamer) ListPrefix(ctx context.Context, prefix string, scanBatch int64) ([]string, error) {
	s.Lock()
	defer s.Unlock()
	keys := []string{}
	for k := range s.streams {
		if strings.HasPrefix(k, prefix) {
			keys = append(keys, k)
		}
	}
	return keys, nil
}

func (s *Streamer) CopyTo(ctx context.Context, key string, wc io.WriteCloser) error {
	defer wc.Close()
	s.Lock()
	logStream, ok := s.streams[key]
	s.Unlock()
	if !ok {
		return stream.ErrNotFound
	}
	for _, line := range logStream.hist {
		jsonBytes, _ := json.Marshal(line)
		wc.Write(jsonBytes)
		wc.Write([]byte("\n"))
	}
	return nil
}

func (s *Streamer) Info(ctx context.Context) *stream.Info {
	s.Lock()
	defer s.Unlock()
	info := &stream.Info{
		Streams: map[string]stream.Stats{},
	}
	for key, str := range s.streams {
		str.Lock()
		info.Streams[key] = stream.Stats{
			Size: len(str.hist),
			Subs: len(str.list),
			TTL:  "-1", // no TTL for memory streams
		}
		str.Unlock()
	}
	return info
}

func (s *Streamer) Exists(ctx context.Context, key string) error {
	s.Lock()
	_, ok := s.streams[key]
	s.Unlock()
	if !ok {
		return stream.ErrNotFound
	}
	return nil
}
