// Package migrate applies the original SQL files using Flyway-compatible history and checksums.
package migrate

import (
	"context"
	"embed"
	"fmt"
	"hash/crc32"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

//go:embed sql/*.sql
var SQL embed.FS

type Migration struct {
	Version             int
	Description, Script string
	SQL                 []byte
	Checksum            int32
}

// Checksum follows Flyway ChecksumCalculator: UTF-8, first-line BOM removed, no line endings.
// Source: github.com/flyway/flyway/.../internal/resolver/ChecksumCalculator.java
func Checksum(b []byte) int32 {
	s := strings.TrimPrefix(string(b), "\ufeff")
	s = strings.ReplaceAll(strings.ReplaceAll(s, "\r\n", ""), "\r", "")
	s = strings.ReplaceAll(s, "\n", "")
	return int32(crc32.ChecksumIEEE([]byte(s)))
}
func Migrations() ([]Migration, error) {
	entries, err := SQL.ReadDir("sql")
	if err != nil {
		return nil, err
	}
	var out []Migration
	for _, e := range entries {
		parts := strings.SplitN(strings.TrimSuffix(e.Name(), ".sql"), "__", 2)
		if len(parts) != 2 {
			return nil, fmt.Errorf("invalid migration name: %s", e.Name())
		}
		v, err := strconv.Atoi(strings.TrimPrefix(parts[0], "V"))
		if err != nil {
			return nil, err
		}
		b, err := SQL.ReadFile("sql/" + e.Name())
		if err != nil {
			return nil, err
		}
		out = append(out, Migration{v, strings.ReplaceAll(parts[1], "_", " "), e.Name(), b, Checksum(b)})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Version < out[j].Version })
	return out, nil
}

const historyDDL = `CREATE TABLE IF NOT EXISTS flyway_schema_history (
 installed_rank INTEGER NOT NULL, version VARCHAR(50), description VARCHAR(200) NOT NULL,
 type VARCHAR(20) NOT NULL, script VARCHAR(1000) NOT NULL, checksum INTEGER,
 installed_by VARCHAR(100) NOT NULL, installed_on TIMESTAMP NOT NULL DEFAULT now(),
 execution_time INTEGER NOT NULL, success BOOLEAN NOT NULL,
 CONSTRAINT flyway_schema_history_pk PRIMARY KEY(installed_rank));
 CREATE INDEX IF NOT EXISTS flyway_schema_history_s_idx ON flyway_schema_history(success)`

func Run(ctx context.Context, p *pgxpool.Pool) error {
	conn, err := p.Acquire(ctx)
	if err != nil {
		return err
	}
	defer conn.Release()
	// Dedicated session lock spans all migration transactions, including history creation.
	if _, err = conn.Exec(ctx, `SELECT pg_advisory_lock(hashtext('timetrack.flyway_schema_history'))`); err != nil {
		return err
	}
	defer conn.Exec(context.Background(), `SELECT pg_advisory_unlock(hashtext('timetrack.flyway_schema_history'))`)
	if _, err = conn.Exec(ctx, historyDDL); err != nil {
		return err
	}
	migrations, err := Migrations()
	if err != nil {
		return err
	}
	rows, err := conn.Query(ctx, `SELECT version,type,checksum,success FROM flyway_schema_history ORDER BY installed_rank`)
	if err != nil {
		return err
	}
	applied := map[int]*int32{}
	baseline := -1
	for rows.Next() {
		var version *string
		var typ string
		var sum *int32
		var ok bool
		if err = rows.Scan(&version, &typ, &sum, &ok); err != nil {
			rows.Close()
			return err
		}
		if !ok {
			rows.Close()
			return fmt.Errorf("failed Flyway migration: %v", version)
		}
		if version == nil {
			continue
		}
		v, e := strconv.Atoi(*version)
		if e != nil {
			rows.Close()
			return fmt.Errorf("unsupported Flyway version: %s", *version)
		}
		switch typ {
		case "BASELINE":
			if v > baseline {
				baseline = v
			}
		case "SCHEMA":
			continue
		case "SQL":
			applied[v] = sum
		default:
			rows.Close()
			return fmt.Errorf("unsupported Flyway migration type: %s", typ)
		}
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return err
	}
	known := map[int]bool{}
	for _, m := range migrations {
		known[m.Version] = true
		if sum, ok := applied[m.Version]; ok && (sum == nil || *sum != m.Checksum) {
			return fmt.Errorf("Flyway checksum mismatch: %s", m.Script)
		}
	}
	for v := range applied {
		if !known[v] {
			return fmt.Errorf("applied Flyway migration missing: V%d", v)
		}
	}
	for _, m := range migrations {
		if _, ok := applied[m.Version]; ok || m.Version <= baseline {
			continue
		}
		start := time.Now()
		err = pgx.BeginFunc(ctx, conn, func(tx pgx.Tx) error {
			if _, e := tx.Exec(ctx, string(m.SQL)); e != nil {
				return e
			}
			_, e := tx.Exec(ctx, `INSERT INTO flyway_schema_history
    (installed_rank,version,description,type,script,checksum,installed_by,execution_time,success)
    SELECT COALESCE(MAX(installed_rank),0)+1,$1,$2,'SQL',$3,$4,current_user,$5,true
    FROM flyway_schema_history`, strconv.Itoa(m.Version), m.Description, m.Script, m.Checksum,
				int(time.Since(start).Milliseconds()))
			return e
		})
		if err != nil {
			return fmt.Errorf("migration %s: %w", m.Script, err)
		}
	}
	return nil
}
