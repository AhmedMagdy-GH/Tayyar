import http from 'k6/http';
import { check, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://backend:8080';
const API = `${BASE}/api/v1`;
const PASSWORD = 'Load test password!';
const scenario = (__ENV.SCENARIO || 'health').toLowerCase();
const unexpected = new Rate('unexpected_failure');
const expected409 = new Counter('expected_409');
const expected429 = new Counter('expected_429');
const checkoutSuccess = new Counter('checkout_success');
const checkoutLatency = new Trend('checkout_latency', true);

function parsedStages(fallback) {
  const raw = __ENV.STAGES || fallback;
  return raw.split(',').map((part) => {
    const [target, duration] = part.split(':');
    return { target: Number(target), duration };
  });
}

const staged = {
  executor: 'ramping-vus', startVUs: 0,
  stages: parsedStages('10:10s,10:20s,0:5s'), gracefulRampDown: '10s',
};
function execution() {
  if (scenario === 'idempotency') return { executor:'shared-iterations', vus:2, iterations:2, maxDuration:'30s' };
  if (scenario === 'checkout') return { executor:'per-vu-iterations', vus:20, iterations:10, maxDuration:'2m' };
  if (scenario === 'promotion_shared' || scenario === 'promotion_distributed') return { executor:'per-vu-iterations', vus:20, iterations:1, maxDuration:'1m' };
  if (scenario === 'operations') return { executor:'per-vu-iterations', vus:100, iterations:1, maxDuration:'1m' };
  if (scenario === 'delivery') return { executor:'per-vu-iterations', vus:10, iterations:1, maxDuration:'1m' };
  if (scenario === 'cart_checkout_race' || scenario === 'accept_cancel_race') return { executor:'per-vu-iterations', vus:2, iterations:1, maxDuration:'30s' };
  return staged;
}
export const options = {
  scenarios: { [scenario]: execution() },
  thresholds: { unexpected_failure: ['rate<0.01'] },
  summaryTrendStats: ['avg','min','med','p(90)','p(95)','p(99)','max'],
  discardResponseBodies: false,
};

function id(prefix, n) { return `${prefix}-0000-0000-0000-${String(n).padStart(12, '0')}`; }
function classify(res, allowed = [200]) {
  if (res.status === 409) expected409.add(1);
  if (res.status === 429) expected429.add(1);
  const ok = allowed.includes(res.status);
  unexpected.add(!ok);
  return ok;
}
function csrf(session) {
  const r = http.get(`${API}/auth/csrf`, { jar: session.jar, tags: { operation: 'csrf' } });
  if (!classify(r, [200])) return null;
  return r.json('token');
}
function login(email) {
  const jar = new http.CookieJar();
  const first = http.get(`${API}/auth/csrf`, { jar, tags: { operation: 'csrf' } });
  if (first.status !== 200) throw new Error(`CSRF failed for ${email}: ${first.status}`);
  const r = http.post(`${API}/auth/session`, JSON.stringify({ email, password: PASSWORD }), {
    jar, headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': first.json('token') }, tags: { operation: 'login' },
  });
  if (r.status !== 204) throw new Error(`Login failed for ${email}: ${r.status} ${r.body}`);
  const second = http.get(`${API}/auth/csrf`, { jar, tags: { operation: 'csrf' } });
  if (second.status !== 200) throw new Error(`post-login CSRF failed for ${email}: ${second.status}`);
  const stored = jar.cookiesForURL(`${API}/users/me`);
  if (__ENV.DEBUG==='1') console.log(`cookies=${JSON.stringify(stored)}`);
  const sessionCookie = stored.SESSION && stored.SESSION[0] ? stored.SESSION[0] : null;
  return { cookie: sessionCookie, token: second.json('token') };
}
function params(session, operation) {
  return { cookies: { SESSION: session.cookie }, headers: { 'X-CSRF-TOKEN': session.token, 'Content-Type': 'application/json' }, tags: { operation } };
}

export function setup() {
  if (scenario === 'health' || scenario === 'discovery') return {};
  if (scenario === 'operations') return { sessions: [login('load-owner@example.test')] };
  if (scenario === 'accept_cancel_race') return { sessions: [login('load-owner@example.test'),login('load-customer-1@example.test')] };
  if (scenario === 'delivery') return { sessions: Array.from({length:10},(_,i)=>login(`load-driver-${i+1}@example.test`)) };
  if (scenario === 'ratelimit' || scenario === 'idempotency' || scenario === 'cart_checkout_race') {
    const session = login('load-customer-1@example.test');
    return { sessions: [session], prepared: prepareCart(session, 1) };
  }
  return { sessions: Array.from({length:20},(_,i)=>login(`load-customer-${i+1}@example.test`)) };
}

function prepareCart(session, customer) {
  const p = params(session, 'cart_prepare');
  let cart = http.get(`${API}/cart`, p);
  if (cart.status === 204) {
    cart = http.post(`${API}/cart/items`, JSON.stringify({ branchId:id('00000000',3001), menuItemId:id('00000000',6001), quantity:1 }), p);
  }
  if (![200,201].includes(cart.status)) throw new Error(`cart preparation failed ${customer}: ${cart.status} ${cart.body}`);
  const value = cart.json();
  return { cartId:value.id, cartVersion:value.version, savedAddressId:id('00000000',9000+customer), itemId:value.items[0].id, itemVersion:value.items[0].version };
}
function doCheckout(session, customer, promotionCode, key, prepared) {
  const cart = prepared || prepareCart(session, customer);
  const body = { cartId:cart.cartId, cartVersion:cart.cartVersion, savedAddressId:cart.savedAddressId, paymentMethod:'CASH' };
  if (promotionCode) body.promotionCode = promotionCode;
  const p = params(session, 'checkout');
  p.headers['Idempotency-Key'] = key;
  const r = http.post(`${API}/checkout`, JSON.stringify(body), p);
  checkoutLatency.add(r.timings.duration);
  if (r.status === 201) checkoutSuccess.add(1);
  classify(r, [201,409,429]);
  return r;
}

function health() {
  const r=http.get(`${API}/health`, {tags:{operation:'health'}}); classify(r,[200]); check(r,{'health 200':x=>x.status===200});
}
function discovery() {
  const n=((__VU+__ITER)%20)+1, b=((n-1)*2)+1, z=((__VU+__ITER)%5)+1;
  group('discovery',()=>{
    const urls=[
      `${API}/discovery/restaurants?page=${__ITER%3}&size=20`,
      `${API}/discovery/restaurants?query=Load%20Restaurant%20${n}&page=0&size=20`,
      `${API}/discovery/restaurants?zoneId=${id('00000000',8100+z)}&page=0&size=20`,
      `${API}/discovery/restaurants/${id('00000000',2000+n)}`,
      `${API}/discovery/restaurants/${id('00000000',2000+n)}/branches?page=0&size=20`,
      `${API}/discovery/restaurants/${id('00000000',2000+n)}/branches/${id('00000000',3000+b)}/menu?page=0&size=5&itemPage=0&itemSize=20&availableOnly=true`,
    ];
    const responses=http.batch(urls.map((url,i)=>['GET',url,null,{tags:{operation:`discovery_${i}`}}]));
    responses.forEach(r=>classify(r,[200]));
  });
}
function authenticated(data) {
  const s=data.sessions[(__VU-1)%data.sessions.length], p=params(s,'auth_read');
  const urls=['/users/me','/users/me/addresses?page=0&size=20','/cart','/orders?page=0&size=20','/notifications?page=0&size=20','/favorites?page=0&size=20'];
  const responses=http.batch(urls.map((path,i)=>['GET',API+path,null,{...p,tags:{operation:`auth_read_${i}`}}]));
  if (__ENV.DEBUG==='1' && __VU===1 && __ITER===0) console.log(`session-length=${s.cookie.length} statuses=${responses.map(r=>r.status).join(',')} first=${responses[0].body}`);
  responses.forEach((r,i)=>classify(r,i===2?[200,204]:[200]));
}
function cart(data) {
  const customer=((__VU-1)%20)+1, s=data.sessions[customer-1], p=params(s,'cart');
  const current=http.get(`${API}/cart`,p); classify(current,[200,204]);
  if (current.status===204) { classify(http.post(`${API}/cart/items`,JSON.stringify({branchId:id('00000000',3001),menuItemId:id('00000000',6001),quantity:1}),p),[200,201]); return; }
  const c=current.json(), line=c.items[0];
  if (!line) { classify(http.post(`${API}/cart/items`,JSON.stringify({branchId:id('00000000',3001),menuItemId:id('00000000',6001),quantity:1,cartId:c.id,cartVersion:c.version}),p),[200,201,409]); return; }
  const r=http.patch(`${API}/cart/items/${line.id}`,JSON.stringify({quantity:(line.quantity%3)+1,cartId:c.id,cartVersion:c.version,itemVersion:line.version}),p);
  classify(r,[200,409]);
}
function checkout(data) {
  const customer=((__VU-1)%20)+1, s=data.sessions[customer-1];
  doCheckout(s,customer,null,`checkout-${customer}-${__VU}-${__ITER}-${Date.now()}`);
}
function promotion(data, distributed) {
  const customer=((__VU-1)%20)+1, s=data.sessions[customer-1];
  doCheckout(s,customer,distributed?`PROMO${String(customer).padStart(2,'0')}`:'SHARED10',`${distributed?'dist':'shared'}-${customer}-${__ITER}-${Date.now()}`);
}
function idempotency(data) { doCheckout(data.sessions[0],1,null,'same-idempotency-key-0001',data.prepared); }
function ratelimit(data) { doCheckout(data.sessions[0],1,null,'rate-limit-idempotency-01',data.prepared); }
function operations(data) {
  const n=((__VU-1)%100)+601, order=id('20000000',n), p=params(data.sessions[0],'order_transition');
  const a=http.post(`${API}/restaurant-orders/${order}/accept`,JSON.stringify({version:0}),p); classify(a,[200,409]);
  if(a.status!==200)return; const b=http.post(`${API}/restaurant-orders/${order}/start-preparation`,JSON.stringify({version:1}),p); classify(b,[200,409]);
  if(b.status!==200)return; classify(http.post(`${API}/restaurant-orders/${order}/ready-for-pickup`,JSON.stringify({version:2}),p),[200,409]);
}
function delivery(data) {
  const i=((__VU-1)%10)+1, order=id('20000000',700+i), s=data.sessions[i-1], p=params(s,'delivery_transition');
  const pickup=http.post(`${API}/driver/orders/${order}/pickup`,JSON.stringify({orderVersion:3,assignmentVersion:0}),p); classify(pickup,[200,409]);
  if(pickup.status!==200)return; classify(http.post(`${API}/driver/orders/${order}/deliver`,JSON.stringify({orderVersion:4,assignmentVersion:0}),p),[200,409]);
}
function cartCheckoutRace(data) {
  if (__VU === 1) return doCheckout(data.sessions[0],1,null,'cart-checkout-race-key-01',data.prepared);
  const c=data.prepared, p=params(data.sessions[0],'cart_checkout_race');
  const r=http.patch(`${API}/cart/items/${c.itemId}`,JSON.stringify({quantity:2,cartId:c.cartId,cartVersion:c.cartVersion,itemVersion:c.itemVersion}),p);
  classify(r,[200,409]);
}
function acceptCancelRace(data) {
  const order=id('20000000',601);
  const r=__VU===1
    ? http.post(`${API}/restaurant-orders/${order}/accept`,JSON.stringify({version:0}),params(data.sessions[0],'accept_cancel_race'))
    : http.post(`${API}/orders/${order}/cancel`,JSON.stringify({version:0,reason:'Controlled race'}),params(data.sessions[1],'accept_cancel_race'));
  classify(r,[200,409]);
}

export default function(data) {
  if(scenario==='health')return health(); if(scenario==='discovery')return discovery();
  if(scenario==='auth')return authenticated(data); if(scenario==='cart')return cart(data);
  if(scenario==='checkout')return checkout(data); if(scenario==='idempotency')return idempotency(data);
  if(scenario==='promotion_shared')return promotion(data,false); if(scenario==='promotion_distributed')return promotion(data,true);
  if(scenario==='operations')return operations(data); if(scenario==='delivery')return delivery(data);
  if(scenario==='cart_checkout_race')return cartCheckoutRace(data);
  if(scenario==='accept_cancel_race')return acceptCancelRace(data);
  if(scenario==='ratelimit')return ratelimit(data); throw new Error(`Unknown scenario ${scenario}`);
}
