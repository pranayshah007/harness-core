// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package memory

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"sync"
	"testing"

	"github.com/harness/harness-core/product/log-service/stream"

	"github.com/google/go-cmp/cmp"
	"github.com/stretchr/testify/assert"
)

// BufioWriterCloser combines a bufio Writer with a Closer
type BufioWriterCloser struct {
	*bufio.Writer
}

func (bwc *BufioWriterCloser) Close() error {
	if err := bwc.Flush(); err != nil {
		return err
	}
	return nil
}

func TestStreamer(t *testing.T) {
	s := New()
	err := s.Create(context.Background(), "1")
	if err != nil {
		t.Error(err)
	}
	if len(s.streams) == 0 {
		t.Errorf("Want stream registered")
	}

	w := sync.WaitGroup{}
	w.Add(4)
	go func() {
		s.Write(context.Background(), "1", &stream.Line{})
		s.Write(context.Background(), "1", &stream.Line{})
		s.Write(context.Background(), "1", &stream.Line{})
		w.Done()
	}()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	tail, errc := s.Tail(ctx, "1")

	go func() {
		for {
			select {
			case <-errc:
				return
			case <-ctx.Done():
				return
			case <-tail:
				w.Done()
			}
		}
	}()

	w.Wait()
}

func TestStreamerDelete(t *testing.T) {
	s := New()
	err := s.Create(context.Background(), "1")
	if err != nil {
		t.Error(err)
	}
	if len(s.streams) == 0 {
		t.Errorf("Want stream registered")
	}
	err = s.Delete(context.Background(), "1")
	if err != nil {
		t.Error(err)
	}
	if len(s.streams) != 0 {
		t.Errorf("Want stream unregistered")
	}
}

func TestStreamerDeleteErr(t *testing.T) {
	s := New()
	err := s.Delete(context.Background(), "1")
	if err != stream.ErrNotFound {
		t.Errorf("Want errStreamNotFound")
	}
}

func TestStreamerWriteErr(t *testing.T) {
	s := New()
	err := s.Write(context.Background(), "1", &stream.Line{})
	if err != stream.ErrNotFound {
		t.Errorf("Want errStreamNotFound")
	}
}

func TestStreamTailNotFound(t *testing.T) {
	s := New()
	outc, errc := s.Tail(context.Background(), "0")
	if outc != nil && errc != nil {
		t.Errorf("Expect nil channel when stream not found")
	}
}

func TestStreamCopy(t *testing.T) {
	s := New()
	var err error

	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	bytes1, _ := json.Marshal(&line1)
	bytes1 = append(bytes1, []byte("\n")...)
	bytes2, _ := json.Marshal(&line2)
	bytes2 = append(bytes2, []byte("\n")...)

	err = s.Create(context.Background(), "1")
	assert.Nil(t, err)
	err = s.Write(context.Background(), "1", line1)
	assert.Nil(t, err)
	err = s.Write(context.Background(), "1", line2)
	assert.Nil(t, err)
	w := new(bytes.Buffer)
	bwc := BufioWriterCloser{bufio.NewWriter(w)}
	err = s.CopyTo(context.Background(), "1", &bwc)
	assert.Nil(t, err)
	assert.Equal(t, w.Bytes(), append(bytes1, bytes2...))
}

func TestStreamerInfo(t *testing.T) {
	s := New()
	s.streams["1"] = &memoryStream{list: map[*subscriber]struct{}{{}: {}, {}: {}}}
	s.streams["2"] = &memoryStream{list: map[*subscriber]struct{}{{}: {}}}
	s.streams["3"] = &memoryStream{list: map[*subscriber]struct{}{}}
	got := s.Info(context.Background())

	want := &stream.Info{
		Streams: map[string]stream.Stats{
			"1": {Subs: 2, TTL: "-1"},
			"2": {Subs: 1, TTL: "-1"},
			"3": {Subs: 0, TTL: "-1"},
		},
	}

	if diff := cmp.Diff(got, want); diff != "" {
		t.Errorf(diff)
	}
}

func TestStreamKeyExists(t *testing.T) {
	ctx := context.Background()
	s := New()
	key := "key"
	err := s.Create(ctx, key)
	if err != nil {
		t.Error(err)
	}
	err = s.Exists(ctx, key)
	if err != nil {
		t.Error(err)
	}
	err = s.Delete(ctx, key)
	if err != nil {
		t.Error(err)
	}
	err = s.Exists(ctx, key)
	if err == nil {
		t.Error(err)
	}
}

func TestStreamListPrefixes(t *testing.T) {
	ctx := context.Background()
	s := New()
	key1 := "key1"
	key2 := "key2"
	key3 := "differentKey"
	err := s.Create(ctx, key1)
	assert.Nil(t, err)
	err = s.Create(ctx, key2)
	assert.Nil(t, err)
	err = s.Create(ctx, key3)
	assert.Nil(t, err)
	l, err := s.ListPrefix(ctx, "key", 1)
	assert.Nil(t, err)
	assert.Contains(t, l, "key1")
	assert.Contains(t, l, "key2")
	assert.Equal(t, len(l), 2)
}
