@echo off
setlocal EnableDelayedExpansion

:: =============================================================
:: CONFIGURATION
:: Proxy port and bypass list. The phone IP is auto-detected
:: from the active default gateway (USB tethering / hotspot),
:: because modern Android randomizes the tethering subnet.
:: =============================================================
set "PROXY_PORT=8080"
set "PROXY_OVERRIDE=<local>;*.local;127.0.0.1;localhost"

:: =============================================================
:: Check Administrator Privileges (graceful fallback)
:: =============================================================
set "IS_ADMIN=0"
net session >nul 2>&1
if %errorlevel% equ 0 set "IS_ADMIN=1"

title Windows Proxy and TCP Optimization Controller

set "REG_KEY=HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings"

:: =============================================================
:: MENU LOOP
:: =============================================================
:MENU
cls

:: Re-read current proxy status fresh every time we show the menu
set "CURRENT_STATUS=0x0"
set "CURRENT_SERVER=None"
for /f "tokens=3" %%A in ('reg query "%REG_KEY%" /v ProxyEnable 2^>nul') do set "CURRENT_STATUS=%%A"
for /f "tokens=3" %%A in ('reg query "%REG_KEY%" /v ProxyServer 2^>nul') do set "CURRENT_SERVER=%%A"

:: Auto-detect default gateway (Phone IP) using ipconfig (with route print and PowerShell fallbacks)
set "DETECTED_GW="
for /f "tokens=2 delims=:" %%A in ('ipconfig 2^>nul ^| findstr /i /c:"Default Gateway" /c:"Gateway"') do (
    for /f "tokens=1" %%B in ("%%A") do (
        set "GW_VAL=%%B"
        if not "!GW_VAL!"=="" (
            if not "!GW_VAL!"=="0.0.0.0" (
                echo !GW_VAL! | findstr /r "^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*$" >nul 2>&1
                if not errorlevel 1 (
                    set "DETECTED_GW=!GW_VAL!"
                )
            )
        )
    )
)

if not defined DETECTED_GW (
    for /f "tokens=3" %%A in ('route print 0.0.0.0 2^>nul ^| findstr "\<0.0.0.0\>"') do (
        if not "%%A"=="0.0.0.0" (
            echo %%A | findstr /r "^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*$" >nul 2>&1
            if not errorlevel 1 set "DETECTED_GW=%%A"
        )
    )
)

if not defined DETECTED_GW (
    for /f "delims=" %%G in ('powershell -NoProfile -Command "try { Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction Stop ^| Where-Object { $_.NextHop -ne '0.0.0.0' -and $_.NextHop -match '^\d+\.\d+\.\d+\.\d+$' } ^| Sort-Object RouteMetric ^| Select-Object -First 1 -ExpandProperty NextHop } catch { }" 2^>nul') do set "DETECTED_GW=%%G"
)

echo ========================================================
echo         WINDOWS PROXY AND TCP OPTIMIZER CONTROLLER
echo ========================================================
echo.
if "!CURRENT_STATUS!"=="0x1" (
    echo   Current Status : [ ONLINE / ACTIVE ]
    echo   Active Proxy   : !CURRENT_SERVER!
) else (
    echo   Current Status : [ OFFLINE / DIRECT CONNECTION ]
    echo   Active Proxy   : None
)
if defined DETECTED_GW (
    echo   Gateway IP     : !DETECTED_GW!  [auto-detected via ipconfig]
) else (
    echo   Gateway IP     : not detected - manual entry required
)
if "!IS_ADMIN!"=="1" (
    echo   Privileges     : Administrator (Full TCP optimization)
) else (
    echo   Privileges     : Standard (Run as Admin for TCP tweaks)
)
echo.
echo ========================================================
echo   Select an Option:
echo ========================================================
echo   [1] Turn ON Proxy for USB Mode
echo   [2] Turn ON Proxy for Wi-Fi Mode
if "!CURRENT_STATUS!"=="0x1" (
    echo   [0] Turn OFF Proxy and Restore Defaults
) else (
    echo   [0] Exit
)
echo.
echo   Single keypress - no Enter required.
echo.

choice /c:120 /n /m "   Enter choice: "
if errorlevel 3 goto :OFF_OR_EXIT
if errorlevel 2 goto :MODE_WIFI
if errorlevel 1 goto :MODE_USB

:MODE_USB
set "MODE_NAME=USB Tethering"
goto :ENABLE_PROXY

:MODE_WIFI
set "MODE_NAME=Wi-Fi Hotspot"
goto :ENABLE_PROXY

:OFF_OR_EXIT
if "!CURRENT_STATUS!"=="0x1" (
    goto :DISABLE_PROXY
) else (
    exit /b
)

:: =============================================================
:: TURN ON PROXY and OPTIMIZE TCP
:: =============================================================
:ENABLE_PROXY
echo.
echo ========================================================
echo   CONFIGURING PROXY FOR: !MODE_NAME!
echo ========================================================
echo.
if defined DETECTED_GW (
    echo   Auto-detected Gateway (Phone IP): !DETECTED_GW!
    echo.
    set "USER_INPUT_IP="
    set /p "USER_INPUT_IP=   Phone IP [Press ENTER to use !DETECTED_GW!]: "
    if defined USER_INPUT_IP (
        set "PHONE_IP=!USER_INPUT_IP!"
    ) else (
        set "PHONE_IP=!DETECTED_GW!"
    )
    goto :APPLY_PROXY
) else (
    echo   Could not automatically detect Default Gateway.
    echo   (Ensure phone is connected with USB Tethering or Wi-Fi Hotspot on)
    echo.
    set "PHONE_IP="
    set /p "PHONE_IP=   Phone IP [e.g. 192.168.42.129]: "
    if not defined PHONE_IP (
        echo.
        echo   No IP entered - returning to menu.
        timeout /t 2 >nul
        goto :MENU
    )
    goto :APPLY_PROXY
)

:APPLY_PROXY
set "SELECTED_PROXY=!PHONE_IP!:!PROXY_PORT!"

echo.
echo [1/3] Enabling Windows System Proxy ^(!SELECTED_PROXY!^)...
reg add "%REG_KEY%" /v ProxyEnable /t REG_DWORD /d 1 /f >nul
reg add "%REG_KEY%" /v ProxyServer /t REG_SZ /d "!SELECTED_PROXY!" /f >nul
reg add "%REG_KEY%" /v ProxyOverride /t REG_SZ /d "%PROXY_OVERRIDE%" /f >nul

if "!IS_ADMIN!"=="1" (
    echo [2/3] Enabling TCP Window Auto-Tuning...
    netsh int tcp set global autotuninglevel=normal >nul 2>&1
    echo [3/3] Disabling TCP Timestamps (Low Latency)...
    netsh int tcp set global timestamps=disabled >nul 2>&1
) else (
    echo [2/3] Skipping TCP tweaks (Run as Administrator for full optimization)
    echo [3/3] Proxy configured successfully
)

:: Refresh WinINet so browsers pick up change immediately
call :REFRESH_WININET

echo.
echo ========================================================
echo   PROXY IS NOW: ONLINE
echo   - Mode          : !MODE_NAME!
echo   - Proxy Address : !SELECTED_PROXY!
echo   - Bypass List   : !PROXY_OVERRIDE!
if "!IS_ADMIN!"=="1" (
    echo   - TCP AutoTune  : NORMAL
    echo   - TCP Timestamps: DISABLED
)
echo ========================================================
echo.
echo   Configuration applied. Window will close now.
pause
exit /b 0

:: =============================================================
:: TURN OFF PROXY and RESTORE DEFAULT TCP
:: =============================================================
:DISABLE_PROXY
echo.
echo [1/3] Disabling Windows System Proxy...
reg add "%REG_KEY%" /v ProxyEnable /t REG_DWORD /d 0 /f >nul

if "!IS_ADMIN!"=="1" (
    echo [2/3] Restoring TCP Window Auto-Tuning...
    netsh int tcp set global autotuninglevel=normal >nul 2>&1
    echo [3/3] Restoring TCP Timestamps to default...
    netsh int tcp set global timestamps=allowed >nul 2>&1
) else (
    echo [2/3] Skipping TCP restore (Not running as Admin)
    echo [3/3] Proxy disabled successfully
)

:: Refresh WinINet so browsers pick up change immediately
call :REFRESH_WININET

echo.
echo ========================================================
echo   PROXY IS NOW: OFFLINE
echo   - Direct Connection restored
if "!IS_ADMIN!"=="1" (
    echo   - TCP settings restored to defaults
)
echo ========================================================
echo.
echo   Proxy disabled. Window will close now.
pause
exit /b 0

:: =============================================================
:: SUBROUTINE: Refresh WinINet settings (no browser restart)
:: =============================================================
:REFRESH_WININET
powershell -NoProfile -Command "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class WinInet { [DllImport(\"wininet.dll\", SetLastError=true)] public static extern bool InternetSetOption(IntPtr h, int o, IntPtr b, int l); }'; $z=[IntPtr]::Zero; [WinInet]::InternetSetOption($z,39,$z,0); [WinInet]::InternetSetOption($z,37,$z,0)" >nul 2>&1
goto :eof
