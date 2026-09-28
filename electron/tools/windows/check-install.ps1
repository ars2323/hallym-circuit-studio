<#
  The setup exe on a Windows PC as a student has it (N-23, D-148): install,
  install again, install over an earlier version, install over a v1.0.x
  MSI, uninstall.  Each phase prints PASS/FAIL lines to <Report>/install.txt
  and fails if any FAIL.  What changed on the PC is compared by
  tools/windows/state.ts (read-only snapshots: files under %APPDATA%,
  %LOCALAPPDATA% and the desktops, the registry keys the program or its
  installer could write).

    check-install.ps1 -Phase clean     -Setup <exe> -Version <v> -Report <dir>
        from nothing: /S, then exactly the install folder, the Start menu
        shortcut and the uninstall entry (with its install record); the
        files the program needs; no desktop shortcut, no .circ association,
        no copy of the installer; sizes and the install's time
    check-install.ps1 -Phase again     -Setup <exe> -Version <v> -Report <dir>
        the same installer over itself: still one of each
    check-install.ps1 -Phase uninstall -Report <dir>
        the uninstall entry's quiet uninstall: nothing left (against the
        snapshot before -Phase clean)
    check-install.ps1 -Phase over -Older <exe> -OlderVersion <v> -Setup <exe> -Version <v> -Report <dir>
        from nothing: the earlier version, this one over it (one folder, one
        entry, now this version, the program replaced), then uninstall
    check-install.ps1 -Phase msi -Msi <v1.0.x msi> -Setup <exe> -Version <v> -Report <dir>
        from nothing: the v1.0.x MSI (per user, as 1.0.x installed it),
        then the setup exe /S: the MSI is gone -- its entry, its product,
        its folder, its Start menu folder, its desktop shortcut -- and only
        the new program is installed; leaves it installed (the caller runs
        the program, then -Phase uninstall)
#>
param(
  [Parameter(Mandatory = $true)][ValidateSet('clean', 'again', 'uninstall', 'over', 'msi')][string]$Phase,
  [string]$Setup = '',
  [string]$Version = '',
  [string]$Older = '',
  [string]$OlderVersion = '',
  [string]$Msi = '',
  [Parameter(Mandatory = $true)][string]$Report
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $Report | Out-Null
$Report = (Resolve-Path $Report).Path
$log = Join-Path $Report 'install.txt'
$script:failures = 0
function Note([string]$m) { Write-Host "      $m"; Add-Content $log "      $m" }
function Check([bool]$ok, [string]$m) {
  if ($ok) { Write-Host "PASS  $m"; Add-Content $log "PASS  $m" } else { Write-Host "FAIL  $m"; Add-Content $log "FAIL  $m"; $script:failures += 1 }
}

$product = 'Hallym Circuit Studio'
$dir = Join-Path $env:LOCALAPPDATA "Programs\$product"
$exe = Join-Path $dir 'HallymCircuitStudio.exe'
$startMenu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'
$shortcut = Join-Path $startMenu "$product.lnk"
$desktops = @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('CommonDesktopDirectory'))
$uninstallRoot = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall'
$guid = 'eb84d729-7626-52ce-aff8-71eda9d27e59'   # tools/package-config.ts APP_GUID
$v1UpgradeCode = '{6206F18C-D7FA-366B-98DA-E7980F6083D6}'   # packaging/installer.nsh

function Entries { @(Get-ChildItem $uninstallRoot -ErrorAction SilentlyContinue | ForEach-Object { Get-ItemProperty $_.PSPath } | Where-Object { $_.DisplayName -like '*allym*' }) }
function Ours { @(Entries | Where-Object { $_.PSChildName -eq $guid }) }
function Folders { @(Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Programs'), $env:LOCALAPPDATA -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '*allym*' } | ForEach-Object { $_.FullName }) }
function Shortcuts { @(@($startMenu) + $desktops + @(Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs') | ForEach-Object { Get-ChildItem $_ -Recurse -Filter '*.lnk' -ErrorAction SilentlyContinue } | Where-Object { $_.FullName -like '*allym*' } | ForEach-Object { $_.FullName }) }
function UninstallerCopies { @(Get-ChildItem $env:TEMP -Directory -Filter '~nsu*.tmp' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }) }
function V1Products { $i = New-Object -ComObject WindowsInstaller.Installer; @($i.RelatedProducts($v1UpgradeCode)) }
# Windows Installer's own entries for Settings > Apps (per-user products: under the user's SID).
function V1Entries {
  @(Get-ChildItem 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Installer\UserData' -ErrorAction SilentlyContinue |
    ForEach-Object { Get-ChildItem (Join-Path $_.PSPath 'Products') -ErrorAction SilentlyContinue } |
    ForEach-Object { Get-ItemProperty (Join-Path $_.PSPath 'InstallProperties') -ErrorAction SilentlyContinue } |
    Where-Object { $_.DisplayName -eq 'HallymCircuitStudio' })
}
function Snap([string]$name) { & node tools/windows/state.ts snapshot (Join-Path $Report "state-$name.json") | Out-Host }
function StateDiff([string]$a, [string]$b, [string]$expect, [string]$what) {
  & node tools/windows/state.ts diff (Join-Path $Report "state-$a.json") (Join-Path $Report "state-$b.json") --expect $expect --report (Join-Path $Report "diff-$b.txt") | Out-Host
  Check ($LASTEXITCODE -eq 0) "$what (state $a -> $b, expect ${expect}: $Report\diff-$b.txt)"
}
# Starts a program and waits for it, without the shell (Start-Process goes through ShellExecute,
# which records the launch in the user's jump lists: Windows' trace, not the installer's).
function Run([string]$exe, [string]$arguments) {
  $si = New-Object Diagnostics.ProcessStartInfo
  $si.FileName = $exe; $si.Arguments = $arguments; $si.UseShellExecute = $false
  $p = [Diagnostics.Process]::Start($si)
  $p.WaitForExit()
  return $p
}
function Install([string]$setupExe, [string]$what) {
  $sw = [Diagnostics.Stopwatch]::StartNew()
  $p = Run (Resolve-Path $setupExe).Path '/S'
  $sw.Stop()
  Check ($p.ExitCode -eq 0) "$what`: $(Split-Path -Leaf $setupExe) /S exit $($p.ExitCode) ($($sw.ElapsedMilliseconds) ms)"
  return $sw.ElapsedMilliseconds
}
function State([string]$when) {
  $e = @(Entries); $f = @(Folders); $s = @(Shortcuts)
  $pv = if (Test-Path $exe) { (Get-Item $exe).VersionInfo.ProductVersion } else { '(none)' }
  Note "$when -- uninstall entries: $(($e | ForEach-Object { "$($_.DisplayName) [$($_.DisplayVersion)] $($_.PSChildName)" }) -join '; ')"
  Note "$when -- folders named *allym*: $($f -join '; ')"
  Note "$when -- shortcuts named *allym*: $($s -join '; ')"
  Note "$when -- HallymCircuitStudio.exe product version: $pv"
  return @{ entries = $e; folders = $f; shortcuts = $s; version = $pv }
}
function OneOfEach([string]$when, [string]$v) {
  $s = State $when
  Check ($s.entries.Count -eq 1 -and $s.entries[0].PSChildName -eq $guid) "$when`: one uninstall entry, ours ($guid)"
  Check ($s.entries.Count -ge 1 -and $s.entries[0].DisplayName -eq "$product $v" -and $s.entries[0].DisplayVersion -eq $v) "$when`: the entry says $product $v"
  Check (($s.folders -join '|') -eq $dir) "$when`: one install folder, $dir"
  Check (($s.shortcuts -join '|') -eq $shortcut) "$when`: one shortcut, the Start menu's $product.lnk"
  Check ($s.version -like "$(($v -split '-')[0])*") "$when`: the program is $v (product version $($s.version))"
}
function Uninstall([string]$when) {
  $entry = @(Ours)
  if ($entry.Count -ne 1) { Check $false "$when`: an uninstall entry to run"; return }
  $un = $entry[0].QuietUninstallString
  Note "quiet uninstall: $un"
  if ($un -match '^"([^"]+)"\s*(.*)$') { $unExe = $Matches[1]; $unArgs = $Matches[2] } else { $unExe = $un; $unArgs = '/S' }
  $sw = [Diagnostics.Stopwatch]::StartNew()
  $p = Run $unExe $unArgs
  # The NSIS uninstaller copies itself to %TEMP%\~nsu<X>.tmp and returns at once: wait for its work,
  # then for that copy to be gone (installer.nsh has a hidden cmd remove it once it has ended).
  $deadline = (Get-Date).AddSeconds(90)
  while (((Ours).Count -gt 0 -or (Test-Path $dir)) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 250 }
  $sw.Stop()
  $copyDeadline = (Get-Date).AddSeconds(120)
  while (@(UninstallerCopies).Count -gt 0 -and (Get-Date) -lt $copyDeadline) { Start-Sleep -Milliseconds 500 }
  $left = @(UninstallerCopies)
  Check ($left.Count -eq 0) "$when`: the uninstaller's copy in %TEMP% is gone ($(if ($left.Count) { $left -join ', ' } else { 'none left' }))"
  Start-Sleep -Seconds 2
  Check ($p.ExitCode -eq 0) "$when`: uninstaller exit $($p.ExitCode) (done in $($sw.ElapsedMilliseconds) ms)"
  Check ((Ours).Count -eq 0) "$when`: the uninstall entry is gone"
  Check (-not (Test-Path $dir)) "$when`: the install folder is gone"
  Check (-not (Test-Path $shortcut)) "$when`: the Start menu shortcut is gone"
  Check (-not (Test-Path "HKCU:\Software\$guid")) "$when`: the install record is gone"
}

Push-Location (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))   # electron/
try {
  if ($Phase -eq 'clean') {
    Write-Host "== clean: $Setup"
    Snap 'before'
    $s0 = State 'before'
    Check ($s0.entries.Count -eq 0 -and $s0.folders.Count -eq 0 -and $s0.shortcuts.Count -eq 0) 'nothing of ours installed to begin with'
    # The installer asks for no administrator (per user; a one-click NSIS installer's own manifest).
    $fs = [IO.File]::OpenRead((Resolve-Path $Setup).Path); $buf = New-Object byte[] (4MB); $n = $fs.Read($buf, 0, $buf.Length); $fs.Close()
    $m = [regex]::Match([Text.Encoding]::ASCII.GetString($buf, 0, $n), 'requestedExecutionLevel\s+level="([a-zA-Z]+)"')
    Check ($m.Success -and $m.Groups[1].Value -eq 'asInvoker') "the installer's manifest: requestedExecutionLevel $($m.Groups[1].Value)"
    $setupBytes = (Get-Item $Setup).Length
    Check ((Split-Path -Leaf $Setup) -eq "HallymCircuitStudio-$Version-win-x64-setup.exe") "the installer's name: $(Split-Path -Leaf $Setup)"
    $ms = Install $Setup 'install'
    Snap 'installed'
    StateDiff 'before' 'installed' 'install' 'the installer wrote only the Start menu shortcut and the uninstall entry (and the install folder)'
    OneOfEach 'installed' $Version
    $entry = (Ours)[0]
    Note "entry: $($entry.DisplayName) $($entry.DisplayVersion), publisher $($entry.Publisher), uninstall $($entry.UninstallString)"
    Check ($entry.Publisher -eq 'AIAC Lab, Hallym University') "the entry's publisher: $($entry.Publisher)"
    Check ($entry.QuietUninstallString -like "*$dir*") 'the entry uninstalls from the install folder, quietly with /S'
    Check ((Get-ItemProperty "HKCU:\Software\$guid").InstallLocation -eq $dir) "the install record: $dir"
    foreach ($f in 'HallymCircuitStudio.exe', 'LICENSE.txt', 'NOTICE.txt', 'resources\app.asar', 'resources\engine\hcs-engine.jar', 'resources\engine\hcs-mips.jar',
      'resources\runtime\bin\java.exe', 'resources\runtime\hcs-engine.jsa', 'resources\runtime\legal\java.base\LICENSE') {
      Check (Test-Path (Join-Path $dir $f)) "installed: $f"
    }
    Check (-not (Test-Path (Join-Path $dir 'resources\elevate.exe'))) 'no elevate.exe (nothing asks for an administrator)'
    Check (-not (Test-Path (Join-Path $dir 'resources\app-update.yml'))) 'no update information (no auto-update)'
    Check (-not (Test-Path (Join-Path $env:LOCALAPPDATA 'hallym-circuit-studio-updater'))) 'no copy of the installer kept (no updater folder)'
    $target = (New-Object -ComObject WScript.Shell).CreateShortcut($shortcut).TargetPath
    Check ($target -eq $exe) "the Start menu shortcut opens $target"
    foreach ($d in $desktops) { Check (@(Get-ChildItem $d -Filter '*.lnk' -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '*allym*' }).Count -eq 0) "no desktop shortcut in $d" }
    Check (-not (Test-Path 'HKCU:\Software\Classes\.circ')) 'no .circ under HKCU\Software\Classes'
    $assoc = (& cmd.exe /c 'assoc .circ' 2>&1) -join ' '
    Check ($assoc -notmatch 'allym') "no .circ association (assoc .circ: $assoc)"
    $files = @(Get-ChildItem $dir -Recurse -File)
    $size = ($files | Measure-Object Length -Sum).Sum
    Note ('setup exe: {0:N1} MB ({1} bytes); installed: {2:N1} MB in {3} files; install {4} ms' -f ($setupBytes / 1MB), $setupBytes, ($size / 1MB), $files.Count, $ms)
    $files | Sort-Object Length -Descending | Select-Object -First 12 | ForEach-Object { Note ('  {0,7:N1} MB  {1}' -f ($_.Length / 1MB), $_.FullName.Substring($dir.Length + 1)) }
    $top = Get-ChildItem $dir | ForEach-Object { $s = if ($_.PSIsContainer) { (Get-ChildItem $_.FullName -Recurse -File | Measure-Object Length -Sum).Sum } else { $_.Length }; [pscustomobject]@{ name = $_.Name; bytes = [int64]$s } }
    $parts = @{}
    foreach ($t in @('app.asar', 'engine', 'runtime')) {
      $q = Join-Path $dir "resources\$t"
      $parts[$t] = if (Test-Path $q -PathType Leaf) { [int64](Get-Item $q).Length } else { [int64](Get-ChildItem $q -Recurse -File -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum }
    }
    @{ setupExeBytes = $setupBytes; installedBytes = [int64]$size; installedFiles = $files.Count; installMs = $ms; resources = $parts; top = $top } | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $Report 'install.json')
    Set-Content (Join-Path $Report 'installed.txt') $exe
  }

  if ($Phase -eq 'again') {
    Write-Host "== again: $Setup over itself"
    $null = Install $Setup 'install again'
    OneOfEach 'again' $Version
    Snap 'again'
    StateDiff 'before' 'again' 'install' 'installed twice: still only the shortcut and the entry'
  }

  if ($Phase -eq 'uninstall') {
    Write-Host '== uninstall'
    Uninstall 'uninstalled'
    Snap 'uninstalled'
    StateDiff 'before' 'uninstalled' 'uninstalled' 'nothing left after uninstall'
    $s = State 'uninstalled'
    Check ($s.entries.Count -eq 0 -and $s.folders.Count -eq 0 -and $s.shortcuts.Count -eq 0) 'no entry, folder or shortcut of ours'
  }

  if ($Phase -eq 'over') {
    Write-Host "== over: $Older ($OlderVersion), then $Setup ($Version)"
    Snap 'before-over'
    $s0 = State 'before-over'
    Check ($s0.entries.Count -eq 0 -and $s0.folders.Count -eq 0) 'nothing of ours installed to begin with'
    $null = Install $Older "install $OlderVersion"
    OneOfEach 'earlier' $OlderVersion
    $null = Install $Setup "install $Version over $OlderVersion"
    OneOfEach 'over' $Version
    Snap 'over'
    StateDiff 'before-over' 'over' 'install' "$Version over $OlderVersion`: only the shortcut and the entry"
    Uninstall 'over-uninstalled'
    Snap 'over-uninstalled'
    StateDiff 'before-over' 'over-uninstalled' 'uninstalled' 'nothing left after uninstall'
  }

  if ($Phase -eq 'msi') {
    Write-Host "== msi: $Msi, then $Setup"
    Snap 'before'
    $s0 = State 'before'
    Check ($s0.entries.Count -eq 0 -and (V1Products).Count -eq 0) 'nothing of ours installed to begin with'
    $p = Run (Join-Path $env:SystemRoot 'System32\msiexec.exe') "/i `"$((Resolve-Path $Msi).Path)`" /qn /norestart"
    Check ($p.ExitCode -eq 0) "the v1.0.x MSI installed quietly (msiexec exit $($p.ExitCode))"
    $s1 = State 'msi'
    # A per-user MSI's entry in Settings > Apps is Windows Installer's own (not under HKCU\...\Uninstall).
    $codes = @(V1Products)
    $wi = New-Object -ComObject WindowsInstaller.Installer
    $info = @($codes | ForEach-Object { "$_ $($wi.ProductInfo($_, 'ProductName')) $($wi.ProductInfo($_, 'VersionString')), assignment $($wi.ProductInfo($_, 'AssignmentType'))" })
    Note "installed products of the 1.0.x UpgradeCode ${v1UpgradeCode}: $($info -join '; ')"
    Check ($codes.Count -eq 1 -and $wi.ProductInfo($codes[0], 'ProductName') -eq 'HallymCircuitStudio') "the 1.0.x MSI is installed and found by its UpgradeCode, as the installer looks for it"
    Check ($codes.Count -eq 1 -and $wi.ProductInfo($codes[0], 'AssignmentType') -eq '0') 'installed for this user (as 1.0.x installed itself)'
    $arp = @(V1Entries)
    Note "its entry in Settings > Apps: $(($arp | ForEach-Object { "$($_.DisplayName) $($_.DisplayVersion)" }) -join '; ')"
    $oldExe = @(Get-ChildItem $env:LOCALAPPDATA, $env:ProgramFiles -Recurse -Depth 3 -Filter 'HallymCircuitStudio.exe' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    Note "1.0.x program: $($oldExe -join '; ')"
    Check ($oldExe.Count -ge 1 -and $s1.shortcuts.Count -ge 1) "1.0.x installed, with its shortcuts ($($s1.shortcuts -join '; '))"
    Snap 'msi'
    $ms = Install $Setup 'the setup exe over the 1.0.x MSI'
    OneOfEach 'after' $Version
    Check ((V1Products).Count -eq 0) 'no product of the 1.0.x UpgradeCode left (Windows Installer)'
    Check (@(V1Entries).Count -eq 0) 'the 1.0.x entry in Settings > Apps is gone'
    foreach ($e in $oldExe) { Check (-not (Test-Path $e)) "the 1.0.x program is gone: $e" }
    foreach ($l in $s1.shortcuts) { if ($l -ne $shortcut) { Check (-not (Test-Path $l)) "the 1.0.x shortcut is gone: $l" } }
    Snap 'after'
    StateDiff 'before' 'after' 'install' 'from before the MSI to after the setup exe: only the new shortcut and entry (the MSI left nothing)'
    Set-Content (Join-Path $Report 'installed.txt') $exe
  }
} finally {
  Pop-Location
}

if ($script:failures -gt 0) { Write-Host "RESULT  FAIL ($($script:failures))"; Add-Content $log "RESULT  FAIL ($($script:failures)) -- $Phase"; exit 1 }
Write-Host 'RESULT  PASS'; Add-Content $log "RESULT  PASS -- $Phase"
exit 0   # (not the exit code of the last program run, e.g. assoc's)
