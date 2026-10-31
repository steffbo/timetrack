package migrate

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestCopiesMatchJava(t *testing.T) {
	originals, err := filepath.Glob("../../../backend/src/main/resources/db/migration/*.sql")
	if err != nil {
		t.Fatal(err)
	}
	migrations, err := Migrations()
	if err != nil {
		t.Fatal(err)
	}
	if len(originals) == 0 || len(originals) != len(migrations) {
		t.Fatal("migration count mismatch")
	}
	for _, p := range originals {
		b, err := os.ReadFile(p)
		if err != nil {
			t.Fatal(err)
		}
		copy, err := SQL.ReadFile("sql/" + filepath.Base(p))
		if err != nil {
			t.Fatal(err)
		}
		if !bytes.Equal(b, copy) {
			t.Errorf("migration differs: %s", p)
		}
	}
}
func TestFlywayChecksum(t *testing.T) {
	// CRC32 of the concatenated UTF-8 lines; fixed standard CRC32 reference vector.
	for _, s := range []string{"123456789", "1234\n56789\n", "\ufeff1234\r\n56789", "1234\r56789"} {
		if got := Checksum([]byte(s)); got != -873187034 {
			t.Fatalf("%q: %d", s, got)
		}
	}
}

func TestChecksumsAgainstFlywayDatabase(t *testing.T) {
	// Captured from a real PostgreSQL 17 database migrated by redgate/flyway:11-alpine (11.20.3).
	// Image digest: sha256:52cdd559dc8ae38a17b56615e3c7137d9b01470271112525b40373de470bb005.
	b, err := os.ReadFile("testdata/flyway-11.20.3-checksums.txt")
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
		t.Fatalf("Flyway checksums differ:\n%s", actual.String())
	}
}
