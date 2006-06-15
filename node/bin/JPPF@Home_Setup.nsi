# Auto-generated by EclipseNSIS Script Wizard
# Jun 14, 2006 8:09:02 PM

Name JPPF@Home
# Defines
!define REGKEY "SOFTWARE\$(^Name)"

# MUI defines
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install-blue.ico"
!define MUI_FINISHPAGE_NOAUTOCLOSE
!define MUI_CUSTOMFUNCTION_GUIINIT CustomGUIInit
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall-blue.ico"
!define MUI_UNFINISHPAGE_NOAUTOCLOSE

# Included files
!include Sections.nsh
!include MUI.nsh

# Reserved Files
ReserveFile "${NSISDIR}\Plugins\BGImage.dll"

# Variables
Var StartMenuGroup
Var STARTMENU_FOLDER
Var MUI_TEMP


# Installer pages
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_PAGE_STARTMENU Application $STARTMENU_FOLDER

# Installer languages
!insertmacro MUI_LANGUAGE English

# Installer attributes
OutFile ..\..\JPPF\build\JPPF@Home_Setup.exe
InstallDir $SYSDIR
CRCCheck on
XPStyle on
ShowInstDetails show
InstallDirRegKey HKLM "${REGKEY}" Path
ShowUninstDetails show

# Installer sections
Section -Main SEC0000
    SetOutPath $SYSDIR
    SetOverwrite on
    File ..\..\JPPF\build\screensaver\dist\jppf-win32\saverbeans-api.jar
    File ..\..\JPPF\build\screensaver\dist\jppf-win32\jppf.jar
    File ..\..\JPPF\build\screensaver\dist\jppf-win32\jppf.scr
    WriteRegStr HKLM "${REGKEY}\Components" Main 1
SectionEnd


Section -post SEC0001
    WriteRegStr HKLM "${REGKEY}" Path $INSTDIR
    WriteUninstaller $INSTDIR\JPPF@Home_Uninstall.exe
		!insertmacro MUI_STARTMENU_WRITE_BEGIN Application
		SetOutPath $SMPROGRAMS\$STARTMENU_FOLDER
    CreateShortcut "$SMPROGRAMS\$STARTMENU_FOLDER\Uninstall $(^Name).lnk" $INSTDIR\JPPF@Home_Uninstall.exe
    !insertmacro MUI_STARTMENU_WRITE_END
    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" DisplayName "$(^Name)"
    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" Publisher "JPPF.org"
    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" URLInfoAbout "http://www.jppf.org"
    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" DisplayIcon $INSTDIR\JPPF@Home_Uninstall.exe
    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" UninstallString $INSTDIR\JPPF@Home_Uninstall.exe
    WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" NoModify 1
    WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" NoRepair 1
SectionEnd

# Macro for selecting uninstaller sections
!macro SELECT_UNSECTION SECTION_NAME UNSECTION_ID
    Push $R0
    ReadRegStr $R0 HKLM "${REGKEY}\Components" "${SECTION_NAME}"
    StrCmp $R0 1 0 next${UNSECTION_ID}
    !insertmacro SelectSection "${UNSECTION_ID}"
    GoTo done${UNSECTION_ID}
next${UNSECTION_ID}:
    !insertmacro UnselectSection "${UNSECTION_ID}"
done${UNSECTION_ID}:
    Pop $R0
!macroend

# Uninstaller sections
Section /o un.Main UNSEC0000
    Delete /REBOOTOK $SYSDIR\jppf.scr
    Delete /REBOOTOK $SYSDIR\jppf.jar
    Delete /REBOOTOK $SYSDIR\saverbeans-api.jar
    DeleteRegValue HKLM "${REGKEY}\Components" Main
SectionEnd

Section un.post UNSEC0001
	  !insertmacro MUI_STARTMENU_GETFOLDER Application $MUI_TEMP
    DeleteRegKey HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)"
    Delete /REBOOTOK "$SMPROGRAMS\$MUI_TEMP\Uninstall $(^Name).lnk"
    Delete /REBOOTOK $INSTDIR\JPPF@Home_Uninstall.exe
    DeleteRegValue HKLM "${REGKEY}" StartMenuGroup
    DeleteRegValue HKLM "${REGKEY}" Path
    DeleteRegKey /IfEmpty HKLM "${REGKEY}\Components"
    DeleteRegKey /IfEmpty HKLM "${REGKEY}"
    RmDir /REBOOTOK $SMPROGRAMS\$MUI_TEMP
    #RmDir /REBOOTOK $INSTDIR
SectionEnd

# Installer functions
Function CustomGUIInit
    Push $R1
    Push $R2
    BgImage::SetReturn /NOUNLOAD on
    BgImage::SetBg /NOUNLOAD /GRADIENT "0 0 0 0 0 0"
    Pop $R1
    Strcmp $R1 success 0 error
    File /oname=$PLUGINSDIR\bgimage.bmp jppf-at-home.bmp
    System::call "user32::GetSystemMetrics(i 0)i.R1"
    System::call "user32::GetSystemMetrics(i 1)i.R2"
    IntOp $R1 $R1 - 593
    IntOp $R1 $R1 / 2
    IntOp $R2 $R2 - 149
    IntOp $R2 $R2 / 2
    BGImage::AddImage /NOUNLOAD $PLUGINSDIR\bgimage.bmp $R1 $R2
    CreateFont $R1 "Times New Roman" 26 700 /ITALIC
    BGImage::AddText /NOUNLOAD "$(^SetupCaption)" $R1 "255 255 255" 16 8 500 100
    Pop $R1
    Strcmp $R1 success 0 error
    BGImage::Redraw /NOUNLOAD
    Goto done
error:
    MessageBox MB_OK|MB_ICONSTOP $R1
done:
    Pop $R2
    Pop $R1
FunctionEnd

Function .onGUIEnd
    BGImage::Destroy
FunctionEnd

Function .onInit
    InitPluginsDir
    StrCpy $StartMenuGroup JPPF@Home
FunctionEnd

# Uninstaller functions
Function un.onInit
    ReadRegStr $INSTDIR HKLM "${REGKEY}" Path
    ReadRegStr $StartMenuGroup HKLM "${REGKEY}" StartMenuGroup
    !insertmacro SELECT_UNSECTION Main ${UNSEC0000}
FunctionEnd

