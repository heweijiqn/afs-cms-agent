' Start the local AFS CMS without any visible window.
'
' Uses javaw.exe (no console subsystem) and a hidden window mode, so nothing pops up and the
' process detaches from whatever launched it. Status is written to .\log\start.log, and the
' service logs stay in .\log\service.out.log / service.err.log.
'
' Usage: double-click this file, or run: wscript.exe Start-LocalCms.vbs
Option Explicit

Dim fso, shell, root, jar, javaHome, javaExe, args, logDir, startLog, outLog, errLog, note
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")

root = fso.GetParentFolderName(WScript.ScriptFullName)
jar = fso.BuildPath(root, "target\afs-cms.jar")
logDir = fso.BuildPath(root, "log")
If Not fso.FolderExists(logDir) Then fso.CreateFolder(logDir) End If
startLog = fso.BuildPath(logDir, "start.log")
outLog = fso.BuildPath(logDir, "service.out.log")
errLog = fso.BuildPath(logDir, "service.err.log")

If Not fso.FileExists(jar) Then
    LogLine startLog, "SKIP: jar not found -> " & jar
    WScript.Quit 1
End If

If AlreadyListening() Then
    LogLine startLog, "SKIP: already listening on 9901"
    WScript.Quit 0
End If

javaHome = FindJdk17()
If javaHome = "" Then
    LogLine startLog, "FAIL: no JDK 17 found in %JAVA_HOME% or %USERPROFILE%\.jdks"
    WScript.Quit 1
End If

javaExe = fso.BuildPath(javaHome, "bin\javaw.exe")
args = "-Xmx1200m -jar """ & jar & """ --spring.profiles.active=local-cms"

On Error Resume Next
shell.CurrentDirectory = root
' 0 = hidden window, False = return immediately without waiting.
shell.Run """" & javaExe & """ " & args, 0, False
If Err.Number <> 0 Then
    LogLine startLog, "FAIL: launch error " & Err.Number & " " & Err.Description
    WScript.Quit 1
End If
Err.Clear
On Error GoTo 0

LogLine startLog, "STARTED: " & javaExe & " (console) http://127.0.0.1:9901/console"
WScript.Quit 0

' ---------------------------------------------------------------------------- helpers

Sub LogLine(path, message)
    Dim stream
    On Error Resume Next
    Set stream = fso.OpenTextFile(path, 8, True)
    If Err.Number = 0 Then
        stream.WriteLine Now & "  " & message
        stream.Close
    End If
    Err.Clear
    On Error GoTo 0
End Sub

Function AlreadyListening()
    Dim probe
    AlreadyListening = False
    On Error Resume Next
    Set probe = CreateObject("MSXML2.ServerXMLHTTP.6.0")
    If Err.Number = 0 Then
        probe.setTimeouts 500, 500, 800, 800
        probe.open "GET", "http://127.0.0.1:9901/health", False
        probe.send
        If Err.Number = 0 Then
            If probe.status = 200 Then AlreadyListening = True
        End If
    End If
    Err.Clear
    On Error GoTo 0
End Function

' Returns the newest directory under %USERPROFILE%\.jdks whose release file declares Java 17.
Function FindJdk17()
    Dim envHome, baseDir, release, text, folder, best, bestName
    FindJdk17 = ""

    envHome = shell.ExpandEnvironmentStrings("%JAVA_HOME%")
    If envHome <> "%JAVA_HOME%" And envHome <> "" Then
        If IsJava17(envHome) Then
            FindJdk17 = envHome
            Exit Function
        End If
    End If

    baseDir = fso.BuildPath(shell.ExpandEnvironmentStrings("%USERPROFILE%"), ".jdks")
    If Not fso.FolderExists(baseDir) Then Exit Function

    best = ""
    bestName = ""
    For Each folder In fso.GetFolder(baseDir).SubFolders
        If IsJava17(folder.Path) Then
            If folder.Name > bestName Then
                bestName = folder.Name
                best = folder.Path
            End If
        End If
    Next
    FindJdk17 = best
End Function

Function IsJava17(home)
    Dim releasePath, stream, text
    IsJava17 = False
    releasePath = fso.BuildPath(home, "release")
    If Not fso.FileExists(releasePath) Then Exit Function
    If Not fso.FileExists(fso.BuildPath(home, "bin\javaw.exe")) Then Exit Function
    On Error Resume Next
    Set stream = fso.OpenTextFile(releasePath, 1)
    If Err.Number <> 0 Then
        Err.Clear
        Exit Function
    End If
    text = stream.ReadAll
    stream.Close
    On Error GoTo 0
    If InStr(text, "JAVA_VERSION=""17") > 0 Then IsJava17 = True
End Function
