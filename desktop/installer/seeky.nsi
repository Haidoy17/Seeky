; Seeky para Windows (64 bits) — instalador por utilizador, sem direitos de administrador.
; makensis -DVERSION=5.46.0 -DSTAGE=<pasta preparada> -DOUT=<ficheiro .exe> seeky.nsi
Unicode true
SetCompressor /SOLID lzma
!include "MUI2.nsh"
!include "x64.nsh"

Name "Seeky ${VERSION}"
OutFile "${OUT}"
InstallDir "$LOCALAPPDATA\Programs\Seeky"
InstallDirRegKey HKCU "Software\Seeky" "InstallDir"
RequestExecutionLevel user
BrandingText "Seeky ${VERSION}"
VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "Seeky"
VIAddVersionKey "FileDescription" "Instalador do Seeky para Windows"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "Seeky"

!define MUI_ICON "${STAGE}\seeky.ico"
!define MUI_UNICON "${STAGE}\seeky.ico"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\Seeky.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Abrir o Seeky"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "Portuguese"

Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_ICONSTOP "O Seeky para Windows precisa de um Windows de 64 bits."
    Abort
  ${EndIf}
FunctionEnd

Section "Seeky"
  SetOutPath "$INSTDIR"
  ; A versão anterior da aplicação e do Java é substituída; os dados (Bíblias, notas, livros) ficam em %APPDATA%\Seeky.
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\runtime"
  File "${STAGE}\Seeky.exe"
  File "${STAGE}\seeky.ico"
  File /r "${STAGE}\app"
  File /r "${STAGE}\runtime"
  File "${STAGE}\LEIA-ME.txt"
  ; Uma instalação nova usa a aplicação que traz (as actualizações descarregadas antes ficam para trás).
  Delete "$LOCALAPPDATA\Seeky\app\current.txt"
  WriteUninstaller "$INSTDIR\Desinstalar.exe"
  CreateDirectory "$SMPROGRAMS\Seeky"
  CreateShortCut "$SMPROGRAMS\Seeky\Seeky.lnk" "$INSTDIR\Seeky.exe" "" "$INSTDIR\seeky.ico"
  CreateShortCut "$SMPROGRAMS\Seeky\Desinstalar o Seeky.lnk" "$INSTDIR\Desinstalar.exe"
  CreateShortCut "$DESKTOP\Seeky.lnk" "$INSTDIR\Seeky.exe" "" "$INSTDIR\seeky.ico"
  WriteRegStr HKCU "Software\Seeky" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Seeky" "DisplayName" "Seeky"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Seeky" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Seeky" "Publisher" "Seeky"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Seeky" "DisplayIcon" "$INSTDIR\seeky.ico"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Seeky" "UninstallString" '"$INSTDIR\Desinstalar.exe"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Seeky" "InstallLocation" "$INSTDIR"
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Seeky" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Seeky" "NoRepair" 1
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\Seeky.lnk"
  RMDir /r "$SMPROGRAMS\Seeky"
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\runtime"
  Delete "$INSTDIR\Seeky.exe"
  Delete "$INSTDIR\seeky.ico"
  Delete "$INSTDIR\LEIA-ME.txt"
  Delete "$INSTDIR\Desinstalar.exe"
  RMDir "$INSTDIR"
  RMDir /r "$LOCALAPPDATA\Seeky\app"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Seeky"
  DeleteRegKey HKCU "Software\Seeky"
  ; Os dados do utilizador (%APPDATA%\Seeky) ficam, para não perder Bíblias, notas e livros.
SectionEnd
