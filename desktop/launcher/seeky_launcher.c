/*
 * Seeky.exe — starts Seeky Desktop with the Java bundled beside it (runtime\bin\javaw.exe).
 * It uses the newest application version installed by the in-app updater
 * (%LOCALAPPDATA%\Seeky\app\<version>, named in current.txt) when it is complete, otherwise the
 * version that came with the installer (app\ beside Seeky.exe).
 * "Seeky.exe --self-test" runs the checks in the console and returns their result.
 */
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#include <windows.h>
#include <shlwapi.h>
#include <wchar.h>

static int exists(const wchar_t *path) { DWORD a = GetFileAttributesW(path); return a != INVALID_FILE_ATTRIBUTES; }

static void updated_app(wchar_t *out, size_t size) {
    wchar_t local[MAX_PATH], file[MAX_PATH], version[64] = L"";
    out[0] = 0;
    if (!GetEnvironmentVariableW(L"LOCALAPPDATA", local, MAX_PATH)) return;
    swprintf(file, MAX_PATH, L"%ls\\Seeky\\app\\current.txt", local);
    HANDLE h = CreateFileW(file, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
    if (h == INVALID_HANDLE_VALUE) return;
    char raw[64] = {0}; DWORD got = 0;
    ReadFile(h, raw, sizeof(raw) - 1, &got, NULL); CloseHandle(h);
    for (DWORD i = 0; i < got && i < 63; i++) {
        char ch = raw[i];
        if ((ch >= '0' && ch <= '9') || ch == '.') version[i] = (wchar_t) ch; else { version[i] = 0; break; }
    }
    if (!version[0]) return;
    wchar_t dir[MAX_PATH], jar[MAX_PATH];
    swprintf(dir, MAX_PATH, L"%ls\\Seeky\\app\\%ls", local, version);
    swprintf(jar, MAX_PATH, L"%ls\\seeky-desktop.jar", dir);
    if (exists(jar)) wcsncpy(out, dir, size - 1);
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR cmd, int show) {
    wchar_t self[MAX_PATH], base[MAX_PATH], app[MAX_PATH], java[MAX_PATH], line[32768];
    GetModuleFileNameW(NULL, self, MAX_PATH);
    wcscpy(base, self); PathRemoveFileSpecW(base);
    int test = cmd && wcsstr(cmd, L"--self-test") != NULL;
    swprintf(java, MAX_PATH, L"%ls\\runtime\\bin\\%ls", base, test ? L"java.exe" : L"javaw.exe");
    if (!exists(java)) { MessageBoxW(NULL, L"O Java do Seeky não foi encontrado. Reinstale o Seeky.", L"Seeky", MB_ICONERROR); return 1; }
    updated_app(app, MAX_PATH);
    if (!app[0]) swprintf(app, MAX_PATH, L"%ls\\app", base);
    swprintf(line, 32768,
        L"\"%ls\" -Xmx1g -Dfile.encoding=UTF-8 -Dseeky.launcher=\"%ls\" --add-modules javafx.controls,javafx.web,javafx.media,javafx.swing -cp \"%ls\\*\" mz.bam.seeky.android.DeskLauncher %ls",
        java, self, app, cmd ? cmd : L"");
    STARTUPINFOW si; PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si)); si.cb = sizeof(si); ZeroMemory(&pi, sizeof(pi));
    if (!CreateProcessW(NULL, line, NULL, NULL, FALSE, 0, NULL, base, &si, &pi)) {
        MessageBoxW(NULL, L"Não foi possível iniciar o Seeky.", L"Seeky", MB_ICONERROR); return 1;
    }
    DWORD code = 0;
    if (test) { WaitForSingleObject(pi.hProcess, INFINITE); GetExitCodeProcess(pi.hProcess, &code); }
    CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
    return (int) code;
}
