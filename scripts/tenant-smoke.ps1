param(
  [string]$Base = "http://localhost:8080/api/v1",
  [string]$PackageId = "base"
)

$ErrorActionPreference = "Stop"
$fail = 0
function Ok($id, $msg) { Write-Host "PASS $id $msg" -ForegroundColor Green }
function Bad($id, $msg) { Write-Host "FAIL $id $msg" -ForegroundColor Red; $script:fail++ }

function Invoke-Json {
  param($Method, $Url, $Body, $Token)
  $headers = @{ "Content-Type" = "application/json" }
  if ($Token) { $headers["Authorization"] = "Bearer $Token" }
  try {
    if ($null -ne $Body) {
      return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -Body ($Body | ConvertTo-Json -Depth 6)
    }
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers
  } catch {
    $resp = $_.Exception.Response
    $code = if ($resp) { [int]$resp.StatusCode } else { 0 }
    $bodyText = $null
    if ($resp -and $resp.GetResponseStream()) {
      $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
      $bodyText = $reader.ReadToEnd()
      $reader.Close()
    }
    return @{ __error = $true; status = $code; message = $_.Exception.Message; body = $bodyText }
  }
}

function Test-JsonError {
  param($Resp)
  return ($Resp -is [hashtable] -or $Resp -is [System.Collections.IDictionary]) -and $Resp.__error
}

$catalog = Invoke-Json GET "$Base/productos/catalog" $null $null
if (Test-JsonError -Resp $catalog) { Bad "TF-01" $catalog.message } else {
  $ids = @($catalog | ForEach-Object { $_.id })
  if ($ids -contains $PackageId) { Ok "TF-01" "catalog $($catalog.Count) planes" } else { Bad "TF-01" "falta paquete $PackageId" }
}

$suffix = Get-Random -Maximum 99999
$order = Invoke-Json POST "$Base/productos/orders" @{
  packageId             = $PackageId
  empresaNombre         = "QA Tenant $suffix"
  contactoNombre        = "QA Contact"
  contactoEmail         = "qa$suffix@metrix.test"
  sucursalesContratadas = 1
} $null
if ((Test-JsonError -Resp $order) -or -not $order.id) { Bad "TF-02" ($order.message); exit 1 }
else { Ok "TF-02" "order $($order.id) $($order.status)" }

$paid = Invoke-Json POST "$Base/productos/orders/$($order.id)/pay" @{
  cardholderName = "QA"
  cardNumber     = "4242424242424242"
  expiryMonth    = "12"
  expiryYear     = "29"
  cvv            = "123"
} $null
if ((Test-JsonError -Resp $paid) -or $paid.status -ne "PAID") { Bad "TF-03" ($paid.message) } else { Ok "TF-03" "PAID" }

$reject = Invoke-Json POST "$Base/productos/orders" @{
  packageId             = $PackageId
  empresaNombre         = "QA Reject $suffix"
  contactoNombre        = "QA"
  contactoEmail         = "rej$suffix@metrix.test"
  sucursalesContratadas = 1
} $null
if (-not (Test-JsonError -Resp $reject) -and $reject.id) {
  $rejPay = Invoke-Json POST "$Base/productos/orders/$($reject.id)/pay" @{
    cardholderName = "QA"
    cardNumber     = "4242424242420000"
    expiryMonth    = "12"
    expiryYear     = "29"
    cvv            = "123"
  } $null
  if ($rejPay.status -eq "PAID") { Bad "TF-04" "debio rechazar, status=PAID" }
  elseif ((Test-JsonError -Resp $rejPay) -or $rejPay.status -ne "PAID") { Ok "TF-04" "pago rechazado" }
  else { Bad "TF-04" "status=$($rejPay.status)" }
}

$adminUser = "QA$suffix"
$prov = Invoke-Json POST "$Base/productos/orders/$($order.id)/provision" @{
  numeroUsuario   = $adminUser
  password        = "TenantPass123"
  confirmPassword = "TenantPass123"
  adminNombre     = "QA Admin"
} $null
if ((Test-JsonError -Resp $prov) -or -not $prov.databaseName) { Bad "TF-05" ($prov.message) }
elseif ($prov.databaseName -notlike "metrix_tenant_*") { Bad "TF-05" "databaseName=$($prov.databaseName)" }
else { Ok "TF-05" $prov.databaseName }

$tenantLogin = Invoke-Json POST "$Base/auth/login" @{
  numeroUsuario = $adminUser
  password      = "TenantPass123"
} $null
if ((Test-JsonError -Resp $tenantLogin) -or -not $tenantLogin.token) { Bad "TF-06" ($tenantLogin.message) }
elseif ($tenantLogin.platformAdmin -eq $true) { Bad "TF-06" "platformAdmin deberia ser false" }
else { Ok "TF-06" "tenant token ok" }

$forbidden = Invoke-Json GET "$Base/platform/instances" $null $tenantLogin.token
if ((Test-JsonError -Resp $forbidden) -and $forbidden.status -in 401, 403) { Ok "TF-07" "tenant blocked from /platform" }
elseif (Test-JsonError -Resp $forbidden) { Bad "TF-07" "status $($forbidden.status)" }
else { Bad "TF-07" "tenant listo instancias" }

$forbiddenPkgs = Invoke-Json GET "$Base/license-packages" $null $tenantLogin.token
if ((Test-JsonError -Resp $forbiddenPkgs) -and $forbiddenPkgs.status -in 401, 403) {
  Ok "TF-07b" "tenant blocked from /license-packages"
} elseif (Test-JsonError -Resp $forbiddenPkgs) {
  Bad "TF-07b" "status $($forbiddenPkgs.status)"
} else {
  Bad "TF-07b" "tenant listo paquetes de licencia"
}

$admin0 = Invoke-Json POST "$Base/auth/login" @{
  numeroUsuario = "ADMIN001"
  password      = "Admin123456"
} $null
if ((Test-JsonError -Resp $admin0) -or -not $admin0.token) { Bad "TF-08" ($admin0.message) }
elseif ($admin0.platformAdmin -ne $true) { Bad "TF-08" "Admin 0 sin platformAdmin" }
else { Ok "TF-08" "Admin 0 ok" }

$instances = Invoke-Json GET "$Base/platform/instances" $null $admin0.token
$instanceId = $null
if (Test-JsonError -Resp $instances) { Bad "TF-09" $instances.message }
else {
  $match = @($instances | Where-Object { $_.empresaNombre -eq "QA Tenant $suffix" })
  if ($match.Count -gt 0) {
    $instanceId = $match[0].id
    Ok "TF-09" "instancia visible a Admin 0"
  } else {
    Bad "TF-09" "no aparece QA Tenant $suffix"
  }
}

# ── Fase 0/1: sucursal + GERENTE + login + gates Base ─────────────────────
$store = Invoke-Json POST "$Base/stores" @{
  nombre = "Sucursal QA $suffix"
} $tenantLogin.token
if ((Test-JsonError -Resp $store) -or -not $store.id) { Bad "TF-10" ("store: " + $store.message) }
else { Ok "TF-10" "store $($store.id)" }

$gerenteUser = "QG$suffix"
$gerente = Invoke-Json POST "$Base/users" @{
  nombre        = "Gerente QA $suffix"
  puesto        = "Gerente"
  storeId       = $store.id
  turno         = "MATUTINO"
  numeroUsuario = $gerenteUser
  password      = "GerentePass123"
  roles         = @("GERENTE")
} $tenantLogin.token
if ((Test-JsonError -Resp $gerente) -or -not $gerente.id) { Bad "TF-11" ("create gerente: " + $gerente.message + " " + $gerente.body) }
else { Ok "TF-11" "gerente $($gerente.numeroUsuario)" }

$gerenteLogin = Invoke-Json POST "$Base/auth/login" @{
  numeroUsuario = $gerenteUser
  password      = "GerentePass123"
} $null
if ((Test-JsonError -Resp $gerenteLogin) -or -not $gerenteLogin.token) { Bad "TF-12" ($gerenteLogin.message) }
elseif ($gerenteLogin.platformAdmin -eq $true) { Bad "TF-12" "gerente no debe ser platformAdmin" }
else { Ok "TF-12" "GERENTE login ok" }

$exams = Invoke-Json GET "$Base/exams/store/$($store.id)" $null $tenantLogin.token
if ((Test-JsonError -Resp $exams) -and $exams.status -in 401, 403) { Ok "TF-13" "Base blocked from /exams" }
elseif (Test-JsonError -Resp $exams) { Bad "TF-13" "status $($exams.status)" }
else { Bad "TF-13" "Base plan no debe listar exams" }

$store2 = Invoke-Json POST "$Base/stores" @{
  nombre = "Sucursal Extra $suffix"
} $tenantLogin.token
if ((Test-JsonError -Resp $store2) -and ($store2.status -in 400, 409, 422)) {
  Ok "TF-14" "limite sucursales Base (contratadas=1)"
} elseif (Test-JsonError -Resp $store2) {
  Bad "TF-14" "status $($store2.status) $($store2.body)"
} else {
  Bad "TF-14" "debio bloquear 2a sucursal"
}

# ── Fase 2: suspender ────────────────────────────────────────────────────
if ($instanceId) {
  $suspended = Invoke-Json PATCH "$Base/platform/instances/$instanceId/status" @{
    status = "SUSPENDED"
  } $admin0.token
  if ((Test-JsonError -Resp $suspended) -or $suspended.status -ne "SUSPENDED") {
    Bad "TF-15" ($suspended.message)
  } else {
    Ok "TF-15" "instancia suspendida"
  }

  $blockedLogin = Invoke-Json POST "$Base/auth/login" @{
    numeroUsuario = $adminUser
    password      = "TenantPass123"
  } $null
  if ((Test-JsonError -Resp $blockedLogin) -and $blockedLogin.status -in 401, 403, 422) {
    Ok "TF-16" "login rechazado tras suspender"
  } elseif (Test-JsonError -Resp $blockedLogin) {
    Bad "TF-16" "status $($blockedLogin.status)"
  } else {
    Bad "TF-16" "login debio fallar"
  }

  $reactivated = Invoke-Json PATCH "$Base/platform/instances/$instanceId/status" @{
    status = "ACTIVE"
  } $admin0.token
  if ((Test-JsonError -Resp $reactivated) -or $reactivated.status -ne "ACTIVE") {
    Bad "TF-17" ($reactivated.message)
  } else {
    Ok "TF-17" "instancia reactivada"
  }
} else {
  Bad "TF-15" "sin instanceId"
  Bad "TF-16" "skip"
  Bad "TF-17" "skip"
}

if ($fail -gt 0) { Write-Host "`n$fail fallos" -ForegroundColor Red; exit 1 }
Write-Host "`nSmoke tenant OK" -ForegroundColor Green
exit 0
