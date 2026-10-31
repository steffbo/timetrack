package migrate

import (
	"fmt"
	"os"
	"strings"
	"testing"
)

func TestChecksumNormalization(t *testing.T) {
	// CRC32 of the concatenated UTF-8 lines; fixed standard CRC32 reference vector.
	for _, s := range []string{"123456789", "1234\n56789\n", "\ufeff1234\r\n56789", "1234\r56789"} {
		if got := Checksum([]byte(s)); got != -873187034 {
			t.Fatalf("%q: %d", s, got)
		}
	}
}

func TestChecksumsAgainstRecordedDatabase(t *testing.T) {
	// Recorded checksums from a PostgreSQL 17 database using the established migration format.
	b, err := os.ReadFile("testdata/migration-checksums.txt")
	if err != nil {
		t.Fatal(err)
	}
	migrations, err := Migrations()
	if err != nil {
		t.Fatal(err)
	}
	var actual strings.Builder
	for _, m := range migrations {
		fmt.Fprintf(&actual, "%d|%d\n", m.Version, m.Checksum)
	}
	if actual.String() != string(b) {
		t.Fatalf("Migration checksums differ:\n%s", actual.String())
	}
}
