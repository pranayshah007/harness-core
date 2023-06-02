// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// This code has been adapted from Drone
// https://github.com/drone/runner-go/blob/master/livelog/livelog.go
package logs

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"sync"
	"time"

	"go.uber.org/zap"

	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/log-service/client"
	"github.com/harness/harness-core/product/log-service/stream"
)

const (
	defaultLimit    = 5242880 // 5MB
	maxLineLimit    = 71680   // 70KB
	defaultInterval = 1 * time.Second
	defaultLevel    = "info"
	messageKey      = "msg"
	levelKey        = "level"
)

// RemoteWriter is an io.Writer that sends logs to the server.
type RemoteWriter struct {
	sync.Mutex

	client client.Client // client

	key string // Unique key to identify in storage

	num    int
	now    time.Time
	size   int
	limit  int
	opened bool // whether the stream has been successfully opened
	nudges []logs.Nudge
	errs   []error

	interval time.Duration
	pending  []*stream.Line
	history  []*stream.Line
	prev     []byte

	closed bool
	close  chan struct{}
	ready  chan struct{}

	indirectUpload bool
	log            *zap.SugaredLogger
}

// NewWriter returns a new writer
// if indirectUpload is true, logs go through log service instead of using an uploadable link.
func NewRemoteWriter(client client.Client, key string, nudges []logs.Nudge, indirectUpload bool) (*RemoteWriter, error) {
	l, err := zap.NewProduction()
	if err != nil {
		return &RemoteWriter{}, err
	}
	defer l.Sync()
	b := &RemoteWriter{
		client:         client,
		key:            key,
		now:            time.Now(),
		limit:          defaultLimit,
		interval:       defaultInterval,
		nudges:         nudges,
		close:          make(chan struct{}),
		ready:          make(chan struct{}, 1),
		log:            l.Sugar(),
		indirectUpload: indirectUpload,
	}
	go b.Start()
	return b, nil
}

// SetLimit sets the Writer limit.
func (b *RemoteWriter) SetLimit(limit int) {
	b.limit = limit
}

// SetInterval sets the Writer flusher interval.
func (b *RemoteWriter) SetInterval(interval time.Duration) {
	b.interval = interval
}

// Convert converts a byte slice to a line format used by the log service
func (b *RemoteWriter) Convert(p string) *stream.Line {
	var jsonMap map[string]interface{}
	args := make(map[string]string)
	err := json.Unmarshal([]byte(p), &jsonMap)
	msg, ok1 := jsonMap[messageKey]
	level, ok2 := jsonMap[levelKey]
	if err != nil || !ok1 || !ok2 {
		// If the message is not in JSON, just use the bytes as the `Message` field
		return &stream.Line{
			Level:     defaultLevel,
			Message:   truncate(p, maxLineLimit),
			Number:    b.num,
			Timestamp: time.Now(),
			Args:      args,
		}
	}
	// Parse all the arguments into Args
	for k, v := range jsonMap {
		args[k] = fmt.Sprintf("%v", v)
	}
	return &stream.Line{
		Level:     fmt.Sprintf("%v", level),
		Message:   truncate(fmt.Sprintf("%v", msg), maxLineLimit),
		Number:    b.num,
		Timestamp: time.Now(),
		Args:      args,
	}
}

// Write uploads the live log stream to the server.
func (b *RemoteWriter) Write(p []byte) (n int, err error) {
	var res []byte
	// Return if a new line character is not present in the input.
	// Commands like `mvn` flush character by character so this prevents
	// spamming of single-character logs.
	if !bytes.Contains(p, []byte("\n")) {
		b.prev = append(b.prev, p...)
		return len(p), nil
	}

	// Contains a new line. It may actually contain multiple new line characters
	// depending on the flushing logic. We find the index of the last \n and
	// add everything before it to res. Prev becomes whatever is left over.
	// Eg: Write(A)           ---> prev is A
	//     Write(BC\nDEF\nGH) ---> res becomes ABC\nDEF\n and prev becomes GH
	first, second := splitLast(p)

	res = append(b.prev, first...)
	b.prev = second

	for _, part := range split(res) {
		if len(part) == 0 {
			continue
		}
		line := b.Convert(part)
		// Only for debugging purposes (Remove later)
		jsonLine, _ := json.Marshal(line)
		b.log.Infow(string(jsonLine))

		for b.size+len(jsonLine) > b.limit {
			// Keep streaming even after the limit, but only upload last `b.limit` data to the store
			if len(b.history) == 0 {
				break
			}

			hline, err := json.Marshal(b.history[0])
			if err != nil {
				// Log the error
				b.log.Errorw("could not marshal log", zap.Error(err))
			}
			b.size -= len(hline)
			b.history = b.history[1:]
		}

		b.size = b.size + len(jsonLine)
		b.num++

		if !b.stopped() {
			b.Lock()
			b.pending = append(b.pending, line)
			b.Unlock()
		} else {
			b.log.Infow("stream closed but still attempting to write", "key", b.key)
		}

		b.Lock()
		b.history = append(b.history, line)
		b.Unlock()
	}

	select {
	case b.ready <- struct{}{}:
	default:
	}

	return len(p), nil
}

func (b *RemoteWriter) Open() error {
	err := b.client.Open(context.Background(), b.key)
	if err != nil {
		b.log.Errorw("could not open the stream", "key", b.key, zap.Error(err))
		b.stop() // stop trying to stream if we could not open the stream
		return err
	}
	b.opened = true
	b.log.Infow("successfully opened log stream", "key", b.key)
	return nil
}

// Close closes the writer and uploads the full contents to
// the server.
func (b *RemoteWriter) Close() error {
	if b.stop() {
		// Flush anything waiting on a new line
		if len(b.prev) > 0 {
			b.Write([]byte("\n"))
		}
		b.flush()
	}
	err := b.upload()
	// Close the log stream once upload has completed. Log in case of any error

	b.log.Infow("content before closing stream on ", "key", b.key, "logs", string(b.prev))

	if errc := b.client.Close(context.Background(), b.key); errc != nil {
		b.log.Errorw("failed to close log stream", "key", b.key, zap.Error(errc))
	}
	return err
}

// upload uploads the full log history to the server.
func (b *RemoteWriter) upload() error {
	// Write history to a file and use that for upload.
	data := new(bytes.Buffer)
	l := len(b.history)
	for idx, line := range b.history {
		buf := new(bytes.Buffer)
		if err := json.NewEncoder(buf).Encode(line); err != nil {
			return err
		}
		// Only check in last 10 lines for now TODO: (Vistaar) see if this can be made better
		if l-idx <= 10 {
			// Iterate over the nudges and see if we get a match
			for _, n := range b.nudges {
				r, err := regexp.Compile(n.GetSearch())
				if err != nil {
					b.log.Errorw("error while compiling regex", zap.Error(err))
					continue
				}
				if r.Match([]byte(line.Message)) {
					b.errs = append(b.errs, formatNudge(line, n))
				}
			}
		}
		data.Write(buf.Bytes())
	}
	if b.indirectUpload {
		b.log.Infow("uploading logs through log service as indirectUpload is specified as true", "key", b.key)
		err := b.client.Upload(context.Background(), b.key, data)
		if err != nil {
			b.log.Errorw("failed to upload logs", "key", b.key, zap.Error(err))
			return err
		}
	} else {
		b.log.Infow("calling upload link", "key", b.key)
		link, err := b.client.UploadLink(context.Background(), b.key)
		if err != nil {
			b.log.Errorw("errored while trying to get upload link", zap.Error(err))
			return err
		}
		b.log.Infow("uploading logs", "key", b.key, "num_lines", len(b.history))
		err = b.client.UploadUsingLink(context.Background(), link.Value, data)
		if err != nil {
			b.log.Errorw("failed to upload using link", "key", b.key, "link", link.Value, zap.Error(err))
			return err
		}
	}
	return nil
}

// flush batch uploads all buffered logs to the server.
func (b *RemoteWriter) flush() error {
	if !b.opened {
		return nil
	}
	b.Lock()
	lines := b.copy()
	b.clear()
	b.Unlock()
	if len(lines) == 0 {
		return nil
	}
	err := b.client.Write(context.Background(), b.key, lines)
	if err != nil {
		b.log.Errorw("failed to flush lines", "key", b.key, "num_lines", len(lines), zap.Error(err))
		return err
	}
	b.log.Infow("successfully flushed lines", "key", b.key, "num_lines", len(lines))
	return nil
}

func (b *RemoteWriter) Error() error {
	if len(b.errs) == 0 {
		return nil
	}
	return b.errs[len(b.errs)-1]
}

// copy returns a copy of the buffered lines.
func (b *RemoteWriter) copy() []*stream.Line {
	return append(b.pending[:0:0], b.pending...)
}

// clear clears the buffer.
func (b *RemoteWriter) clear() {
	b.pending = b.pending[:0]
}

func (b *RemoteWriter) stop() bool {
	b.Lock()
	var closed bool
	if !b.closed {
		close(b.close)
		closed = true
		b.closed = true
	}
	b.Unlock()
	return closed
}

func (b *RemoteWriter) stopped() bool {
	b.Lock()
	closed := b.closed
	b.Unlock()
	return closed
}

// Start starts a periodic loop to flush logs to the live stream
func (b *RemoteWriter) Start() error {
	intervalTimer := time.NewTimer(b.interval)
	for {
		select {
		case <-b.close:
			return nil
		case <-b.ready:
			intervalTimer.Reset(b.interval)
			select {
			case <-b.close:
				return nil
			case <-intervalTimer.C:
				// we intentionally ignore errors. log streams
				// are ephemeral and are considered low priority
				err := b.flush()
				// Write the error to help with debugging
				if err != nil {
					b.log.Errorw("errored while trying to flush lines", "key", b.key, zap.Error(err))
				}
			}
		}
	}
}

// return back two byte arrays after splitting on last \n.
// Eg: ABC\nDEF\nGH will return ABC\nDEF\n and GH
func splitLast(p []byte) ([]byte, []byte) {
	if !bytes.Contains(p, []byte("\n")) {
		return p, []byte{} // If no \n is present, return the string itself
	}
	s := string(p)
	last := strings.LastIndex(s, "\n")
	first := s[:last+1]
	second := s[last+1:]
	return []byte(first), []byte(second)
}

// truncates a string to the given length
func truncate(inp string, to int) string {
	if len(inp) > to {
		return inp[:to] + "... (log line truncated)"
	}
	return inp
}

func split(p []byte) []string {
	s := string(p)
	v := []string{s}
	// kubernetes buffers the output and may combine
	// multiple lines into a single block of output.
	// Split into multiple lines.
	//
	// note that docker output always inclines a line
	// feed marker. This needs to be accounted for when
	// splitting the output into multiple lines.
	if strings.Contains(strings.TrimSuffix(s, "\n"), "\n") {
		v = strings.SplitAfter(s, "\n")
	}
	return v
}

func formatNudge(line *stream.Line, nudge logs.Nudge) error {
	return fmt.Errorf("Found possible error on line %d.\n Log: %s.\n Possible error: %s.\n Possible resolution: %s.",
		line.Number+1, line.Message, nudge.GetError(), nudge.GetResolution())
}
