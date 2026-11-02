# WINWEB01 - make IIS a reverse proxy: /docvault/... goes to NGINX on the Linux VM.
# Path: Browser -> IIS (:80) -> NGINX (:8081) -> Tomcat (:8080) -> DocVault -> PostgreSQL
# Safe to run twice: it removes the old rule before adding it again.
param(
    [string]$Upstream = 'http://192.168.49.172:8081'   # NGINX on the Linux VM
)
Import-Module WebAdministration

# ARR is switched off until this is set.
# preserveHostHeader keeps the address the user typed, so DocVault redirects come back through IIS.
Set-WebConfigurationProperty -PSPath 'MACHINE/WEBROOT/APPHOST' -Filter 'system.webServer/proxy' -Name enabled -Value $true
Set-WebConfigurationProperty -PSPath 'MACHINE/WEBROOT/APPHOST' -Filter 'system.webServer/proxy' -Name preserveHostHeader -Value $true

$site = 'MACHINE/WEBROOT/APPHOST/Default Web Site'
Clear-WebConfiguration -PSPath $site -Filter "system.webServer/rewrite/rules/rule[@name='DocVault']" -ErrorAction SilentlyContinue
Add-WebConfigurationProperty -PSPath $site -Filter 'system.webServer/rewrite/rules' -Name '.' `
    -Value @{ name = 'DocVault'; patternSyntax = 'ECMAScript'; stopProcessing = 'True' }
$rule = "system.webServer/rewrite/rules/rule[@name='DocVault']"
Set-WebConfigurationProperty -PSPath $site -Filter "$rule/match"  -Name url  -Value '^docvault(/.*)?$'
Set-WebConfigurationProperty -PSPath $site -Filter "$rule/action" -Name type -Value 'Rewrite'
Set-WebConfigurationProperty -PSPath $site -Filter "$rule/action" -Name url  -Value "$Upstream/docvault{R:1}"

# Check: expect 200 and an X-Request-ID made by NGINX.
$r = Invoke-WebRequest http://localhost/docvault/login -UseBasicParsing
$r.StatusCode
$r.Headers['X-Request-ID']
