param([string]$Base = 'http://127.0.0.1:8080/api/v1', [string]$ResumeOrderId, [string]$ResumeCustomerEmail, [switch]$AfterDelivery)
$ErrorActionPreference = 'Stop'
if ($Base -notmatch '^http://127\.0\.0\.1:\d+/api/v1$') { throw 'Local disposable audit stack only.' }
# Requires the load-test fixtures in the isolated tayyar-full-audit-20260921 project.
# Adds records and exercises commands; does not reset, truncate, or delete state.
function Browser { return @{ Session = [Microsoft.PowerShell.Commands.WebRequestSession]::new(); Token = $null } }
function Call($Browser, $Method, $Path, $Body = $null, $Expected = 200, $Extra = @{}) {
    $headers = @{} + $Extra
    if ($Browser.Token) { $headers['X-CSRF-TOKEN'] = $Browser.Token }
    $params = @{ Uri = "$Base$Path"; Method = $Method; WebSession = $Browser.Session; Headers = $headers; SkipHttpErrorCheck = $true }
    if ($null -ne $Body) { $params.Body = ConvertTo-Json $Body -Depth 12 -Compress; $params.ContentType = 'application/json' }
    $response = Invoke-WebRequest @params
    if ([int]$response.StatusCode -ne $Expected) { throw "$Method $Path expected $Expected, got $($response.StatusCode): $($response.Content)" }
    Write-Host "PASS $Expected $Method $Path"
    if ($response.Content) { return $response.Content | ConvertFrom-Json }
}
function Csrf($Browser) { $Browser.Token = (Call $Browser GET '/auth/csrf').token }
function Login($Email) {
    $browser = Browser
    Csrf $browser
    $null = Call $browser POST '/auth/session' @{ email = $Email; password = 'Load test password!' } 204
    Csrf $browser
    return $browser
}
$restaurant = '00000000-0000-0000-0000-000000002001'
$branch = '00000000-0000-0000-0000-000000003001'
$staffId = '00000000-0000-0000-0000-000000000053'
$driverId = '00000000-0000-0000-0000-000000000043'
$owner = Login 'load-owner@example.test'
$admin = Login 'load-admin@example.test'
$driver = Login 'load-driver-1@example.test'
if ($ResumeOrderId) {
    $email = $ResumeCustomerEmail
    $customer = Login $email
    $me = Call $customer GET '/users/me'
    $order = @{ orderId = $ResumeOrderId }
} else {
$fresh = Browser
Csrf $fresh
$email = 'audit-' + [guid]::NewGuid().ToString('N') + '@example.test'
$registered = Call $fresh POST '/auth/registrations' @{fullName='Audit Customer';email=$email;password='Load test password!';phone='+201000000099'} 201
$customer = Login $email
$me = Call $customer GET '/users/me'
if ($me.roles.Count -ne 1 -or $me.roles[0] -ne 'CUSTOMER') { throw 'Registration privilege violation' }
foreach ($path in @('/admin/users','/driver/profile','/restaurant-operations/context')) { $null = Call $customer GET $path $null 403 }
foreach ($path in @('/admin/users','/driver/profile')) { $null = Call $owner GET $path $null 403 }
$null = Call $owner GET '/restaurants/99999999-0000-0000-0000-000000000001' $null 404
$noCsrf = Browser
$null = Call $noCsrf POST '/auth/session' @{email=$email;password='Load test password!'} 403
$address = Call $customer POST '/users/me/addresses' @{label='Audit home';street='Audit Street';building='1';city='Cairo';countryCode='EG'} 201
$address = Call $customer PUT "/users/me/addresses/$($address.id)/delivery-zone" @{deliveryZoneId='00000000-0000-0000-0000-000000008101';version=$address.version}
$cart = Call $customer POST '/cart/items' @{branchId=$branch;menuItemId='00000000-0000-0000-0000-000000006001';quantity=1} 201
$input = @{cartId=$cart.id;cartVersion=$cart.version;savedAddressId=$address.id;paymentMethod='CASH'}
$key = [guid]::NewGuid().ToString()
$order = Call $customer POST '/checkout' $input 201 @{'Idempotency-Key'=$key}
$repeat = Call $customer POST '/checkout' $input 201 @{'Idempotency-Key'=$key}
if ($repeat.orderId -ne $order.orderId) { throw 'Checkout duplicated order' }
$null = Call $customer POST '/checkout' $input 409 @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
$existingStaff = Call $owner GET "/restaurants/$restaurant/staff?size=100"
if ($staffId -notin $existingStaff.items.userId) { $null = Call $owner POST "/restaurants/$restaurant/staff" @{email='load-staff-1@example.test'} 201 }
$branchView = Call $owner GET "/restaurants/$restaurant/branches/$branch"
$staffList = Call $owner GET "/restaurants/$restaurant/staff?size=100"
$staffMember = $staffList.items | Where-Object userId -eq $staffId
if ($branch -notin $staffMember.branches.branchId) { $null = Call $owner PUT "/restaurants/$restaurant/branches/$branch/staff/$staffId" @{version=$branchView.version} }
$staff = Login 'load-staff-1@example.test'
$null = Call $staff GET '/restaurant-operations/context'
$null = Call $staff GET "/restaurants/$restaurant/branches" $null 403
$null = Call $staff GET "/restaurant-orders?restaurantId=$restaurant&branchId=00000000-0000-0000-0000-000000003002" $null 404
$detail = Call $staff GET "/restaurant-orders/$($order.orderId)"
$accepted = Call $staff POST "/restaurant-orders/$($order.orderId)/accept" @{version=$detail.order.version}
$null = Call $staff POST "/restaurant-orders/$($order.orderId)/accept" @{version=$detail.order.version} 409
$preparing = Call $staff POST "/restaurant-orders/$($order.orderId)/start-preparation" @{version=$accepted.version}
$ready = Call $staff POST "/restaurant-orders/$($order.orderId)/ready-for-pickup" @{version=$preparing.version}
$active = Call $driver GET '/driver/orders'
foreach ($assigned in $active.items) {
    if ($assigned.status -eq 'READY_FOR_PICKUP') { $assigned = Call $driver POST "/driver/orders/$($assigned.orderId)/pickup" @{orderVersion=$assigned.orderVersion;assignmentVersion=$assigned.assignmentVersion} }
    $null = Call $driver POST "/driver/orders/$($assigned.orderId)/deliver" @{orderVersion=$assigned.orderVersion;assignmentVersion=$assigned.assignmentVersion}
}
$null = Call $driver GET '/driver/orders/20000000-0000-0000-0000-000000000702' $null 404
$assignment = Call $admin POST '/admin/delivery-assignments' @{orderId=$order.orderId;driverId=$driverId;orderVersion=$ready.version}
}
if (-not $AfterDelivery) {
$assigned = Call $driver GET "/driver/orders/$($order.orderId)"
$picked = Call $driver POST "/driver/orders/$($order.orderId)/pickup" @{orderVersion=$assigned.orderVersion;assignmentVersion=$assigned.assignmentVersion}
$null = Call $driver POST "/driver/orders/$($order.orderId)/pickup" @{orderVersion=$assigned.orderVersion;assignmentVersion=$assigned.assignmentVersion} 409
$delivered = Call $driver POST "/driver/orders/$($order.orderId)/deliver" @{orderVersion=$picked.orderVersion;assignmentVersion=$picked.assignmentVersion}
if ($delivered.orderStatus -ne 'DELIVERED' -or $delivered.paymentStatus -ne 'PAID' -or $delivered.driverState -ne 'AVAILABLE' -or $delivered.assignmentStatus -ne 'COMPLETED') { throw 'Completion not atomic' }
}
$null = Call $customer GET "/orders/$($order.orderId)"
$null = Call $customer GET '/notifications'
$null = Call $customer POST "/orders/$($order.orderId)/review" @{rating=5;comment='Disposable full-system audit review'} 201
$null = Call $customer PUT "/favorites/$restaurant" $null 204
$null = Call $customer GET '/favorites'
$null = Call $admin GET "/admin/orders/$($order.orderId)"
$null = Call $admin POST "/admin/users/$($me.id)/suspension" @{reason='Disposable audit suspension'}
$null = Call $customer GET '/users/me' $null 401
$null = Call $admin POST "/admin/users/$($me.id)/reactivation" @{reason='Disposable audit reactivation'}
$customer = Login $email
$application = Call $customer POST '/restaurant-applications' @{name='Audit Kitchen';description='Disposable audit application'} 201
$null = Call $admin POST "/admin/restaurant-applications/$($application.id)/decisions" @{outcome='APPROVED';version=$application.version} 201
$null = Call $customer GET '/users/me' $null 401
$null = Call $admin GET '/admin/audit-log'
Write-Host "AUDIT COMPLETE customer=$($me.id) order=$($order.orderId) application=$($application.id)"
