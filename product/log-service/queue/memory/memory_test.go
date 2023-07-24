package memory

import (
	"context"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestQueuer_Consume(t *testing.T) {
	q := New()
	m := []string{"file1", "file2"}
	group := "group1"
	key := "key"
	zipkey := "zipkey"
	var err error
	for i := 1; i <= 3; i++ {
		err := q.Produce(context.Background(), group, key+strconv.Itoa(i), zipkey, m)
		if err != nil {
			t.Error(err)
		}
	}
	for i := 1; i <= 3; i++ {
		_, err = q.Consume(context.Background(), group, key+strconv.Itoa(i), zipkey)
	}
	assert.NoError(t, err)
	assert.Equal(t, 0, len(q.queue))
}

func TestQueuer_Produce(t *testing.T) {
	q := New()
	m := []string{"file1", "file2"}
	err := q.Produce(context.Background(), "consumerGroup", "key", "zipkey", m)
	assert.NoError(t, err)
	assert.Equal(t, 1, len(q.queue))

}
