# WINWEB01 - give the server a fixed address.
# Run in PowerShell (Admin) on the VM console. Edit the values below first.
# Why fixed: IIS and the other servers must find this machine at the same address every time.
param(
    [string]$Alias   = 'Ethernet0',
    [string]$Ip      = '192.168.49.214',
    [int]   $Prefix  = 24,              # 24 = 255.255.255.0
    [string]$Gateway = '192.168.49.1',
    [string[]]$Dns   = @('192.168.49.1', '8.8.8.8')
)

# Turn DHCP off and clear any old address, so New-NetIPAddress does not clash with it.
Set-NetIPInterface -InterfaceAlias $Alias -Dhcp Disabled
Get-NetIPAddress -InterfaceAlias $Alias -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Remove-NetIPAddress -Confirm:$false -ErrorAction SilentlyContinue
Get-NetRoute -InterfaceAlias $Alias -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
    Remove-NetRoute -Confirm:$false -ErrorAction SilentlyContinue

New-NetIPAddress -InterfaceAlias $Alias -IPAddress $Ip -PrefixLength $Prefix -DefaultGateway $Gateway | Out-Null
Set-DnsClientServerAddress -InterfaceAlias $Alias -ServerAddresses $Dns

ipconfig
Test-NetConnection google.com -Port 443    # TcpTestSucceeded should be True
