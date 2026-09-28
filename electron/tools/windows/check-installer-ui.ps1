<#
.SYNOPSIS
  Runs the installer as a student does -- with its pages, not /S -- and
  checks them (D-155: tools/package-config.ts, packaging/installer.nsh).
  Derived from Hallym MIPS v2.5.0 electron/tools/windows/check-installer-ui.ps1:
  this program's names, the band's and the bar's colours measured on the
  screen as there, and the finish page's words checked.

.DESCRIPTION
  PASS/FAIL lines to <Report>/installer-ui.txt; the script fails if any FAIL.
    - the pages: the progress, then the finish page, and nothing else (no
      folder to choose, no "for all users", no welcome or licence page)
    - the progress page: its head (설치하는 중), and its bar in the app's blue
      (#0055A5) as the screen shows it, not Windows' green
    - the finish page: "설치가 완료되었습니다", its words (Hallym Circuit Studio
      설치를 마쳤습니다...), "지금 실행하기" ticked, and the band on the left in
      the app's navy with the white plate of the symbol, as the screen shows it
    - 마침, with it ticked, starts the program
    - installed where /S installs: %LOCALAPPDATA%\Programs\Hallym Circuit
      Studio, the Start menu's Hallym Circuit Studio, the uninstall entry
      "Hallym Circuit Studio <version>"
  then uninstalls it with the uninstaller's pages (the progress, then "제거가
  끝났습니다", the same band), as Settings > Apps does.

  What the PC keeps (D-155, as the /S checks of tools/windows/check-install.ps1
  and D-148 11-12 measure it -- the guided path is the students' default):
    - before the install, a control period (45 s: snapshot, the helpers, a
      wait, snapshot "ui-before") measures what Windows changes by itself;
    - after the guided install and the program 마침 started (closed, its
      engine and run folder gone): only the installer's four writes (the
      folder, the Start menu shortcut, the uninstall entry and its install
      record) differ from ui-before, but for that control period's noise and
      state.ts ALLOWED (report: diff-ui-installed.txt);
    - before the uninstall, another control period (20 s), then after the
      guided uninstall (and its %TEMP% copy gone): nothing differs from
      ui-before (diff-ui-uninstalled.txt) -- whatever the pages wrote
      (NSIS's language or page records included) would count.
  Each comparison lets through only its own control period's noise.

  Pictures, in <Report>:
    installer-progress.png  the progress page
    installer-finish.png    the finish page
    installer-started.jpg   the program 마침 started (its first screen; JPEG: a photo)
    uninstaller-finish.png  the uninstaller's finish page

  Usage (CI, with nothing of ours installed):
    check-installer-ui.ps1 -Setup s.exe -Report dir
#>
param(
  [Parameter(Mandatory = $true)][string]$Setup,
  [Parameter(Mandatory = $true)][string]$Report
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $Report | Out-Null
$log = Join-Path $Report 'installer-ui.txt'
$script:failures = 0
function Pass([string]$m) { Write-Host "PASS  $m"; Add-Content $log "PASS  $m" }
function Bad([string]$m) { Write-Host "FAIL  $m"; Add-Content $log "FAIL  $m"; $script:failures += 1 }
function Check([bool]$ok, [string]$m) { if ($ok) { Pass $m } else { Bad $m } }
function Note([string]$m) { Write-Host "      $m"; Add-Content $log "      $m" }

Add-Type -AssemblyName System.Drawing
# (Compiled here, before any control period: Add-Type writes its build files in %TEMP%.)
Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class Spi { [DllImport("user32.dll")] public static extern bool SystemParametersInfo(uint a, uint b, ref bool c, uint d); }'
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class Ui {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] static extern bool EnumChildWindows(IntPtr p, EnumProc f, IntPtr l);
  [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc f, IntPtr l);
  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetClassName(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern IntPtr SendMessage(IntPtr h, int msg, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  public static string Text(IntPtr h) { var s = new StringBuilder(1024); GetWindowText(h, s, s.Capacity); return s.ToString(); }
  public static string Class(IntPtr h) { var s = new StringBuilder(256); GetClassName(h, s, s.Capacity); return s.ToString(); }
  public static IntPtr[] AllTops() {
    var list = new List<IntPtr>();
    EnumWindows((h, l) => { if (IsWindowVisible(h)) list.Add(h); return true; }, IntPtr.Zero);
    return list.ToArray();
  }
  public static IntPtr[] Tops(uint pid) {
    var list = new List<IntPtr>();
    EnumWindows((h, l) => { uint p; GetWindowThreadProcessId(h, out p); if (p == pid && IsWindowVisible(h)) list.Add(h); return true; }, IntPtr.Zero);
    return list.ToArray();
  }
  public static IntPtr[] Children(IntPtr parent) {
    var list = new List<IntPtr>();
    EnumChildWindows(parent, (h, l) => { if (IsWindowVisible(h)) list.Add(h); return true; }, IntPtr.Zero);
    return list.ToArray();
  }
}
'@
$BM_GETCHECK = 0x00F0; $BM_CLICK = 0x00F5; $PBM_GETRANGE = 0x0407; $PBM_GETPOS = 0x0408

function Shot([IntPtr]$h, [string]$name) {
  [void][Ui]::SetForegroundWindow($h)
  Start-Sleep -Milliseconds 400
  $r = New-Object Ui+RECT
  [void][Ui]::GetWindowRect($h, [ref]$r)
  $w = $r.Right - $r.Left; $hgt = $r.Bottom - $r.Top
  $bmp = New-Object System.Drawing.Bitmap $w, $hgt
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
  $format = if ($name -like '*.jpg') { [System.Drawing.Imaging.ImageFormat]::Jpeg } else { [System.Drawing.Imaging.ImageFormat]::Png }
  $bmp.Save((Join-Path $Report $name), $format)
  $g.Dispose(); $bmp.Dispose()
  Note "picture: $name (${w}x${hgt})"
}
# The finish pages' band (packaging/*Sidebar.bmp, 164x314 at the page's top
# left, inside the frame): the app's navy around a white plate with the
# symbol, not electron-builder's light blue drawing.  Measured on the screen.
function Band($wr, [string]$what) {
  $navy = PixelAt ($wr.Left + 20) ($wr.Top + 260)
  Note "${what}'s band at (20, 260): rgb($($navy.R), $($navy.G), $($navy.B))"
  Check ($navy.R -lt 40 -and $navy.G -lt 80 -and $navy.B -gt 70 -and $navy.B -lt 160) "${what}'s band in the app's navy"
  # The plate: along rows through its middle (40..112 of the band, under the title bar), navy, then white.
  $found = $null
  foreach ($dy in 80, 90, 100, 110, 120, 130) {
    $row = @(0..70 | ForEach-Object { PixelAt ($wr.Left + $_) ($wr.Top + $dy) })
    $first = -1
    for ($i = 0; $i -lt $row.Count; $i++) { if ($row[$i].R -gt 235 -and $row[$i].G -gt 235 -and $row[$i].B -gt 235) { $first = $i; break } }
    $before = if ($first -gt 4) { $row[$first - 4] } else { $null }
    if ($first -gt 4 -and $before.R -lt 40 -and $before.B -gt 70) { $found = "row +$dy, white from +$first after navy rgb($($before.R), $($before.G), $($before.B))"; break }
  }
  Note "${what}'s band, the symbol's plate: $(if ($found) { $found } else { 'not found' })"
  Check ($null -ne $found) "${what}'s band: the white plate of the symbol on the navy"
}
# One pixel of the screen, as the display shows it.
function PixelAt([int]$x, [int]$y) {
  $b = New-Object System.Drawing.Bitmap 1, 1
  $g = [System.Drawing.Graphics]::FromImage($b)
  $g.CopyFromScreen($x, $y, 0, 0, $b.Size)
  $c = $b.GetPixel(0, 0); $g.Dispose(); $b.Dispose()
  return $c
}

# The installer's (or uninstaller's) window and what it shows now: which
# page, its controls.  The uninstaller runs as a copy of itself from %TEMP%,
# so it is found by its title, not by the process started.
function Page($p, [string]$finishTitle = '설치가 완료되었습니다') {
  $tops = if ($p -is [System.Diagnostics.Process]) { [Ui]::Tops([uint32]$p.Id) } else { [Ui]::AllTops() | Where-Object { [Ui]::Text($_) -like 'Hallym Circuit Studio*' } }
  $top = $tops | Where-Object { [Ui]::Class($_) -eq '#32770' } | Select-Object -First 1
  if (-not $top) { return $null }
  $controls = @([Ui]::Children($top) | ForEach-Object { [pscustomobject]@{ H = $_; Class = [Ui]::Class($_); Text = [Ui]::Text($_) } })
  $kind = if ($controls | Where-Object { $_.Text -eq $finishTitle }) { 'finish' }
          elseif ($controls | Where-Object { $_.Class -eq 'msctls_progress32' }) { 'progress' }
          else { 'other' }
  return [pscustomobject]@{ Top = $top; Kind = $kind; Controls = $controls; Title = [Ui]::Text($top) }
}

$uninstallRoot = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall'
function Ours() {
  Get-ChildItem $uninstallRoot -ErrorAction SilentlyContinue | ForEach-Object { Get-ItemProperty $_.PSPath } |
    Where-Object { $_.DisplayName -like 'Hallym Circuit Studio *' }
}
Check ($null -eq (Ours)) 'nothing of ours installed before'
Get-Process HallymCircuitStudio -ErrorAction SilentlyContinue | Stop-Process -Force

# ---- what the PC keeps (D-155): the snapshots and comparisons of tools/windows/state.ts, as check-install.ps1
$ReportFull = (Resolve-Path $Report).Path
$installDir = Join-Path $env:LOCALAPPDATA 'Programs\Hallym Circuit Studio'
function Snap([string]$name) { & node tools/windows/state.ts snapshot (Join-Path $ReportFull "state-$name.json") | Out-Host }
function StateDiff([string]$a, [string]$b, [string]$expect, [string]$control, [string]$what) {
  & node tools/windows/state.ts diff (Join-Path $ReportFull "state-$a.json") (Join-Path $ReportFull "state-$b.json") --expect $expect --noise (Join-Path $ReportFull "noise-$control.json") --report (Join-Path $ReportFull "diff-$b.txt") | Out-Host
  Check ($LASTEXITCODE -eq 0) "$what (state $a -> $b, expect ${expect}: $ReportFull\diff-$b.txt)"
}
# A program started without the shell (ShellExecute would record the launch in the user's jump lists: the
# check's trace, not the installer's); not waited for.
function Launch([string]$file, [string]$arguments) {
  $si = New-Object Diagnostics.ProcessStartInfo
  $si.FileName = $file; $si.Arguments = $arguments; $si.UseShellExecute = $false
  return [Diagnostics.Process]::Start($si)
}
function OursRunning {
  @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -and ($_.Path -like "$installDir\*" -or $_.Path -like '*\~nsu*.tmp\*' -or $_.Path -like '*\HallymCircuitStudio*') } |
    ForEach-Object { "$($_.Id) $($_.Path)" })
}
# Windows' own noise before a phase (D-148 12): snapshot, the helpers this phase uses (a program started and
# ended without the shell, the uninstall entries read), a wait as long as the phase, snapshot $as; nothing of
# ours running all along.  The phase's comparison lets through only these places.
function Control([string]$name, [int]$seconds, [string]$as) {
  # (An uninstall just before -- the previous CI step's -- ends with its copy in %TEMP% still closing.)
  $wait = (Get-Date).AddSeconds(60)
  while (@(OursRunning).Count -gt 0 -and (Get-Date) -lt $wait) { Start-Sleep -Milliseconds 500 }
  $o = @(OursRunning)
  Check ($o.Count -eq 0) "before the control period for $name`: nothing of ours running ($(if ($o.Count) { $o -join '; ' } else { 'none' }))"
  Snap "control-$name"
  $sw = [Diagnostics.Stopwatch]::StartNew()
  $h = Launch (Join-Path $env:SystemRoot 'System32\hostname.exe') ''; $h.WaitForExit()
  $null = @(Ours)
  $left = $seconds * 1000 - $sw.ElapsedMilliseconds
  if ($left -gt 0) { Start-Sleep -Milliseconds $left }
  $o = @(OursRunning)
  Check ($o.Count -eq 0) "the control period for $name ($seconds s): nothing of ours running ($(if ($o.Count) { $o -join '; ' } else { 'none' }))"
  Snap $as
  & node tools/windows/state.ts noise (Join-Path $ReportFull "state-control-$name.json") (Join-Path $ReportFull "state-$as.json") (Join-Path $ReportFull "noise-$name.json") | Out-Host
}
# The program 마침 started, closed as a student closes it, and everything of it gone: its processes (the
# engine too) and its run folder in %TEMP% (removed by its own detached cleanup after it ends).
function ProgramGone([string]$what) {
  $deadline = (Get-Date).AddSeconds(60)
  while (((@(OursRunning).Count -gt 0) -or (Test-Path (Join-Path $env:TEMP 'HallymCircuitStudio'))) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
  $o = @(OursRunning)
  Check ($o.Count -eq 0) "$what`: nothing of ours running ($(if ($o.Count) { $o -join '; ' } else { 'none' }))"
  Check (-not (Test-Path (Join-Path $env:TEMP 'HallymCircuitStudio'))) "$what`: its run folder in %TEMP% is gone"
}

Write-Host '== the control period before the guided install'
Control 'ui-install' 45 'ui-before'

Write-Host '== the installer, with its pages'
$p = Launch (Resolve-Path $Setup).Path ''
$pages = New-Object System.Collections.Generic.List[string]
$shotProgress = $false
$finish = $null
$deadline = (Get-Date).AddMinutes(4)
while ((Get-Date) -lt $deadline -and -not $p.HasExited) {
  $page = Page $p
  if ($page) {
    if ($pages.Count -eq 0 -or $pages[$pages.Count - 1] -ne $page.Kind) {
      $pages.Add($page.Kind)
      Note "page $($pages.Count): $($page.Kind) -- window ""$($page.Title)"": $(($page.Controls | Where-Object { $_.Text } | ForEach-Object { $_.Text }) -join ' | ')"
    }
    if ($page.Kind -eq 'progress' -and -not $shotProgress) {
      $bar = ($page.Controls | Where-Object { $_.Class -eq 'msctls_progress32' } | Select-Object -First 1).H
      $max = [Ui]::SendMessage($bar, $PBM_GETRANGE, [IntPtr]0, [IntPtr]0).ToInt64()
      $pos = [Ui]::SendMessage($bar, $PBM_GETPOS, [IntPtr]0, [IntPtr]0).ToInt64()
      if ($max -gt 0 -and $pos -ge $max * 0.25) {
        Shot $page.Top 'installer-progress.png'; $shotProgress = $true
        $script:progressHead = @($page.Controls | Where-Object { $_.Text -eq '설치하는 중' }).Count -eq 1
        # The filled part of the bar, as the screen shows it: the app's blue (#0055A5), not Windows' green.
        # (Its position is read before the screen shows it: a first sample can still be the pale track, seen once
        # on the runner -- so up to 2 s of samples, each noted.)
        $br = New-Object Ui+RECT; [void][Ui]::GetWindowRect($bar, [ref]$br)
        for ($k = 0; $k -lt 20 -and -not $script:barBlue; $k++) {
          $c = PixelAt ($br.Left + 4) ([int](($br.Top + $br.Bottom) / 2))
          Note "the progress bar's filled part: rgb($($c.R), $($c.G), $($c.B))"
          $script:barBlue = ($c.B -gt 140 -and $c.R -lt 60 -and $c.G -lt 130)
          if (-not $script:barBlue) { Start-Sleep -Milliseconds 100 }
        }
      }
    }
    if ($page.Kind -eq 'finish') { $finish = $page; break }
  }
  Start-Sleep -Milliseconds 150
}
Check ($null -ne $finish) 'the finish page came'
Check (($pages -join ',') -eq 'progress,finish') "the pages: $($pages -join ', ') (the progress, then the finish page, nothing else)"
Check $shotProgress 'the progress page, pictured'
Check ([bool]$script:progressHead) 'the progress page: 설치하는 중 (installer.nsh, no particle after the name)'
Check ([bool]$script:barBlue) "the progress bar in the app's blue, not Windows' green"
if ($finish) {
  Start-Sleep -Milliseconds 500
  $finish = Page $p
  Shot $finish.Top 'installer-finish.png'
  # The band on the left (packaging/installerSidebar.bmp): the app's navy, not electron-builder's light blue.
  $wr = New-Object Ui+RECT; [void][Ui]::GetWindowRect($finish.Top, [ref]$wr)
  Band $wr 'the finish page'
  $texts = @($finish.Controls | ForEach-Object { $_.Text })
  Check ($texts -contains '설치가 완료되었습니다') 'finish page: 설치가 완료되었습니다'
  Check (@($texts | Where-Object { $_ -like 'Hallym Circuit Studio 설치를 마쳤습니다.*' }).Count -eq 1) 'finish page: Hallym Circuit Studio 설치를 마쳤습니다.'
  $run = $finish.Controls | Where-Object { $_.Class -eq 'Button' -and $_.Text -eq '지금 실행하기' } | Select-Object -First 1
  Check ($null -ne $run) 'finish page: 지금 실행하기'
  if ($run) { Check ([Ui]::SendMessage($run.H, $BM_GETCHECK, [IntPtr]0, [IntPtr]0).ToInt64() -eq 1) '지금 실행하기 ticked' }
  $done = $finish.Controls | Where-Object { $_.Class -eq 'Button' -and $_.Text -like '마침*' } | Select-Object -First 1
  Check ($null -ne $done) "finish page: the 마침 button ($($done.Text))"

  Write-Host '== 마침, with 지금 실행하기 ticked'
  if ($done) { [void][Ui]::SendMessage($done.H, $BM_CLICK, [IntPtr]0, [IntPtr]0) }
  Check ($p.WaitForExit(30000)) 'the installer closed'
  $app = $null
  for ($i = 0; $i -lt 60 -and -not $app; $i++) { Start-Sleep -Milliseconds 500; $app = Get-Process HallymCircuitStudio -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1 }
  Check ($null -ne $app) 'the program started'
  if ($app) {
    Start-Sleep -Seconds 6 # the first screen's video playing
    $app.Refresh()
    Shot $app.MainWindowHandle 'installer-started.jpg'
    # What this launch shows behind the card -- the video, or the still:
    # Windows' "animation effects" (SPI_GETCLIENTAREAANIMATION) is what
    # prefers-reduced-motion follows; and whether the picture moves.
    $anim = $false; [void][Spi]::SystemParametersInfo(0x1042, 0, [ref]$anim, 0)
    Note "Windows animation effects (SPI_GETCLIENTAREAANIMATION): $anim -- off means prefers-reduced-motion, the still only"
    $ar = New-Object Ui+RECT; [void][Ui]::GetWindowRect($app.MainWindowHandle, [ref]$ar)
    $grab = { $bm = New-Object System.Drawing.Bitmap 400, 120; $gr = [System.Drawing.Graphics]::FromImage($bm); $gr.CopyFromScreen($ar.Left + 60, $ar.Top + 80, 0, 0, $bm.Size); $gr.Dispose(); $bm }
    $a1 = & $grab; Start-Sleep -Seconds 2; $a2 = & $grab
    $diff = 0; for ($y = 0; $y -lt 120; $y += 4) { for ($x = 0; $x -lt 400; $x += 4) { $c1 = $a1.GetPixel($x, $y); $c2 = $a2.GetPixel($x, $y); $diff += [Math]::Abs($c1.R - $c2.R) + [Math]::Abs($c1.G - $c2.G) + [Math]::Abs($c1.B - $c2.B) } }
    Note ("the start screen's background over 2 s: mean change {0:N1} per pixel ({1})" -f ($diff / 3000), $(if ($diff / 3000 -gt 2) { 'moving: the video' } else { 'still: no video' }))
    # Closed as a student closes it (the window's close button: no file open, nothing asked).
    Get-Process HallymCircuitStudio -ErrorAction SilentlyContinue | ForEach-Object { $null = $_.CloseMainWindow() }
    ProgramGone 'the program closed'
    # If it did not end, end it (and its engine), so that the checks below still run; the check above failed.
    Get-Process HallymCircuitStudio -ErrorAction SilentlyContinue | Stop-Process -Force
    Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$installDir\*" } | Stop-Process -Force
  }
}
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }

Write-Host '== what the guided install left on the PC'
Snap 'ui-installed'
StateDiff 'ui-before' 'ui-installed' 'install' 'ui-install' 'the guided install (and the program 마침 started, then closed) wrote only the Start menu shortcut, the uninstall entry and its install record (and the install folder)'

Write-Host '== where it went: as /S installs'
$entry = Ours
Check ($null -ne $entry) 'uninstall entry under HKCU (per user)'
if ($entry) {
  Note "uninstall entry: $($entry.DisplayName) $($entry.DisplayVersion); $($entry.UninstallString)"
  Check ($entry.DisplayName -match '^Hallym Circuit Studio \d+\.\d+\.\d+(-[0-9A-Za-z.]+)?$') "uninstall entry named ""$($entry.DisplayName)"""
  # The uninstall key has no InstallLocation: the folder is the uninstaller's (as check-side-by-side.ps1 reads it).
  $dir = $entry.InstallLocation
  if (-not $dir) { $dir = Split-Path -Parent ($entry.UninstallString -replace '"', '' -replace ' /currentuser', '') }
  Check ($dir -eq "$env:LOCALAPPDATA\Programs\Hallym Circuit Studio") "installed in $dir"
  Check (Test-Path (Join-Path $dir 'HallymCircuitStudio.exe')) 'HallymCircuitStudio.exe there'
  Check (Test-Path (Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Hallym Circuit Studio.lnk')) 'Start menu: Hallym Circuit Studio'
  Check (-not (Test-Path (Join-Path ([Environment]::GetFolderPath('Desktop')) 'Hallym Circuit Studio.lnk'))) 'no desktop shortcut'

  Write-Host '== uninstall, with its pages (as Settings > Apps runs it)'
  $un = $entry.UninstallString
  $exe = [regex]::Match($un, '"([^"]+)"').Groups[1].Value
  $uargs = ($un -replace '"[^"]+"', '').Trim()
  Control 'ui-uninstall' 20 'ui-pre-uninstall'
  $u = Launch $exe $uargs
  $upages = New-Object System.Collections.Generic.List[string]
  $ufinish = $null
  $deadline = (Get-Date).AddMinutes(3)
  while ((Get-Date) -lt $deadline) {
    $page = Page 'uninstaller' '제거가 끝났습니다'
    if ($page) {
      if ($upages.Count -eq 0 -or $upages[$upages.Count - 1] -ne $page.Kind) {
        $upages.Add($page.Kind)
        Note "uninstaller page $($upages.Count): $($page.Kind) -- window ""$($page.Title)"": $(($page.Controls | Where-Object { $_.Text } | ForEach-Object { $_.Text }) -join ' | ')"
      }
      if ($page.Kind -eq 'finish') { $ufinish = $page; break }
    }
    Start-Sleep -Milliseconds 150
  }
  Check (($upages -join ',') -eq 'progress,finish') "the uninstaller's pages: $($upages -join ', ') (the progress, then the finish page)"
  if ($ufinish) {
    Start-Sleep -Milliseconds 500
    $ufinish = Page 'uninstaller' '제거가 끝났습니다'
    Shot $ufinish.Top 'uninstaller-finish.png'
    $uwr = New-Object Ui+RECT; [void][Ui]::GetWindowRect($ufinish.Top, [ref]$uwr)
    Band $uwr "the uninstaller's finish page"
    Check (@($ufinish.Controls | Where-Object { $_.Text -like 'Hallym Circuit Studio 제거를 마쳤습니다.*' }).Count -eq 1) 'uninstaller finish page: Hallym Circuit Studio 제거를 마쳤습니다.'
    $done = $ufinish.Controls | Where-Object { $_.Class -eq 'Button' -and $_.Text -like '마침*' } | Select-Object -First 1
    if ($done) { [void][Ui]::SendMessage($done.H, $BM_CLICK, [IntPtr]0, [IntPtr]0) }
  }
  Start-Sleep -Seconds 5
  Check ($null -eq (Ours)) 'uninstalled: the entry gone'
  Check (-not (Test-Path (Join-Path $dir 'HallymCircuitStudio.exe'))) 'uninstalled: the program gone'
  # The uninstaller runs as a copy of itself in %TEMP%\~nsu<X>.tmp; installer.nsh has that folder removed once it ends.
  $copyDeadline = (Get-Date).AddSeconds(120)
  while (@(Get-ChildItem $env:TEMP -Directory -Filter '~nsu*.tmp' -ErrorAction SilentlyContinue).Count -gt 0 -and (Get-Date) -lt $copyDeadline) { Start-Sleep -Milliseconds 500 }
  Check (@(Get-ChildItem $env:TEMP -Directory -Filter '~nsu*.tmp' -ErrorAction SilentlyContinue).Count -eq 0) "uninstalled: the uninstaller's copy in %TEMP% is gone"
  Start-Sleep -Seconds 2
  Snap 'ui-uninstalled'
  StateDiff 'ui-before' 'ui-uninstalled' 'uninstalled' 'ui-uninstall' 'nothing left after the guided uninstall (whatever its pages wrote would count)'
  if (Ours) {
    # Leave the runner clean whatever happened above.
    $q = Launch $exe "$uargs /S"; $q.WaitForExit()
  }
}

if ($script:failures -gt 0) { Write-Host "$($script:failures) failed"; exit 1 }
