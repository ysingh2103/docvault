# Trainer scenario W1: stop the IIS site, then restore it.
# Run on WINWEB01 (PowerShell Admin). Diagnose from another PC between -Break and -Fix.
# Runbook: docs/runbooks/win-01-iis-site-stopped.md
param(
    [switch]$Break,
    [switch]$Fix,
    [string]$Site = 'Default Web Site'
)
if ($Break -eq $Fix) { throw 'Use exactly one of -Break or -Fix.' }
Import-Module WebAdministration
if ($Break) { Stop-Website $Site; 'Site stopped. Port 80 should now refuse connections.' }
if ($Fix)   { Start-Website $Site; 'Site started.' }
Get-Website $Site | Format-Table Name, State, Bindings -AutoSize
