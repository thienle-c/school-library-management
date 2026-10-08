@echo off
setlocal
set SERVER_IP=%1
if "%SERVER_IP%"=="" (
    set /p SERVER_IP="Nhap dia chi IP cua may Server (vi du: 10.15.38.111): "
)
if "%SERVER_IP%"=="" set SERVER_IP=127.0.0.1

echo.
echo ==========================================================
echo  DANG KIEM TRA KET NOI TOI SERVER: %SERVER_IP%
echo ==========================================================
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ip='%SERVER_IP%'; Write-Host '1. Kiem tra Port 1099 (RMI Registry)...' -NoNewline; try { $c=New-Object Net.Sockets.TcpClient; $t=$c.ConnectAsync($ip, 1099); if ($t.Wait(3000) -and $c.Connected) { $c.Close(); Write-Host ' [OK] CONG 1099 KET NOI TOT' -ForegroundColor Green } else { $c.Close(); Write-Host ' [FAIL] BI CHAN HOAC CHUA BAT' -ForegroundColor Red } } catch { Write-Host ' [FAIL] LOI KET NOI' -ForegroundColor Red }; Write-Host '2. Kiem tra Port 1100 (RMI Remote Object)...' -NoNewline; try { $c2=New-Object Net.Sockets.TcpClient; $t2=$c2.ConnectAsync($ip, 1100); if ($t2.Wait(3000) -and $c2.Connected) { $c2.Close(); Write-Host ' [OK] CONG 1100 KET NOI TOT' -ForegroundColor Green } else { $c2.Close(); Write-Host ' [FAIL] BI CHAN HOAC CHUA BAT' -ForegroundColor Red } } catch { Write-Host ' [FAIL] LOI KET NOI' -ForegroundColor Red }"
echo.
pause
