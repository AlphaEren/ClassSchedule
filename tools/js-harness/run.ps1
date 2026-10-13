<#
  Headless-Chrome harness for the injected timetable scraper.

  It pulls RAW_SCRAPER_JS out of TimetableExtractor.kt and NetSniffer.installJs out of
  NetSniffer.kt, wraps every cases/*.html page with those scripts and prints what the
  scraper returns. No emulator needed, so JS changes can be iterated in seconds.
#>
param(
  [string]$Filter = "*",
  [string]$Root = "",
  [string]$Chrome = ""
)
$ErrorActionPreference = "Stop"
if (-not $Root) { $Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path }
$kT = Join-Path $Root "app\src\main\java\com\lorat\classschedule\data\TimetableExtractor.kt"
$kN = Join-Path $Root "app\src\main\java\com\lorat\classschedule\ui\NetSniffer.kt"
if (-not $Chrome) {
  $cands = @(
    "C:\Program Files\Google\Chrome\Application\chrome.exe",
    "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
  )
  $Chrome = ($cands | Where-Object { Test-Path $_ } | Select-Object -First 1)
}
if (-not $Chrome) { throw "no Chrome/Edge found, pass -Chrome <path>" }

$src = [System.IO.File]::ReadAllText($kT, [System.Text.Encoding]::UTF8)
$m = [regex]::Match($src, '(?s)RAW_SCRAPER_JS: String = """\r?\n(.*?)\r?\n"""')
if (-not $m.Success) { throw "RAW_SCRAPER_JS not found in $kT" }
$js = $m.Groups[1].Value.Replace('__PRESET__', 'auto')

$nsrc = [System.IO.File]::ReadAllText($kN, [System.Text.Encoding]::UTF8)
$hm = [regex]::Match($nsrc, '(?s)installJs: String = """\r?\n(.*?)\r?\n"""')
if (-not $hm.Success) { throw "installJs not found in $kN" }
$hook = $hm.Groups[1].Value

$tmp = Join-Path $env:TEMP "cs-harness"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$prof = Join-Path $tmp "profile"
Get-Process chrome, msedge -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 400

Get-ChildItem (Join-Path $PSScriptRoot "cases") -Filter *.html | Where-Object { $_.Name -like $Filter } | ForEach-Object {
  $body = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
  # the hook is injected into <head> so page scripts fire their XHR/fetch after it is armed
  $page = '<!doctype html><html><head><meta charset="utf-8"><style>body{font-family:sans-serif;font-size:12px;margin:0}*{box-sizing:border-box}</style><script>' +
          $hook + '</' + 'script></head><body>' + $body +
          '<pre id="RESULT"></pre><script>setTimeout(function(){var __r=' + $js +
          ';document.getElementById("RESULT").textContent="@@OK@@"+__r+"@@END@@";},400);</' + 'script></body></html>'
  $out = Join-Path $tmp ($_.BaseName + ".html")
  [System.IO.File]::WriteAllText($out, $page, (New-Object System.Text.UTF8Encoding($false)))
  $ua = @('--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--disable-extensions',
          '--allow-file-access-from-files', '--virtual-time-budget=4000', "--user-data-dir=$prof", '--dump-dom',
          ("file:///" + ($out -replace '\\', '/')))
  $dom = (& $Chrome @ua 2>&1 | Out-String)
  "==== $($_.BaseName) ===="
  $mm = [regex]::Match($dom, '@@OK@@(.*?)@@END@@', [System.Text.RegularExpressions.RegexOptions]::Singleline)
  if (-not $mm.Success) { "NO RESULT"; ""; return }
  $o = $mm.Groups[1].Value | ConvertFrom-Json
  "ok=$($o.ok) mode=$($o.mode) preset=$($o.preset) system=$($o.system)"
  "diag=$($o.diagnostics)"
  if ($o.error) { "error=$($o.error) sample=$($o.sample)" }
  if ($o.st) { "sections=$($o.st.Count)  first=$($o.st[0].s)-$($o.st[0].e)  last=$($o.st[-1].s)-$($o.st[-1].e)" }
  if ($o.week) { "sourceCurrentWeek=$($o.week)  semesterStart=$($o.start)  lastTeachingWeek=$($o.weeks)" }
  if ($o.courses) {
    "courses=$($o.courses.Count)"
    foreach ($c in $o.courses) { "  {0} | d{1} {2}-{3} | w[{4}] | {5} | {6}" -f $c.n, $c.d, $c.s, $c.e, ($c.w -join ','), $c.t, $c.r }
  }
  if ($o.rows) {
    "rows=$($o.rows.Count)"
    foreach ($r in $o.rows) { "  " + (($r | ForEach-Object { $t = $_.t; if ($t) { $t -replace "`n", "/" } else { "." } }) -join " | ") }
  }
  ""
}