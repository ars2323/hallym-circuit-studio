<#
  Who writes Windows' own stores during the checks (D-164): Windows' audit
  trail records, for the places below, every write, append, attribute
  change and delete of a file (event 4663) and every value set in the
  registry (event 4657), with the process that did it.  tools/windows/state.ts
  reads it back (auditedWriters) and lets a change at such a place through
  only when every process that wrote it there is Windows' own (ALLOWED's
  `writers`), never the program, its installer or its uninstaller.

  The places are Explorer's: its icon and thumbnail caches
  (%LOCALAPPDATA%\Microsoft\Windows\Explorer), the shell's versioned caches
  (%LOCALAPPDATA%\Microsoft\Windows\Caches) and its launch and session
  counters (HKCU\...\Explorer\UserAssist).  Setting an audit rule changes a
  folder's or key's security descriptor only -- not a file's size or time,
  not a value -- so the snapshots do not see it.  Run once, before the
  checks, elevated (the runner is).

    audit.ps1
#>
$ErrorActionPreference = 'Stop'

# The audit policy's subcategories, by GUID (their names are in the system's language): File System, Registry.
foreach ($sub in '{0CCE921D-69AE-11D9-BED3-505054503030}', '{0CCE921E-69AE-11D9-BED3-505054503030}') {
  & auditpol.exe /set /subcategory:$sub /success:enable | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "auditpol $sub exit $LASTEXITCODE" }
}
# Room for the whole job's events (the default Security log is 20 MB and overwrites the oldest).
& wevtutil.exe sl Security /ms:268435456 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "wevtutil sl Security exit $LASTEXITCODE" }
# state.ts refuses a window the log may not hold whole (auditCoverage): its size now, for the report.
& wevtutil.exe gl Security | Select-String 'maxSize|retention'

$everyone = New-Object Security.Principal.SecurityIdentifier('S-1-1-0')
$inherit = [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
$none = [Security.AccessControl.PropagationFlags]::None
$success = [Security.AccessControl.AuditFlags]::Success

$fileRights = [Security.AccessControl.FileSystemRights]'WriteData, AppendData, WriteExtendedAttributes, WriteAttributes, Delete, DeleteSubdirectoriesAndFiles'
foreach ($d in 'Microsoft\Windows\Explorer', 'Microsoft\Windows\Caches') {
  $path = Join-Path $env:LOCALAPPDATA $d
  if (-not (Test-Path $path)) { "audit: $path (not there)"; continue }
  $acl = Get-Acl -Path $path -Audit
  $acl.AddAuditRule((New-Object Security.AccessControl.FileSystemAuditRule($everyone, $fileRights, $inherit, $none, $success)))
  Set-Acl -Path $path -AclObject $acl
  "audit: $path ($((Get-Acl -Path $path -Audit).Audit.Count) rule(s))"
}

$keyRights = [Security.AccessControl.RegistryRights]'SetValue, CreateSubKey, Delete'
foreach ($k in 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\UserAssist') {
  $acl = Get-Acl -Path $k -Audit
  $acl.AddAuditRule((New-Object Security.AccessControl.RegistryAuditRule($everyone, $keyRights, $inherit, $none, $success)))
  Set-Acl -Path $k -AclObject $acl
  "audit: $k ($((Get-Acl -Path $k -Audit).Audit.Count) rule(s))"
}
& auditpol.exe /get /subcategory:'{0CCE921D-69AE-11D9-BED3-505054503030},{0CCE921E-69AE-11D9-BED3-505054503030}'
exit 0
