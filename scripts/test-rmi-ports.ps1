param(
    [string]$ServerIP = "127.0.0.1"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " KIEM TRA KET NOI MANG JAVA RMI (CLIENT -> SERVER)" -ForegroundColor Cyan
Write-Host " Target Server IP: $ServerIP" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

function Check-RmiPort {
    param (
        [string]$IP,
        [int]$Port,
        [string]$Description
    )
    Write-Host ""
    Write-Host "[+] Dang kiem tra cong $Port ($Description)..." -NoNewline
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($IP, $Port)
        $ok = $task.Wait(3000)
        if ($ok -and $client.Connected) {
            $client.Close()
            Write-Host " [ THANH CONG (OPEN) ]" -ForegroundColor Green
            return $true
        } else {
            $client.Close()
            Write-Host " [ BI CHAN / TIMEOUT ]" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host " [ LOI KET NOI ]" -ForegroundColor Red
        return $false
    }
}

$port1099 = Check-RmiPort -IP $ServerIP -Port 1099 -Description "RMI Registry"
$port1100 = Check-RmiPort -IP $ServerIP -Port 1100 -Description "RMI Remote Object"

Write-Host ""
Write-Host "----------------------------------------------------------" -ForegroundColor Gray
Write-Host " TONG HOP KET QUA KIEM TRA:" -ForegroundColor Yellow

if ($port1099 -and $port1100) {
    Write-Host "  -> CA HAI CONG 1099 VA 1100 DEU MO THANH CONG!" -ForegroundColor Green
    Write-Host "  -> Client co the ket noi hoan chinh qua Java RMI." -ForegroundColor Green
} else {
    Write-Host "  -> PHAT HIEN CONG BI CHAN:" -ForegroundColor Red
    if (-not $port1099) {
        Write-Host "     - Cong 1099 (RMI Registry): KHONG THE KET NOI!" -ForegroundColor Red
        Write-Host "       Nguyen nhan: Server chua khoi dong hoac Firewall chan cong 1099." -ForegroundColor Gray
    }
    if (-not $port1100) {
        Write-Host "     - Cong 1100 (RMI Remote Object): KHONG THE KET NOI!" -ForegroundColor Red
        Write-Host "       Nguyen nhan: Cong 1100 chua duoc mo tren Firewall cua may Server." -ForegroundColor Gray
    }

    Write-Host ""
    Write-Host "[HUONG DAN KHAC PHUC TREN MAY SERVER]:" -ForegroundColor Yellow
    Write-Host "Mo PowerShell quyen 'Run as Administrator' tren may Server va chay:" -ForegroundColor White
    Write-Host "New-NetFirewallRule -DisplayName 'School Library RMI Registry' -Direction Inbound -LocalPort 1099 -Protocol TCP -Action Allow" -ForegroundColor Cyan
    Write-Host "New-NetFirewallRule -DisplayName 'School Library RMI Object' -Direction Inbound -LocalPort 1100 -Protocol TCP -Action Allow" -ForegroundColor Cyan
}

Write-Host "==========================================================" -ForegroundColor Cyan
