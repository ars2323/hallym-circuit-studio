; The Windows installer's own part (N-23, D-148), read by electron-builder's
; one-click NSIS template (tools/package-config.ts).  Derived from Hallym
; MIPS v2.3.0/v2.4.0 electron/packaging/installer.nsh (the folder's name, no
; updater copy); the v1.0.x MSI and the words are this program's.
;
; What the installer writes, all of it: the install folder
; %LOCALAPPDATA%\Programs\Hallym Circuit Studio, the Start menu shortcut
; "Hallym Circuit Studio", and under HKCU the uninstall entry
; (Software\Microsoft\Windows\CurrentVersion\Uninstall\{guid}) with
; electron-builder's install record beside it (Software\{guid}: where it is
; installed, for installing over it).  The uninstaller removes each.  Nothing
; in %APPDATA%, no file association, no desktop shortcut, no auto-update.

!include LogicLib.nsh
!include FileFunc.nsh

; The per-user install folder: %LOCALAPPDATA%\Programs\Hallym Circuit Studio.
; A one-click installer names it after the package's npm name
; (hallym-circuit-studio, which may not have blanks or capitals); this include
; is read before the templates that use APP_FILENAME, so the folder carries
; the program's name.
!undef APP_FILENAME
!define APP_FILENAME "Hallym Circuit Studio"

; Hallym Circuit Studio 1.0.0 - 1.0.2 were also published as an MSI (jpackage,
; per user: tools/package-windows.ps1 at those tags, --name HallymCircuitStudio
; --vendor "AIAC Lab, Hallym University").  jpackage derives the MSI's
; UpgradeCode from those two -- UUID.nameUUIDFromBytes("UpgradeCode/<vendor>/<name>")
; -- so every 1.0.x MSI has this one (checked against the published 1.0.2 MSI:
; tests/unit/package-config.test.ts).  1.0.3 has no MSI; the 1.0.3 zip is
; not an install and is left alone.
!define HCS_V1_MSI_UPGRADE_CODE "{6206F18C-D7FA-366B-98DA-E7980F6083D6}"
!define HCS_V1_MSI_NAME "HallymCircuitStudio"

; The words the installer and the uninstaller may show, in Korean without a
; particle after the program's name (N-20; electron-builder's own Korean
; ones put one after it, and some have none, falling back to English).
; customHeader comes after electron-builder's messages, so these are the
; ones used (NSIS warns that a LangString is set twice: allowed here only).
; uninstallFailed is shown followed by ": <exit code>".
!macro customHeader
  !pragma warning push
  !pragma warning disable 6030
  LangString installing 1042 "설치하는 중입니다. 잠시 기다려 주세요."
  LangString appRunning 1042 "실행 중인 프로그램을 닫아야 설치할 수 있습니다: ${PRODUCT_NAME}$\r$\n확인 단추를 누르면 닫고 계속합니다."
  LangString appClosing 1042 "실행 중인 프로그램을 닫는 중입니다: ${PRODUCT_NAME}"
  LangString appCannotBeClosed 1042 "프로그램을 닫지 못했습니다: ${PRODUCT_NAME}$\r$\n직접 닫은 뒤 다시 시도 단추를 누르세요."
  LangString areYouSureToUninstall 1042 "다음 프로그램을 제거합니다: ${PRODUCT_NAME}$\r$\n직접 저장한 .circ 파일은 그대로 둡니다."
  LangString uninstallFailed 1042 "예전 버전의 파일을 지우지 못했습니다. 설치 파일을 다시 실행해 보세요. 오류 코드"
  LangString decompressionFailed 1042 "파일을 풀지 못했습니다. 설치 파일을 다시 받아 실행해 보세요."
  !pragma warning pop
!macroend

; Removes every installed product of the 1.0.x MSI's UpgradeCode, silently
; (msiexec /x <product code> /qn: a per-user MSI needs no administrator).
; Run after the new program is in place, so that a failure never leaves the
; student with neither.  If one cannot be removed (an administrator installed
; it for all users, say), a notice says what to do and the install stands.
!macro hcsRemoveV1Msi
  Push $R0
  Push $R1
  Push $R2
  Push $R3
  Push $R4
  Push $R5
  StrCpy $R0 0    ; the index into the related products
  StrCpy $R4 ""   ; the ones that could not be removed
  StrCpy $R5 0    ; rounds, at most 16
  ${Do}
    System::Call 'msi::MsiEnumRelatedProductsW(w "${HCS_V1_MSI_UPGRADE_CODE}", i 0, i R0, w .R1) i .R2'
    ${If} $R2 != 0   ; ERROR_NO_MORE_ITEMS (or none at all)
      ${Break}
    ${EndIf}
    DetailPrint "HallymCircuitStudio 1.0.x (MSI): $R1"
    ExecWait '"$SYSDIR\msiexec.exe" /x $R1 /qn /norestart' $R3
    ; 0 removed, 1605 already gone, 3010/1641 removed (restart to finish): the next one takes index $R0.
    ${If} $R3 == 0
    ${OrIf} $R3 == 1605
    ${OrIf} $R3 == 3010
    ${OrIf} $R3 == 1641
      DetailPrint "  removed ($R3)"
    ${Else}
      DetailPrint "  not removed ($R3)"
      StrCpy $R4 "$R4 $R1"
      IntOp $R0 $R0 + 1
    ${EndIf}
    IntOp $R5 $R5 + 1
  ${LoopUntil} $R5 >= 16
  ${If} $R4 != ""
    MessageBox MB_OK|MB_ICONINFORMATION "새 버전은 설치했지만 예전 버전을 지우지 못했습니다.$\r$\n설정 › 앱에서 다음 항목을 직접 제거하세요: ${HCS_V1_MSI_NAME}" /SD IDOK
  ${EndIf}
  Pop $R5
  Pop $R4
  Pop $R3
  Pop $R2
  Pop $R1
  Pop $R0
!macroend

!macro customInstall
  ; electron-builder's installer keeps a copy of itself (the whole installer,
  ; over 100 MB) in %LOCALAPPDATA%\<name>-updater for electron-updater's
  ; differential updates.  This program has no auto-updater: remove the copy.
  Delete "$LOCALAPPDATA\${APP_INSTALLER_STORE_FILE}"
  RMDir "$LOCALAPPDATA\hallym-circuit-studio-updater"
  !insertmacro hcsRemoveV1Msi
!macroend

; The uninstaller, started from the uninstall entry, copies itself to
; %TEMP%\~nsu<X>.tmp\Un_<X>.exe and runs from there so that it can remove the
; install folder; NSIS asks Windows to delete that copy at the next restart,
; which only an administrator's request does.  So when the uninstaller runs
; from such a copy (not in the install folder: an install over an earlier
; version runs it in place), a hidden cmd waits for the copy to end and then
; removes its folder -- nothing of the program is left in %TEMP% either.
; CreateProcess with CREATE_NO_WINDOW: no console window, and no shell launch
; (ShellExecute would record the launch in the user's jump lists).
!macro hcsRemoveUninstallerCopy
  Push $R0
  Push $R1
  Push $R2
  Push $R3
  Push $R4
  Push $R5
  Push $R6
  ${GetFileName} "$EXEDIR" $R0
  StrCpy $R1 $R0 4
  StrCpy $R2 $R0 "" -4
  ${If} $R1 == "~nsu"
  ${AndIf} $R2 == ".tmp"
  ${AndIf} "$EXEDIR" != "$INSTDIR"
    StrCpy $R3 '"$SYSDIR\cmd.exe" /d /q /c for /l %i in (1,1,120) do @if exist "$EXEDIR\" (rd /s /q "$EXEDIR" 2>nul & ping -n 2 127.0.0.1 >nul)'
    System::Call '*(i 68, &w32) p .R4'
    System::Call '*(p, p, i, i) p .R5'
    System::Call 'kernel32::CreateProcessW(p 0, w R3, p 0, p 0, i 0, i 0x08000000, p 0, p 0, p R4, p R5) i .R6'
    ${If} $R6 != 0
      System::Call '*$R5(p .R1, p .R2)'
      System::Call 'kernel32::CloseHandle(p R1)'
      System::Call 'kernel32::CloseHandle(p R2)'
    ${EndIf}
    System::Free $R4
    System::Free $R5
  ${EndIf}
  Pop $R6
  Pop $R5
  Pop $R4
  Pop $R3
  Pop $R2
  Pop $R1
  Pop $R0
!macroend

!macro customUnInstall
  !insertmacro hcsRemoveUninstallerCopy
!macroend
