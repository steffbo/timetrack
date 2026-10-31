package integration_test

// implementedC lists the operations of package C that no longer return 501.
var implementedC = map[string]bool{
	"GetTimeOffEntries": true, "CreateTimeOff": true, "UpdateTimeOff": true, "DeleteTimeOff": true,
	"GetRecurringOffDays": true, "CreateRecurringOffDay": true, "UpdateRecurringOffDay": true,
	"DeleteRecurringOffDay": true, "GetExemptions": true, "CreateExemption": true,
	"DeleteExemption": true, "GetConflictWarnings": true, "AcknowledgeWarning": true,
}
