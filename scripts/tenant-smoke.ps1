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

if (-not $prov.codigoEmpresa) { Bad "TF-05b" "falta codigoEmpresa" }
elseif ($prov.loginUrl -notlike "*empresa=$($prov.codigoEmpresa)*") { Bad "TF-05b" "loginUrl=$($prov.loginUrl)" }
else { Ok "TF-05b" $prov.codigoEmpresa }

$codigoA = $prov.codigoEmpresa
$tenantLogin = Invoke-Json POST "$Base/auth/login" @{
  codigoEmpresa = $codigoA
  numeroUsuario = $adminUser
  password      = "TenantPass123"
} $null
if ((Test-JsonError -Resp $tenantLogin) -or -not $tenantLogin.token) { Bad "TF-06" ($tenantLogin.message) }
elseif ($tenantLogin.platformAdmin -eq $true) { Bad "TF-06" "platformAdmin deberia ser false" }
else { Ok "TF-06" "tenant token ok" }

$wrongCode = Invoke-Json POST "$Base/auth/login" @{
  codigoEmpresa = "NOPE-0000"
  numeroUsuario = $adminUser
  password      = "TenantPass123"
} $null
if ((Test-JsonError -Resp $wrongCode) -and $wrongCode.status -in 401, 403) { Ok "TF-06b" "codigo ajeno rechazado" }
elseif (Test-JsonError -Resp $wrongCode) { Bad "TF-06b" "status $($wrongCode.status)" }
else { Bad "TF-06b" "debio ser 401" }

# ── Banco de Datos / licencia (Fase 1): núcleo 200, premium 403 en Base ────
$feats = @()
if ($null -ne $tenantLogin.licensedFeatures) { $feats = @($tenantLogin.licensedFeatures) }
if ($feats -contains "TRAININGS" -or $feats -contains "EXAMS") {
  Bad "TF-BD-01" "Base no debe tener TRAININGS/EXAMS: [$($feats -join ',')]"
} else {
  Ok "TF-BD-01" "licensedFeatures sin premium ($($feats.Count) codes)"
}

$usersBd = Invoke-Json GET "$Base/users" $null $tenantLogin.token
if (Test-JsonError -Resp $usersBd) { Bad "TF-BD-02" "users status $($usersBd.status)" }
else { Ok "TF-BD-02" "GET /users 200" }

$storesBd = Invoke-Json GET "$Base/stores" $null $tenantLogin.token
if (Test-JsonError -Resp $storesBd) { Bad "TF-BD-03" "stores status $($storesBd.status)" }
else { Ok "TF-BD-03" "GET /stores 200" }

$puestosBd = Invoke-Json GET "$Base/catalogs/PUESTO" $null $tenantLogin.token
if (Test-JsonError -Resp $puestosBd) { Bad "TF-BD-04" "catalogs/PUESTO $($puestosBd.status)" }
elseif (@($puestosBd).Count -lt 1) { Bad "TF-BD-04" "seed PUESTO vacio" }
else { Ok "TF-BD-04" "GET /catalogs/PUESTO seed ok" }

$taskTplBd = Invoke-Json GET "$Base/task-templates" $null $tenantLogin.token
if (Test-JsonError -Resp $taskTplBd) { Bad "TF-BD-05" "task-templates $($taskTplBd.status)" }
else { Ok "TF-BD-05" "GET /task-templates 200" }

$qbankBd = Invoke-Json GET "$Base/question-bank" $null $tenantLogin.token
if ((Test-JsonError -Resp $qbankBd) -and $qbankBd.status -eq 403) {
  if ($qbankBd.body -match '"error"') { Ok "TF-BD-10" "question-bank 403+error" }
  else { Ok "TF-BD-10" "question-bank 403" }
} elseif (Test-JsonError -Resp $qbankBd) { Bad "TF-BD-10" "status $($qbankBd.status)" }
else { Bad "TF-BD-10" "Base no debe listar question-bank" }

$ttBd = Invoke-Json GET "$Base/training-templates" $null $tenantLogin.token
if ((Test-JsonError -Resp $ttBd) -and $ttBd.status -eq 403) { Ok "TF-BD-11" "training-templates 403" }
elseif (Test-JsonError -Resp $ttBd) { Bad "TF-BD-11" "status $($ttBd.status)" }
else { Bad "TF-BD-11" "Base no debe listar training-templates" }

$tmBd = Invoke-Json GET "$Base/training-materials" $null $tenantLogin.token
if ((Test-JsonError -Resp $tmBd) -and $tmBd.status -eq 403) { Ok "TF-BD-12" "training-materials 403" }
elseif (Test-JsonError -Resp $tmBd) { Bad "TF-BD-12" "status $($tmBd.status)" }
else { Bad "TF-BD-12" "Base no debe listar training-materials" }

$examsBd = Invoke-Json GET "$Base/exams" $null $tenantLogin.token
if ((Test-JsonError -Resp $examsBd) -and $examsBd.status -eq 403) { Ok "TF-BD-13" "exams 403" }
elseif (Test-JsonError -Resp $examsBd) { Bad "TF-BD-13" "status $($examsBd.status)" }
else { Bad "TF-BD-13" "Base no debe listar exams" }

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
  codigoEmpresa = "METRIX"
  numeroUsuario = "ADMIN001"
  password      = "Admin123456"
} $null
if ((Test-JsonError -Resp $admin0) -or -not $admin0.token) { Bad "TF-08" ($admin0.message) }
elseif ($admin0.platformAdmin -ne $true) { Bad "TF-08" "Admin 0 sin platformAdmin" }
else { Ok "TF-08" "Admin 0 ok" }

$admin0Wrong = Invoke-Json POST "$Base/auth/login" @{
  codigoEmpresa = "METRIX"
  numeroUsuario = $adminUser
  password      = "TenantPass123"
} $null
if ((Test-JsonError -Resp $admin0Wrong) -and $admin0Wrong.status -in 401, 403) { Ok "TF-08b" "METRIX no autentica tenant" }
elseif (Test-JsonError -Resp $admin0Wrong) { Bad "TF-08b" "status $($admin0Wrong.status)" }
else { Bad "TF-08b" "debio ser 401" }

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
  codigoEmpresa = $codigoA
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
    codigoEmpresa = $codigoA
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

# ── Login por codigoEmpresa: mismo ADMIN001 en dos tenants ───────────────
$orderB = Invoke-Json POST "$Base/productos/orders" @{
  packageId             = $PackageId
  empresaNombre         = "QA Tenant B $suffix"
  contactoNombre        = "QA B"
  contactoEmail         = "qab$suffix@metrix.test"
  sucursalesContratadas = 1
} $null
if ((Test-JsonError -Resp $orderB) -or -not $orderB.id) { Bad "TF-18" ($orderB.message) }
else {
  $paidB = Invoke-Json POST "$Base/productos/orders/$($orderB.id)/pay" @{
    cardholderName = "QA"
    cardNumber     = "4242424242424242"
    expiryMonth    = "12"
    expiryYear     = "29"
    cvv            = "123"
  } $null
  $provB = Invoke-Json POST "$Base/productos/orders/$($orderB.id)/provision" @{
    numeroUsuario   = "ADMIN001"
    password        = "TenantBPass123"
    confirmPassword = "TenantBPass123"
    adminNombre     = "Admin B"
  } $null
  if ((Test-JsonError -Resp $provB) -or -not $provB.codigoEmpresa) { Bad "TF-18" ($provB.message) }
  elseif ($provB.codigoEmpresa -eq $codigoA) { Bad "TF-18" "codigos iguales" }
  else { Ok "TF-18" "ADMIN001 en tenant B $($provB.codigoEmpresa)" }

  $loginA = Invoke-Json POST "$Base/auth/login" @{
    codigoEmpresa = $codigoA
    numeroUsuario = $adminUser
    password      = "TenantPass123"
  } $null
  $loginB = Invoke-Json POST "$Base/auth/login" @{
    codigoEmpresa = $provB.codigoEmpresa
    numeroUsuario = "ADMIN001"
    password      = "TenantBPass123"
  } $null
  if ((Test-JsonError -Resp $loginA) -or (Test-JsonError -Resp $loginB)) {
    Bad "TF-19" "login A o B fallo"
  } elseif ($loginA.databaseName -eq $loginB.databaseName) {
    Bad "TF-19" "misma databaseName"
  } else { Ok "TF-19" "BDs distintas" }

  if (-not (Test-JsonError -Resp $loginB) -and $loginB.token) {
    $storeB = Invoke-Json POST "$Base/stores" @{ nombre = "Sucursal B $suffix" } $loginB.token
    $gerB = Invoke-Json POST "$Base/users" @{
      nombre        = "Gerente B"
      puesto        = "Gerente"
      storeId       = $storeB.id
      turno         = "MATUTINO"
      numeroUsuario = $gerenteUser
      password      = "GerenteBPass1"
      roles         = @("GERENTE")
    } $loginB.token
    if ((Test-JsonError -Resp $gerB) -or -not $gerB.id) { Bad "TF-20" ($gerB.message) }
    else { Ok "TF-20" "GERENTE repetido en B" }

    $gerBLogin = Invoke-Json POST "$Base/auth/login" @{
      codigoEmpresa = $provB.codigoEmpresa
      numeroUsuario = $gerenteUser
      password      = "GerenteBPass1"
    } $null
    if ((Test-JsonError -Resp $gerBLogin) -or -not $gerBLogin.token) { Bad "TF-21" ($gerBLogin.message) }
    elseif ($gerBLogin.databaseName -ne $loginB.databaseName) { Bad "TF-21" "BD distinta a B" }
    else { Ok "TF-21" "GERENTE B en su codigo" }
  } else {
    Bad "TF-20" "skip"
    Bad "TF-21" "skip"
  }

  $crossPass = Invoke-Json POST "$Base/auth/login" @{
    codigoEmpresa = $codigoA
    numeroUsuario = "ADMIN001"
    password      = "TenantBPass123"
  } $null
  if ((Test-JsonError -Resp $crossPass) -and $crossPass.status -in 401, 403) { Ok "TF-22" "password de B no entra a A" }
  elseif (Test-JsonError -Resp $crossPass) { Bad "TF-22" "status $($crossPass.status)" }
  else { Bad "TF-22" "debio ser 401" }
}

if ($fail -gt 0) { Write-Host "`n$fail fallos" -ForegroundColor Red; exit 1 }
Write-Host "`nSmoke tenant OK" -ForegroundColor Green
exit 0
