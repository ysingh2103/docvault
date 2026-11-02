# WINWEB01 - install IIS, then URL Rewrite and Application Request Routing (ARR).
# Run in PowerShell (Admin). Needs internet access for the two downloads.

Install-WindowsFeature Web-Server -IncludeManagementTools

# IIS should answer on its own default page: expect 200.
(Invoke-WebRequest http://localhost -UseBasicParsing).StatusCode

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
New-Item -ItemType Directory -Force C:\setup | Out-Null
Invoke-WebRequest 'https://download.microsoft.com/download/1/2/8/128E2E22-C1B9-44A4-BE2A-5859ED1D4592/rewrite_amd64_en-US.msi' -OutFile C:\setup\rewrite.msi
Invoke-WebRequest 'https://download.microsoft.com/download/E/9/8/E9849D6A-020E-47E4-9FD0-A023E99B54EB/requestRouter_amd64.msi' -OutFile C:\setup\arr.msi

# Order matters: ARR needs URL Rewrite installed first.
Start-Process msiexec.exe -ArgumentList '/i C:\setup\rewrite.msi /qn' -Wait
Start-Process msiexec.exe -ArgumentList '/i C:\setup\arr.msi /qn' -Wait

Get-WmiObject Win32_Product |
    Where-Object { $_.Name -match 'URL Rewrite|Application Request Routing' } |
    Select-Object Name, Version
