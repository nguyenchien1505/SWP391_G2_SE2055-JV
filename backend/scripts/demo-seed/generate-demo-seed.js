#!/usr/bin/env node
/*
 * Sinh bộ dữ liệu DEMO (6 file R__seed_demo_0X_*.sql trong src/main/resources/db/seed).
 *
 *   node backend/scripts/demo-seed/generate-demo-seed.js
 *
 * - Mọi ngày giờ ghi dạng TƯƠNG ĐỐI so với CURRENT_DATE lúc Flyway chạy (@d0), như các seed test.
 * - Trước khi ghi file, script tự kiểm tra các luật mà service sẽ áp: policy ca (8h/ngày, 48h và
 *   1 ngày nghỉ mỗi 7 ngày, 6 ngày liên tiếp, nghỉ 12h giữa ca), ca theo mẫu phải có lễ tân, quota,
 *   chuỗi trạng thái phòng hợp lệ, người dọn có ca và đã vào ca, cặp đổi ca hợp lệ sau khi đổi...
 *   Sai một điều là dừng, không ghi gì.
 * - Chạy lại cho ra đúng cùng nội dung (không có số ngẫu nhiên).
 */
'use strict';

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const OUT_DIR = path.resolve(__dirname, '../../src/main/resources/db/seed');
const PASSWORD_HASH = '$2a$10$fscqy3oQjGzezQ9ymSXCxOmp7fjPEKkqpMbZ2Qo/szgUUOu.j2SCq'; // Test@123
const DAY = 1440;
const FROM = -28;          // lịch sử ca: 4 tuần trước
const TO = 7;              // ... đến 1 tuần sau
const UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36';

// ── tiện ích ───────────────────────────────────────────────────────────────────
function fail(msg) { throw new Error('KIỂM TRA THẤT BẠI: ' + msg); }
function check(cond, msg) { if (!cond) fail(msg); }
const pad = n => String(n).padStart(2, '0');
const mod = (a, n) => ((a % n) + n) % n;
const hm = s => { const [h, m] = s.split(':').map(Number); return h * 60 + m; };
const at = (day, s) => day * DAY + hm(s);
const dayOf = t => Math.floor(t / DAY);

const counters = {};
function newId(prefix) {
  counters[prefix] = (counters[prefix] || 0) + 1;
  return `${prefix}-0000-4000-8000-${String(counters[prefix]).padStart(12, '0')}`;
}
const raw = s => ({ raw: s });
const sqlDate = d => (d === 0 ? '@d0' : `(@d0 + INTERVAL ${d} DAY)`);
const date = d => raw(sqlDate(d));
function ts(t) {
  const d = dayOf(t), r = t - d * DAY;
  return raw(`TIMESTAMP(${sqlDate(d)}, '${pad(Math.floor(r / 60))}:${pad(r % 60)}:00')`);
}
const time = m => `${pad(Math.floor(m / 60) % 24)}:${pad(m % 60)}:00`;
function v(x) {
  if (x === null || x === undefined) return 'NULL';
  if (typeof x === 'object' && 'raw' in x) return x.raw;
  if (typeof x === 'boolean') return x ? 'TRUE' : 'FALSE';
  if (typeof x === 'number') return String(x);
  return "'" + String(x).replace(/\\/g, '\\\\').replace(/'/g, "''") + "'";
}
function insert(table, cols, rows, conflict) {
  if (!rows.length) return '';
  const tail = conflict || `ON DUPLICATE KEY UPDATE ${cols[0]} = ${cols[0]}`;
  let out = '';
  for (let i = 0; i < rows.length; i += 150) {
    const part = rows.slice(i, i + 150);
    out += `INSERT INTO ${table} (${cols.join(', ')}) VALUES\n`;
    out += part.map(r => '    (' + cols.map(c => v(r[c])).join(', ') + ')').join(',\n');
    out += `\n${tail};\n\n`;
  }
  return out;
}
function hash(...xs) {   // băm cố định 0..9999
  let h = 2166136261;
  for (const ch of xs.join('|')) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619) >>> 0; }
  return h % 10000;
}

// PNG thang xám nhỏ — ảnh đính kèm đơn nghỉ.
function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function makePng(w, h) {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 0;
  const pix = Buffer.alloc((w + 1) * h);
  for (let y = 0; y < h; y++) {
    pix[y * (w + 1)] = 0;
    for (let x = 0; x < w; x++) {
      const line = (y > 8 && y < h - 8 && y % 10 < 2 && x > 10 && x < w - 10 - (y * 7) % 40);
      pix[y * (w + 1) + 1 + x] = (x < 4 || y < 4 || x >= w - 4 || y >= h - 4) ? 90 : line ? 60 : 245;
    }
  }
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(pix)), chunk('IEND', Buffer.alloc(0))]);
}

// ── 1. Nền tảng & thanh toán ─────────────────────────────────────────────────────
const PRICE = { loc: 500000, user: 50000, room: 20000 };     // pricing_config hiện hành (V1)
const pricingRows = [{ id: newId('d1400000'), price_per_location: 400000, price_per_user: 40000,
  price_per_room: 15000, effective_from: '2024-06-01', created_at: '2024-05-20 09:00:00' }];

const tenants = [];
const subs = [];
const policies = [];
const invoices = [];
function addTenant(t) {
  t.id = newId('d1000000');
  tenants.push(t);
  const sub = Object.assign({ id: newId('d1100000'), tenant_id: t.id,
    price_per_location: PRICE.loc, price_per_user: PRICE.user, price_per_room: PRICE.room }, t.sub);
  subs.push(sub);
  t.subRow = sub;
  policies.push({ id: newId('d1200000'), tenant_id: t.id });
  return t;
}
const fee = (q) => q[0] * PRICE.loc + q[1] * PRICE.user + q[2] * PRICE.room;
function addInvoice(t, type, start, q, amount, issuedT) {
  const inv = { id: newId('d1300000'), tenant_id: t.id, subscription_id: t.subRow.id, invoice_type: type,
    period_start: date(start.from), period_end: date(start.to),
    quota_location_snapshot: q[0], quota_user_snapshot: q[1], quota_room_snapshot: q[2],
    amount, status: 'PAID', issued_at: ts(issuedT), paid_at: ts(issuedT + 3), created_at: ts(issuedT),
    _from: start.from, _type: type };
  invoices.push(inv);
  return inv;
}

const main = addTenant({ key: 'main', name: 'Chuỗi khách sạn Sen Việt', email: 'director.demo@swp391.test',
  phone: '0912345001', status: 'ACTIVE', created: at(-75, '09:00'),
  sub: { quota_location: 3, quota_user: 40, quota_room: 80, is_trial: false, trial_ends_at: date(-45),
    current_period_start: date(-10), next_billing_date: date(20) } });
addInvoice(main, 'PERIODIC', { from: -70, to: -41 }, [2, 25, 50], fee([2, 25, 50]), at(-70, '10:12'));
addInvoice(main, 'PERIODIC', { from: -40, to: -11 }, [2, 25, 50], fee([2, 25, 50]), at(-42, '20:05'));
addInvoice(main, 'UPGRADE_DIFF', { from: -25, to: -11 }, [3, 40, 80],
  Math.round((fee([3, 40, 80]) - fee([2, 25, 50])) * 15 / 30), at(-25, '14:40'));
addInvoice(main, 'PERIODIC', { from: -10, to: 19 }, [3, 40, 80], fee([3, 40, 80]), at(-12, '21:30'));

const bienXanh = addTenant({ key: 'bienxanh', name: 'Khách sạn Biển Xanh', email: 'director.bienxanh@swp391.test',
  phone: '0912345002', status: 'TRIAL', created: at(-18, '15:20'),
  sub: { quota_location: 1, quota_user: 20, quota_room: 50, is_trial: true, trial_ends_at: date(12) } });
const hoangGia = addTenant({ key: 'hoanggia', name: 'Hoàng Gia Resort', email: 'director.hoanggia@swp391.test',
  phone: '0912345003', status: 'ACTIVE', created: at(-60, '10:00'),
  sub: { quota_location: 1, quota_user: 10, quota_room: 20, is_trial: false, trial_ends_at: date(-25),
    current_period_start: date(-25), next_billing_date: date(5) } });
addInvoice(hoangGia, 'PERIODIC', { from: -55, to: -26 }, [1, 10, 20], fee([1, 10, 20]), at(-55, '11:02'));
addInvoice(hoangGia, 'PERIODIC', { from: -25, to: 4 }, [1, 10, 20], fee([1, 10, 20]), at(-27, '19:45'));
const phoCo = addTenant({ key: 'phoco', name: 'Nhà nghỉ Phố Cổ', email: 'director.phoco@swp391.test',
  phone: '0912345004', status: 'PAYMENT_OVERDUE', created: at(-68, '08:30'),
  sub: { quota_location: 1, quota_user: 5, quota_room: 10, is_trial: false, trial_ends_at: date(-33),
    current_period_start: date(-33), next_billing_date: date(-3) } });
addInvoice(phoCo, 'PERIODIC', { from: -63, to: -34 }, [1, 5, 10], fee([1, 5, 10]), at(-63, '09:15'));
addInvoice(phoCo, 'PERIODIC', { from: -33, to: -4 }, [1, 5, 10], fee([1, 5, 10]), at(-34, '22:10'));
const doiThong = addTenant({ key: 'doithong', name: 'Khách sạn Đồi Thông', email: 'director.doithong@swp391.test',
  phone: '0912345005', status: 'SUSPENDED', suspend_reason: 'ADMIN_LOCKED', suspended_at: at(-2, '10:15'),
  created: at(-30, '13:00'),
  sub: { quota_location: 1, quota_user: 10, quota_room: 20, is_trial: false, trial_ends_at: date(-15),
    current_period_start: date(-15), next_billing_date: date(15) } });
addInvoice(doiThong, 'PERIODIC', { from: -15, to: 14 }, [1, 10, 20], fee([1, 10, 20]), at(-15, '16:20'));
const mayTrang = addTenant({ key: 'maytrang', name: 'Homestay Mây Trắng', email: 'director.maytrang@swp391.test',
  phone: '0912345006', status: 'SUSPENDED', suspend_reason: 'TRIAL_EXPIRED', suspended_at: at(-5, '00:00'),
  created: at(-36, '20:00'),
  sub: { quota_location: 1, quota_user: 20, quota_room: 50, is_trial: true, trial_ends_at: date(-5) } });

// Luật job gia hạn: hóa đơn PERIODIC đã trả có kỳ bắt đầu SAU current_period_start sẽ bị áp lại quota.
for (const inv of invoices) {
  const t = tenants.find(x => x.id === inv.tenant_id);
  const cps = Number(/INTERVAL (-?\d+)/.exec(t.subRow.current_period_start.raw)[1]);
  check(!(inv._type === 'PERIODIC' && inv._from > cps), `hóa đơn ${inv.id} có kỳ bắt đầu sau current_period_start`);
}

// ── 2. Tổ chức ───────────────────────────────────────────────────────────────────
const locations = [];
function addLocation(o) { o.id = newId('d2000000'); locations.push(o); return o; }
const HN = addLocation({ key: 'HN', tenant: main, name: 'Sen Việt Hà Nội – Hoàn Kiếm',
  address: '18 Hàng Bè, Hoàn Kiếm, Hà Nội', phone: '02439260001', star: 3, status: 'OPERATIONAL',
  ipMode: 'WARN', own: false, created: at(-74, '09:30'), base: 0 });
const DN = addLocation({ key: 'DN', tenant: main, name: 'Sen Việt Đà Nẵng – Mỹ Khê',
  address: '96 Võ Nguyên Giáp, Sơn Trà, Đà Nẵng', phone: '02363950002', star: 3, status: 'OPERATIONAL',
  ipMode: 'OFF', own: true, created: at(-72, '10:00'), base: 60 });
const HL = addLocation({ key: 'HL', tenant: main, name: 'Sen Việt Hạ Long – Bãi Cháy',
  address: '25 Hạ Long, Bãi Cháy, Hạ Long, Quảng Ninh', phone: '02033840003', star: 2, status: 'NOT_OPERATIONAL',
  ipMode: 'OFF', own: false, created: at(-20, '11:00'), base: 0 });
const BX = addLocation({ key: 'BX', tenant: bienXanh, name: 'Biển Xanh Nha Trang', address: '40 Trần Phú, Lộc Thọ, Nha Trang',
  phone: '02583520004', star: 2, status: 'NOT_OPERATIONAL', ipMode: 'OFF', own: false, created: at(-17, '09:00'), base: 0 });
const HG = addLocation({ key: 'HG', tenant: hoangGia, name: 'Hoàng Gia Resort Phú Quốc', address: '12 Trần Hưng Đạo, Dương Đông, Phú Quốc',
  phone: '02973980005', star: 3, status: 'OPERATIONAL', ipMode: 'OFF', own: false, created: at(-59, '09:00'), base: 0 });
const PC = addLocation({ key: 'PC', tenant: phoCo, name: 'Nhà nghỉ Phố Cổ Hội An', address: '7 Nguyễn Thái Học, Minh An, Hội An',
  phone: '02353860006', star: 1, status: 'OPERATIONAL', ipMode: 'OFF', own: false, created: at(-67, '09:00'), base: 0 });
const DT = addLocation({ key: 'DT', tenant: doiThong, name: 'Đồi Thông Đà Lạt', address: '3 Hồ Tùng Mậu, Phường 3, Đà Lạt',
  phone: '02633820007', star: 2, status: 'NOT_OPERATIONAL', ipMode: 'OFF', own: false, created: at(-29, '09:00'), base: 0 });
const MT = addLocation({ key: 'MT', tenant: mayTrang, name: 'Mây Trắng Sa Pa', address: '15 Thác Bạc, Sa Pa, Lào Cai',
  phone: '02143870008', star: 1, status: 'NOT_OPERATIONAL', ipMode: 'OFF', own: false, created: at(-35, '09:00'), base: 0 });

const networks = [
  { id: newId('d2100000'), tenant_id: main.id, location_id: HN.id, cidr: '113.160.92.0/24', label: 'Wifi khách sạn (VNPT)' },
  { id: newId('d2100000'), tenant_id: main.id, location_id: HN.id, cidr: '127.0.0.1', label: 'Máy demo (localhost)' },
  { id: newId('d2100000'), tenant_id: main.id, location_id: HN.id, cidr: '::1', label: 'Máy demo (localhost IPv6)' },
];

const departments = [];
const positions = [];
function dept(t, name, active = true) { const d = { id: newId('d3000000'), tenant_id: t.id, name, is_active: active }; departments.push(d); return d; }
function pos(t, d, name, type, active = true) { const p = { id: newId('d4000000'), tenant_id: t.id, department_id: d.id, name, position_type: type, is_active: active }; positions.push(p); return p; }
const dLeTan = dept(main, 'Lễ tân'), dBuong = dept(main, 'Buồng phòng'), dGiamSat = dept(main, 'Giám sát vận hành'),
  dKyThuat = dept(main, 'Kỹ thuật – Bảo trì'), dHanhChinh = dept(main, 'Hành chính', false);
const P = {
  truongCa: pos(main, dLeTan, 'Trưởng ca lễ tân', 'RECEPTION'),
  leTan: pos(main, dLeTan, 'Nhân viên lễ tân', 'RECEPTION'),
  buong: pos(main, dBuong, 'Nhân viên buồng phòng', 'HOUSEKEEPING'),
  giamSat: pos(main, dGiamSat, 'Giám sát buồng phòng', 'SUPERVISION'),
  kyThuat: pos(main, dKyThuat, 'Kỹ thuật viên', 'OTHER'),
  vanThu: pos(main, dHanhChinh, 'Nhân viên văn thư', 'OTHER', false),
};
const secPos = {};
for (const t of [hoangGia, phoCo]) {
  const d = dept(t, 'Vận hành');
  secPos[t.key] = { leTan: pos(t, d, 'Lễ tân', 'RECEPTION'), buong: pos(t, d, 'Buồng phòng', 'HOUSEKEEPING') };
}

const roomTypes = [];
function rtype(t, name, active = true) { const r = { id: newId('d6000000'), tenant_id: t.id, name, is_active: active }; roomTypes.push(r); return r; }
const RT = { std: rtype(main, 'Standard'), sup: rtype(main, 'Superior'), dlx: rtype(main, 'Deluxe'),
  suite: rtype(main, 'Suite'), fam: rtype(main, 'Family'), old: rtype(main, 'Phòng tập thể (ngừng dùng)', false) };
const RTsec = { HG: rtype(hoangGia, 'Bungalow'), PC: rtype(phoCo, 'Phòng đôi') };

const areas = [];
function area(loc, name) { const a = { id: newId('d2200000'), tenant_id: loc.tenant.id, location_id: loc.id, name, loc }; areas.push(a); return a; }
const A = {
  HN: { sanh: area(HN, 'Sảnh lễ tân'), nhaHang: area(HN, 'Nhà hàng tầng 1'), gym: area(HN, 'Phòng gym'),
    kho: area(HN, 'Kho buồng phòng'), giat: area(HN, 'Khu giặt là') },
  DN: { sanh: area(DN, 'Sảnh lễ tân'), nhaHang: area(DN, 'Nhà hàng hướng biển'), beBoi: area(DN, 'Bể bơi tầng thượng'),
    kho: area(DN, 'Kho buồng phòng') },
  HL: { sanh: area(HL, 'Sảnh lễ tân') },
};

const templates = [];
function tmpl(t, loc, name, s, e, desc) { const x = { id: newId('d7000000'), tenant_id: t.id, location_id: loc ? loc.id : null, name, start: hm(s), end: hm(e), description: desc }; templates.push(x); return x; }
const TPL = {
  chain: { S: tmpl(main, null, 'Ca sáng', '06:00', '14:00', 'Dùng chung toàn chuỗi'),
    C: tmpl(main, null, 'Ca chiều', '14:00', '22:00', 'Dùng chung toàn chuỗi'),
    D: tmpl(main, null, 'Ca đêm', '22:00', '06:00', 'Qua đêm, tính vào ngày bắt đầu') },
  DN: { S: tmpl(main, DN, 'Ca sáng', '07:00', '15:00', 'Riêng Đà Nẵng — lệch 1 giờ theo giờ đón khách biển'),
    C: tmpl(main, DN, 'Ca chiều', '15:00', '23:00', 'Riêng Đà Nẵng'),
    D: tmpl(main, DN, 'Ca đêm', '23:00', '07:00', 'Riêng Đà Nẵng, qua đêm') },
};
const tplOf = loc => (loc === DN ? TPL.DN : TPL.chain);

// ── Tài khoản ────────────────────────────────────────────────────────────────────
const users = [];
const byKey = {};
const byId = {};
let phoneSeq = 1;
function user(o) {
  o.id = newId('d5000000');
  o.phone = o.phone || '09' + String(81000000 + phoneSeq++ * 7919).slice(0, 8);
  o.perms = o.perms || [];
  o.status = o.status || 'ACTIVE';
  users.push(o); byKey[o.key] = o; byId[o.id] = o;
  return o;
}
const avatar = n => 'https://ui-avatars.com/api/?name=' + encodeURIComponent(n) + '&background=random';

for (const t of tenants) {
  const names = { main: 'Nguyễn Minh Khôi', bienxanh: 'Trần Thu Hà', hoanggia: 'Lê Hoàng Long',
    phoco: 'Phạm Văn Đức', doithong: 'Vũ Thị Lan', maytrang: 'Đỗ Quang Huy' };
  t.director = user({ key: 'director.' + t.key, tenant: t, role: 'DIRECTOR', email: t.email, name: names[t.key],
    phone: t.phone, created: t.created });
}
const mgr = {
  HN: user({ key: 'manager.hn', tenant: main, role: 'MANAGER', email: 'manager.hn@swp391.test', name: 'Hoàng Thị Mai Anh',
    loc: HN, start: -330, dob: '1988-04-12', gender: 'FEMALE', address: '45 Lý Thái Tổ, Hoàn Kiếm, Hà Nội' }),
  DN: user({ key: 'manager.dn', tenant: main, role: 'MANAGER', email: 'manager.dn@swp391.test', name: 'Ngô Văn Bảo',
    loc: DN, start: -320, dob: '1986-09-03', gender: 'MALE', address: '12 Phạm Văn Đồng, Sơn Trà, Đà Nẵng' }),
  HL: user({ key: 'manager.hl', tenant: main, role: 'MANAGER', email: 'manager.hl@swp391.test', name: 'Đặng Thu Trang',
    loc: HL, start: 7, dob: '1991-12-20', gender: 'FEMALE', address: '8 Cái Lân, Bãi Cháy, Hạ Long' }),
  reserve: user({ key: 'manager.duphong', tenant: main, role: 'MANAGER', email: 'manager.duphong@swp391.test', name: 'Bùi Đức Thịnh',
    loc: null, start: -30, dob: '1990-06-08', gender: 'MALE', address: '77 Nguyễn Trãi, Thanh Xuân, Hà Nội' }),
  HG: user({ key: 'manager.hoanggia', tenant: hoangGia, role: 'MANAGER', email: 'manager.hoanggia@swp391.test', name: 'Trịnh Văn Tâm',
    loc: HG, start: -58, dob: '1987-02-14', gender: 'MALE', address: '5 Bạch Đằng, Dương Đông, Phú Quốc' }),
  PC: user({ key: 'manager.phoco', tenant: phoCo, role: 'MANAGER', email: 'manager.phoco@swp391.test', name: 'Lâm Thị Hồng',
    loc: PC, start: -66, dob: '1985-11-30', gender: 'FEMALE', address: '22 Trần Phú, Minh An, Hội An' }),
};

// Staff chuỗi chính. shift: S/C/D; off: ngày nghỉ là các ngày d có (d - off) chia hết cho 7.
const R = ['RECEPTION'], HK = ['HOUSEKEEPING'], SUP = ['SUPERVISION'];
const staffSpec = [
  // Hà Nội
  ['letan.hn1', HN, P.truongCa, R, 'S', 3, 'Nguyễn Thị Thu Hằng', 'FEMALE', '1994-03-08', -300],
  ['letan.hn2', HN, P.leTan, R, 'S', 0, 'Phạm Quang Vinh', 'MALE', '1997-07-21', -290],
  ['letan.hn3', HN, P.leTan, R, 'C', 1, 'Lê Ngọc Ánh', 'FEMALE', '1999-01-15', -280],
  ['letan.hn4', HN, P.leTan, [...R, ...HK], 'C', 4, 'Trần Đức Anh', 'MALE', '1996-10-02', -270],
  ['letan.hn5', HN, P.leTan, R, 'D', 2, 'Vũ Thị Hương', 'FEMALE', '1993-05-27', -260],
  ['buongphong.hn1', HN, P.buong, HK, 'S', 1, 'Đinh Thị Hoa', 'FEMALE', '1990-08-19', -305],
  ['buongphong.hn2', HN, P.buong, HK, 'S', 2, 'Nguyễn Văn Nam', 'MALE', '1995-12-01', -295],
  ['buongphong.hn3', HN, P.buong, HK, 'S', 4, 'Lê Thị Mai', 'FEMALE', '1992-02-11', -285],
  ['buongphong.hn4', HN, P.buong, HK, 'S', 5, 'Hà Thị Ngọc', 'FEMALE', '1998-06-30', -275],
  ['buongphong.hn5', HN, P.buong, HK, 'C', 0, 'Phan Văn Lực', 'MALE', '1991-09-09', -265],
  ['buongphong.hn6', HN, P.buong, HK, 'C', 3, 'Cao Thị Yến', 'FEMALE', '2000-04-04', -255],
  ['giamsat.hn1', HN, P.giamSat, SUP, 'S', 6, 'Trương Minh Tuấn', 'MALE', '1989-11-23', -310],
  ['giamsat.hn2', HN, P.giamSat, [...SUP, ...HK], 'C', 5, 'Lý Thị Kim Oanh', 'FEMALE', '1990-01-31', -300],
  ['kythuat.hn1', HN, P.kyThuat, [], 'S', 3, 'Đoàn Văn Hùng', 'MALE', '1987-07-07', -300],
  ['kythuat.hn2', HN, P.kyThuat, [], 'D', 2, 'Mạc Văn Tiến', 'MALE', '1994-03-18', -240],
  ['buongphong.hn7', HN, P.buong, HK, 'S', 6, 'Kiều Thị Dung', 'FEMALE', '1996-05-05', -230, { status: 'INACTIVE', lastDay: -6 }],
  ['buongphong.hn8', HN, P.buong, HK, 'S', 5, 'Tô Văn Sơn', 'MALE', '1993-08-08', -220, { status: 'TERMINATED', lastDay: -3 }],
  // Đà Nẵng
  ['letan.dn1', DN, P.truongCa, R, 'S', 3, 'Huỳnh Thị Bích Ngân', 'FEMALE', '1993-02-02', -300],
  ['letan.dn2', DN, P.leTan, R, 'S', 0, 'Võ Minh Quân', 'MALE', '1998-12-12', -290],
  ['letan.dn3', DN, P.leTan, R, 'C', 1, 'Nguyễn Hoài Thương', 'FEMALE', '1999-09-19', -280],
  ['letan.dn4', DN, P.leTan, R, 'C', 5, 'Trần Quốc Bảo', 'MALE', '1997-04-24', -270],
  ['letan.dn5', DN, P.leTan, R, 'D', 2, 'Lê Văn Phúc', 'MALE', '1995-06-16', -260],
  ['buongphong.dn1', DN, P.buong, HK, 'S', 2, 'Phạm Thị Lệ', 'FEMALE', '1991-10-10', -300],
  ['buongphong.dn2', DN, P.buong, HK, 'S', 4, 'Hồ Thị Thắm', 'FEMALE', '1994-11-11', -290],
  ['buongphong.dn3', DN, P.buong, HK, 'C', 0, 'Dương Văn Khánh', 'MALE', '1996-03-03', -280],
  ['buongphong.dn4', DN, P.buong, HK, 'C', 6, 'Lưu Thị Nhàn', 'FEMALE', '1992-07-17', -270],
  ['giamsat.dn1', DN, P.giamSat, [...SUP, ...HK], 'S', 5, 'Châu Văn Thành', 'MALE', '1988-08-28', -310],
  ['kythuat.dn1', DN, P.kyThuat, [], 'S', 6, 'Tạ Văn Hiếu', 'MALE', '1990-12-25', -300],
  ['kythuat.dn2', DN, P.kyThuat, [], 'D', 2, 'Quách Văn Lâm', 'MALE', '1993-01-09', -250],
  ['buongphong.dn5', DN, P.buong, HK, 'S', 3, 'Mai Thị Thu', 'FEMALE', '2001-05-15', 0, { mustChange: true, firstDay: 1 }],
];
const addressOf = loc => (loc === HN ? 'Hoàn Kiếm, Hà Nội' : 'Sơn Trà, Đà Nẵng');
staffSpec.forEach(([key, loc, p, perms, shift, off, name, gender, dob, start, extra], i) => {
  const u = user(Object.assign({ key, tenant: main, role: 'STAFF', email: key + '@swp391.test', name, loc, pos: p,
    perms, shift, off, gender, dob, start, address: `${10 + i * 3} ${['Hàng Gai', 'Lê Duẩn', 'Ngô Quyền', 'Bạch Đằng', 'Trần Phú'][i % 5]}, ${addressOf(loc)}` }, extra || {}));
  if (u.status === 'TERMINATED') { u.terminatedAt = at(u.lastDay, '17:00'); u.terminatedBy = mgr.HN; }
});
// Staff tenant phụ
const secStaff = [
  ['letan.hoanggia', hoangGia, HG, secPos.hoanggia.leTan, R, 'Phan Thị Ngọc Diệp', 'FEMALE'],
  ['buongphong.hoanggia', hoangGia, HG, secPos.hoanggia.buong, HK, 'Nguyễn Văn Toàn', 'MALE'],
  ['letan.phoco', phoCo, PC, secPos.phoco.leTan, R, 'Trần Thị Thanh Tâm', 'FEMALE'],
  ['buongphong.phoco', phoCo, PC, secPos.phoco.buong, HK, 'Lê Văn Mạnh', 'MALE'],
];
secStaff.forEach(([key, t, loc, p, perms, name, gender], i) => user({ key, tenant: t, role: 'STAFF', email: key + '@swp391.test',
  name, loc, pos: p, perms, gender, dob: `199${i + 2}-0${i + 3}-1${i}`, start: loc === HG ? -57 : -65,
  address: loc.address }));

const hasPerm = (u, p) => u.perms.includes(p);
const staffOf = loc => users.filter(u => u.role === 'STAFF' && u.loc === loc);

// Quota
for (const t of tenants) {
  const locs = locations.filter(l => l.tenant === t).length;
  const staff = users.filter(u => u.tenant === t && u.role === 'STAFF' && u.status !== 'TERMINATED').length;
  t.usage = { locs, staff };
  check(locs <= t.subRow.quota_location, `${t.name}: vượt quota khách sạn`);
  check(staff <= t.subRow.quota_user, `${t.name}: vượt quota nhân viên (${staff})`);
}
// Một khách sạn tối đa 1 Manager chưa nghỉ; OPERATIONAL ⇔ có Manager đã tới ngày vào làm.
for (const l of locations) {
  const ms = users.filter(u => u.role === 'MANAGER' && u.loc === l && u.status !== 'TERMINATED');
  check(ms.length <= 1, `${l.name}: hơn 1 Manager`);
  const due = ms.some(m => m.start <= 0);
  check((l.status === 'OPERATIONAL') === due, `${l.name}: trạng thái không khớp Manager`);
}

// ── 3. Lịch ca ───────────────────────────────────────────────────────────────────
const shifts = [];
const shiftStart = s => s.day * DAY + s.start;
const shiftEnd = s => shiftStart(s) + 480;
for (const u of users) {
  if (!u.shift) continue;
  const tp = tplOf(u.loc)[u.shift];
  const first = Math.max(FROM, u.firstDay !== undefined ? u.firstDay : FROM);
  for (let d = first; d <= TO; d++) {
    if (mod(d - u.off, 7) === 0) continue;
    if (u.status === 'INACTIVE' && d > u.lastDay) continue;
    const s = { id: null, loc: u.loc, staff: u.id, origStaff: u.id, day: d, tpl: tp, start: tp.start,
      overnight: tp.end <= tp.start, checkIn: null, checkOut: null, reason: null, unassignedAt: null };
    if (u.status === 'TERMINATED' && d > u.lastDay) { s.staff = null; s.reason = 'TERMINATION'; s.unassignedAt = u.terminatedAt; }
    shifts.push(s);
  }
}
shifts.sort((a, b) => a.loc.key.localeCompare(b.loc.key) || a.day - b.day || a.start - b.start || a.origStaff.localeCompare(b.origStaff));
shifts.forEach(s => { s.id = newId('d8000000'); });
const shiftsOf = uid => shifts.filter(s => s.staff === uid);
const findShift = (key, d) => shifts.find(s => s.staff === byKey[key].id && s.day === d);

// ── Đơn nghỉ (áp trước để kiểm tra lịch) ─────────────────────────────────────────
const leaves = [];
function leave(o) {
  o.id = newId('d9000000');
  o.user = byKey[o.who];
  leaves.push(o);
  if (o.unassign) {   // đơn đã duyệt: gỡ người khỏi các ca chưa vào trong khoảng nghỉ
    for (const s of shifts) {
      if (s.staff === o.user.id && s.day >= o.from && s.day <= o.to) {
        s.staff = null; s.reason = 'LEAVE_APPROVED'; s.unassignedAt = o.decided;
      }
    }
  }
  return o;
}
leave({ who: 'buongphong.hn2', type: 'ANNUAL', from: -12, to: -11, status: 'APPROVED', created: at(-20, '09:10'),
  approver: mgr.HN, decided: at(-19, '08:30'), reason: 'Về quê dự đám cưới em gái.', unassign: true });
leave({ who: 'buongphong.hn3', type: 'ANNUAL', from: 3, to: 4, status: 'APPROVED', created: at(-6, '13:20'),
  approver: mgr.HN, decided: at(-5, '09:00'), reason: 'Đưa con đi khám định kỳ ở bệnh viện Nhi Trung ương.', unassign: true });
leave({ who: 'buongphong.hn4', type: 'ANNUAL', from: 5, to: 6, status: 'PENDING', created: at(-1, '16:45'),
  reason: 'Nghỉ phép năm, đi du lịch cùng gia đình.' });
const sickLeave = leave({ who: 'buongphong.hn6', type: 'UNPAID', from: 1, to: 1, status: 'PENDING', created: at(0, '06:40'),
  reason: 'Bị sốt, bác sĩ cho nghỉ 1 ngày — có giấy khám bệnh đính kèm.' });
leave({ who: 'buongphong.hn1', type: 'ANNUAL', from: 2, to: 2, status: 'REJECTED', created: at(-5, '10:00'),
  approver: mgr.HN, decided: at(-4, '08:15'), reason: 'Việc gia đình.',
  rejectReason: 'Ngày này khách sạn đón đoàn 40 khách, thiếu người dọn phòng. Bạn chọn ngày khác giúp chị nhé.' });
leave({ who: 'buongphong.dn2', type: 'UNPAID', from: 6, to: 6, status: 'CANCELLED', created: at(-3, '11:30'),
  updated: at(-2, '08:00'), reason: 'Đi làm giấy tờ nhà đất.' });
leave({ who: 'buongphong.dn1', type: 'ANNUAL', from: 4, to: 5, status: 'CANCELLED', created: at(-8, '09:00'),
  approver: mgr.DN, decided: at(-7, '10:30'), reason: 'Về quê thăm bố mẹ.', revokedBy: mgr.DN, revokedAt: at(-1, '17:20'),
  revokeReason: 'Khách đoàn đặt kín phòng hai ngày này; đã trao đổi, nhân viên đồng ý dời phép sang tuần sau.', unassign: true });
leave({ who: 'manager.dn', type: 'ANNUAL', from: 10, to: 11, status: 'PENDING', created: at(-2, '15:00'),
  reason: 'Tham dự lễ tốt nghiệp của con tại TP.HCM.' });

// ── Đổi ca đã duyệt trong quá khứ (áp vào lịch) ──────────────────────────────────
function staffErrors(uid) {
  const list = shiftsOf(uid).sort((a, b) => shiftStart(a) - shiftStart(b));
  const errs = [];
  const perDay = {};
  for (let i = 0; i < list.length; i++) {
    const s = list[i];
    perDay[s.day] = (perDay[s.day] || 0) + 8;
    if (perDay[s.day] > 8) errs.push(`quá 8h ngày ${s.day}`);
    if (i > 0 && shiftStart(s) - shiftEnd(list[i - 1]) < 12 * 60) errs.push(`nghỉ dưới 12h trước ca ngày ${s.day}`);
  }
  const days = new Set(list.map(s => s.day));
  let run = 0;
  for (let d = FROM - 7; d <= TO + 7; d++) {
    run = days.has(d) ? run + 1 : 0;
    if (run > 6) errs.push(`quá 6 ngày liên tiếp tới ngày ${d}`);
    let cnt = 0;
    for (let k = 0; k < 7; k++) if (days.has(d + k)) cnt++;
    if (cnt > 6) errs.push(`7 ngày từ ${d} không có ngày nghỉ`);
  }
  for (const l of leaves.filter(l => l.user.id === uid && l.status === 'APPROVED')) {
    for (const s of list) if (s.day >= l.from && s.day <= (l.to)) errs.push(`có ca trong ngày nghỉ phép ${s.day}`);
  }
  return errs;
}
// Mỗi người làm cố định 1 loại ca + nghỉ 1 ngày/7 ngày nên đổi 2 ca CÙNG loại khác ngày luôn vỡ luật
// "6 ngày liên tiếp". Cặp hợp lệ: người ca chiều (nghỉ hôm trước) ⇄ người ca sáng (nghỉ hôm sau) trong cùng 1 ngày.
const swaps = [];
function trySwap(a, b) {        // đổi người hai ca, trả về true nếu lịch cả hai vẫn hợp lệ (giữ nguyên thay đổi)
  const ua = a.staff, ub = b.staff;
  a.staff = ub; b.staff = ua;
  if (!staffErrors(ua).length && !staffErrors(ub).length) return true;
  a.staff = ua; b.staff = ub;
  return false;
}
const samePerms = (x, y) => x.pos === y.pos && [...x.perms].sort().join() === [...y.perms].sort().join();
const pendingShiftIds = new Set();
function findSwapPair(loc, requesterKeys, peerKeys, dFrom, dTo, apply) {
  for (const rk of requesterKeys) {
    const ru = byKey[rk];
    for (const a of shifts.filter(s => s.staff === ru.id && s.day >= dFrom && s.day <= dTo && !pendingShiftIds.has(s.id))) {
      for (const pk of peerKeys) {
        const pu = byKey[pk];
        if (pu === ru || !samePerms(ru, pu) || pu.loc !== loc) continue;
        for (const b of shifts.filter(s => s.staff === pu.id && s.day >= dFrom && s.day <= dTo && !pendingShiftIds.has(s.id))) {
          if (a.day === b.day && a.start === b.start) continue;   // phải khác giờ
          if (trySwap(a, b)) {
            if (!apply) { const t = a.staff; a.staff = b.staff; b.staff = t; }   // chỉ thử, trả lại như cũ
            return { a, b, ru, pu };
          }
        }
      }
    }
  }
  fail(`không tìm được cặp đổi ca hợp lệ cho ${requesterKeys.join(',')}`);
}
{
  const p = findSwapPair(HN, ['buongphong.hn5'], ['buongphong.hn2'], -16, -11, true);
  const first = Math.min(shiftStart(p.a), shiftStart(p.b));
  swaps.push({ loc: HN, requester: p.ru, peer: p.pu, rs: p.a, ps: p.b, status: 'APPROVED', created: first - 3 * DAY,
    peerAt: first - 3 * DAY + 120, expires: first - 2 * DAY, approver: mgr.HN, decided: first - 2 * DAY - 300 });
}

// ── Kiểm tra toàn bộ lịch ────────────────────────────────────────────────────────
function validateSchedule() {
  for (const u of users.filter(u => u.shift)) {
    const e = staffErrors(u.id);
    check(!e.length, `lịch ${u.key}: ${e.join('; ')}`);
  }
  const groups = {};
  for (const s of shifts) {
    if (!s.staff) continue;
    const k = `${s.loc.key}|${s.day}|${s.tpl.id}`;
    (groups[k] = groups[k] || []).push(byId[s.staff]);
  }
  for (const [k, us] of Object.entries(groups)) {
    check(us.some(u => hasPerm(u, 'RECEPTION')), `ca ${k} có người nhưng không có lễ tân`);
  }
  for (const u of users.filter(u => u.shift)) {
    const sts = shiftsOf(u.id);
    for (const s of sts) check(s.loc === u.loc, `${u.key} có ca ở khách sạn khác`);
    if (u.status === 'TERMINATED') check(!sts.some(s => s.day > u.lastDay), `${u.key} đã nghỉ việc vẫn còn ca`);
  }
}
validateSchedule();
const groupOf = s => shifts.filter(x => x.staff && x.loc === s.loc && x.day === s.day && x.tpl === s.tpl);
function soleReceptionist(uid, loc, day) {
  const s = shifts.find(x => x.staff === uid && x.loc === loc && x.day === day);
  if (!s || !hasPerm(byId[uid], 'RECEPTION')) return false;
  return groupOf(s).filter(u => hasPerm(byId[u.staff], 'RECEPTION')).length === 1;
}

// ── 4. Chấm công ─────────────────────────────────────────────────────────────────
const events = [];
const adjustments = [];
const forced = {
  'buongphong.hn1|-2': 'MISSED_OUT', 'letan.hn3|-4': 'ABSENT', 'giamsat.hn2|-6': 'MISSED_OUT',
  'kythuat.hn1|-8': 'ABSENT', 'letan.dn3|-5': 'MISSED_OUT', 'buongphong.hn5|-9': 'LATE', 'buongphong.dn3|-3': 'LATE',
  'kythuat.hn1|-3': 'MANAGER', 'buongphong.dn4|-6': 'MANAGER', 'buongphong.hn5|-1': 'OK',
};
function outcome(u, s) {
  const k = `${u.key}|${s.day}`;
  if (forced[k]) return forced[k];
  if (s.day >= 0) return 'OK';
  const h = hash(u.key, s.day) % 100;
  if (h < 4) return 'ABSENT';
  if (h < 9) return 'MISSED_OUT';
  if (h < 19) return 'LATE';
  if (h < 24) return 'EARLY';
  return 'OK';
}
function addEvent(s, u, type, t, method, recordedBy, recordedAt, note) {
  let net = 'NOT_CHECKED', ip = null, cidr = null;
  if (method === 'SELF') {
    ip = s.loc === HN ? '113.160.92.' + (20 + hash(u.key) % 200) : '14.161.20.' + (5 + hash(u.key) % 200);
    if (s.loc.ipMode !== 'OFF') {
      if (hash(u.key, s.day, type) % 23 === 7) { net = 'OUTSIDE'; ip = '27.72.100.' + (hash(u.key, s.day) % 250); }
      else { net = 'MATCHED'; cidr = '113.160.92.0/24'; }
    }
  }
  events.push({ id: newId('d8100000'), tenant_id: main.id, shift_id: s.id, staff_id: u.id, event_type: type,
    occurred_at: ts(t), recorded_at: ts(recordedAt || t), method, recorded_by: recordedBy.id,
    ip_address: ip, user_agent: method === 'SELF' ? UA : null, verification_type: 'NONE', note: note || null,
    network_check: net, matched_cidr: cidr, created_at: ts(recordedAt || t), created_by: recordedBy.id });
}
for (const s of shifts) {
  if (!s.staff) continue;
  const u = byId[s.staff];
  const st = shiftStart(s), en = shiftEnd(s);
  const past = s.day < 0;
  const todayRunning = s.day === 0 && s.tpl.start <= hm('07:00');   // ca sáng hôm nay đang làm
  if (!past && !todayRunning) continue;
  const o = outcome(u, s);
  const h = hash(u.key, s.day, 'm');
  if (o === 'ABSENT') continue;
  const inT = s.day === 0 ? st - 15 : o === 'LATE' ? st + 12 + h % 14 : st - (5 + h % 12);
  s.checkIn = inT;
  if (o === 'MANAGER') {
    const m = mgr[s.loc.key];
    addEvent(s, u, 'CHECK_IN', inT, 'MANAGER', m, st + 45, 'Nhân viên quên mang điện thoại — Manager ghi giờ vào theo sổ giao ca.');
  } else addEvent(s, u, 'CHECK_IN', inT, 'SELF', u);
  if (todayRunning || o === 'MISSED_OUT') continue;
  const outT = o === 'EARLY' ? en - (20 + h % 20) : en + (2 + h % 9);
  s.checkOut = outT;
  addEvent(s, u, 'CHECK_OUT', outT, 'SELF', u);
}
// Ca đêm hôm qua (kết thúc sáng nay) đã được vòng lặp trên ghi cả vào và ra.

function adj(key, day, status, o) {
  const u = byKey[key];
  const s = shifts.find(x => x.staff === u.id && x.day === day);
  check(s, `không có ca ${key} ngày ${day}`);
  check(day >= -31, 'đơn điều chỉnh quá 31 ngày');
  const a = Object.assign({ id: newId('d8200000'), shift: s, user: u, status }, o);
  if (status === 'APPROVED') {   // áp giờ vào ca như khi Manager duyệt
    const m = a.reviewer;
    if (a.inT !== undefined) { s.checkIn = a.inT; addEvent(s, u, 'CHECK_IN', a.inT, 'MANAGER', m, a.decided, 'Duyệt đơn điều chỉnh công: ' + a.reason); }
    if (a.outT !== undefined) { s.checkOut = a.outT; addEvent(s, u, 'CHECK_OUT', a.outT, 'MANAGER', m, a.decided, 'Duyệt đơn điều chỉnh công: ' + a.reason); }
  }
  adjustments.push(a);
  return a;
}
{
  const S = (k, d) => findShift(k, d);
  const st = (k, d) => shiftStart(S(k, d)), en = (k, d) => shiftEnd(S(k, d));
  adj('buongphong.hn1', -2, 'PENDING', { outT: en('buongphong.hn1', -2) + 4, created: en('buongphong.hn1', -2) + 600,
    reason: 'Hết ca vội bàn giao phòng 207 nên quên bấm ra ca.' });
  adj('letan.hn3', -4, 'PENDING', { inT: st('letan.hn3', -4) - 8, outT: en('letan.hn3', -4) + 5, created: at(-3, '09:30'),
    reason: 'Điện thoại hết pin cả ca nên không chấm công được, đã ký sổ giao ca.' });
  adj('giamsat.hn2', -6, 'PENDING', { outT: en('giamsat.hn2', -6) + 10, created: at(-5, '15:00'),
    reason: 'Kiểm tra phòng 401 đến sát giờ, quên bấm ra ca.' });
  adj('kythuat.hn1', -8, 'APPROVED', { inT: st('kythuat.hn1', -8) - 10, outT: en('kythuat.hn1', -8) + 6, created: at(-8, '19:00'),
    reason: 'App lỗi không bấm được, có camera sảnh xác nhận.', reviewer: mgr.HN, decided: at(-7, '09:20'),
    note: 'Đã đối chiếu camera sảnh.' });
  adj('letan.dn3', -5, 'APPROVED', { outT: en('letan.dn3', -5) + 3, created: at(-4, '10:00'),
    reason: 'Quên bấm ra ca, đã bàn giao cho ca đêm lúc 23:00.', reviewer: mgr.DN, decided: at(-4, '14:00'), note: null });
  adj('buongphong.hn5', -9, 'REJECTED', { inT: st('buongphong.hn5', -9), created: at(-8, '08:00'),
    reason: 'Em đến đúng giờ nhưng máy chấm chậm.', reviewer: mgr.HN, decided: at(-8, '11:00'),
    note: 'Camera ghi nhận bạn vào cổng sau giờ bắt đầu ca 15 phút, giữ nguyên giờ đã chấm.' });
  adj('buongphong.dn3', -3, 'CANCELLED', { inT: st('buongphong.dn3', -3) - 2, created: at(-3, '20:00'),
    reason: 'Xin sửa giờ vào ca.', decided: at(-2, '07:30') });
}

// ── Đổi ca đang chờ / đã khép ────────────────────────────────────────────────────
{
  const p1 = findSwapPair(HN, ['buongphong.hn5'], ['buongphong.hn2'], 1, 7, false);
  pendingShiftIds.add(p1.a.id); pendingShiftIds.add(p1.b.id);
  swaps.push({ loc: HN, requester: p1.ru, peer: p1.pu, rs: p1.a, ps: p1.b, status: 'PENDING_PEER',
    createdNow: -4 * 60, expiresNow: 20 * 60 });
  const p2 = findSwapPair(HN, ['buongphong.hn6'], ['buongphong.hn4'], 2, 6, false);
  pendingShiftIds.add(p2.a.id); pendingShiftIds.add(p2.b.id);
  swaps.push({ loc: HN, requester: p2.ru, peer: p2.pu, rs: p2.a, ps: p2.b, status: 'PENDING_MANAGER',
    createdNow: -6 * 60, peerNow: -2 * 60, expiresNow: 18 * 60 });
  const p3 = findSwapPair(DN, ['buongphong.dn3'], ['buongphong.dn1'], 1, 7, false);
  pendingShiftIds.add(p3.a.id); pendingShiftIds.add(p3.b.id);
  swaps.push({ loc: DN, requester: p3.ru, peer: p3.pu, rs: p3.a, ps: p3.b, status: 'PENDING_PEER',
    createdNow: -3 * 60, expiresNow: 21 * 60 });
  // Đã khép — không đụng lịch.
  const closed = (loc, rk, pk, d1, d2, status, extra) => {
    const a = findShift(rk, d1), b = findShift(pk, d2);
    check(a && b, `thiếu ca cho đổi ca ${status}`);
    const first = Math.min(shiftStart(a), shiftStart(b));
    swaps.push(Object.assign({ loc, requester: byKey[rk], peer: byKey[pk], rs: a, ps: b, status,
      created: first - 3 * DAY, expires: first - 2 * DAY }, extra(first)));
  };
  const dShift = (k, from) => { for (let d = from; d < 0; d++) if (findShift(k, d)) return d; return null; };
  closed(HN, 'buongphong.hn3', 'buongphong.hn4', dShift('buongphong.hn3', -20), dShift('buongphong.hn4', -19), 'PEER_REJECTED',
    f => ({ peerAt: f - 3 * DAY + 240 }));
  closed(DN, 'buongphong.dn1', 'buongphong.dn2', dShift('buongphong.dn1', -16), dShift('buongphong.dn2', -15), 'MANAGER_REJECTED',
    f => ({ peerAt: f - 3 * DAY + 90, approver: mgr.DN, decided: f - 2 * DAY - 200 }));
  closed(HN, 'letan.hn2', 'letan.hn3', dShift('letan.hn2', -10), dShift('letan.hn3', -9), 'EXPIRED',
    f => ({ decided: f - 2 * DAY }));
  closed(HN, 'buongphong.hn6', 'buongphong.hn5', dShift('buongphong.hn6', -6), dShift('buongphong.hn5', -5), 'CANCELLED',
    f => ({ decided: f - 3 * DAY + 60 }));
}
for (const s of swaps.filter(s => s.status.startsWith('PENDING'))) {
  check(s.rs.day >= 1 && s.ps.day >= 1, 'đổi ca đang chờ phải là ca tương lai');
  check(s.rs.staff === s.requester.id && s.ps.staff === s.peer.id, 'đổi ca đang chờ sai người');
}

// ── 5. Phòng, việc dọn ───────────────────────────────────────────────────────────
const rooms = [];
const tasks = [];
const history = [];
const inspections = [];
const returnsRows = [];
const ALLOWED = { null: ['DIRTY'], AVAILABLE: ['RESERVED', 'OCCUPIED', 'UNAVAILABLE'], RESERVED: ['OCCUPIED', 'AVAILABLE', 'UNAVAILABLE'],
  OCCUPIED: ['DIRTY'], DIRTY: ['CLEANING', 'UNAVAILABLE'], CLEANING: ['INSPECTION', 'DIRTY', 'UNAVAILABLE'],
  INSPECTION: ['AVAILABLE', 'DIRTY', 'UNAVAILABLE'], UNAVAILABLE: ['DIRTY', 'AVAILABLE'] };

function onDuty(loc, t, pred) {     // người đã vào ca và đang trong ca tại thời điểm t
  return shifts.filter(s => s.staff && s.loc === loc && s.checkIn !== null && s.checkIn <= t &&
    t < shiftEnd(s) && (s.checkOut === null || s.checkOut > t) && pred(byId[s.staff]))
    .map(s => byId[s.staff]).sort((a, b) => a.key.localeCompare(b.key));
}
function receptionist(loc, t) {
  if (dayOf(t) < FROM) return staffOf(loc).find(u => hasPerm(u, 'RECEPTION') && u.status === 'ACTIVE');
  const r = onDuty(loc, t, u => hasPerm(u, 'RECEPTION'));
  check(r.length, `không có lễ tân đang trực tại ${loc.key} lúc ngày ${dayOf(t)} ${time(t - dayOf(t) * DAY)}`);
  return r[hash(loc.key, t) % r.length];
}
function move(room, t, to, source, by, task, reason) {
  check(ALLOWED[String(room.status)].includes(to), `phòng ${room.number}: không được chuyển ${room.status} → ${to}`);
  check(t > room.lastT, `phòng ${room.number}: sự kiện ngày ${dayOf(t)} không theo thứ tự thời gian`);
  check((source === 'SYSTEM') === (by === null), `phòng ${room.number}: nguồn ${source} sai người thực hiện`);
  history.push({ id: newId('da100000'), tenant_id: room.loc.tenant.id, room_id: room.id, from_status: room.status,
    to_status: to, changed_by: by ? by.id : null, changed_at: ts(t), change_source: source, reason: reason || null,
    related_task_id: task ? task.id : null, created_at: ts(t) });
  room.status = to; room.lastT = t;
}
function newTask(room, t, source, parent) {
  const task = { id: newId('da000000'), room, status: 'UNASSIGNED', created: t, source, parent: parent || null,
    assignees: [], assignedDate: null, assignedBy: null, assignedAt: null, assignSource: null, completedAt: null,
    unassignedReason: null, cancelReason: null, cancelledAt: null, note: null, busyUntil: null };
  tasks.push(task);
  room.openTask = task;
  return task;
}
function createRoom(room, t) {      // phòng mới: Chờ dọn + việc dọn chờ giao, như RoomService
  room.created = t;
  const task = newTask(room, t, 'CHECKOUT_AUTO');
  move(room, t, 'DIRTY', 'SYSTEM', null, task, 'Tạo phòng mới');
}
const checkIn = (room, t) => move(room, t, 'OCCUPIED', 'RECEPTION', receptionist(room.loc, t), null);
const reserve = (room, t) => move(room, t, 'RESERVED', 'RECEPTION', receptionist(room.loc, t), null);
function checkOut(room, t) {
  check(!room.openTask, `phòng ${room.number} còn việc dọn đang mở`);
  const r = receptionist(room.loc, t);
  const task = newTask(room, t, 'CHECKOUT_AUTO');
  move(room, t, 'DIRTY', 'RECEPTION', r, task);
}
function assertAssignee(u, room, t, source) {
  check(hasPerm(u, 'HOUSEKEEPING'), `${u.key} không có quyền dọn`);
  const day = dayOf(t);
  if (day < FROM) return;
  const s = shifts.find(x => x.staff === u.id && x.loc === room.loc && (x.day === day || (x.day === day - 1 && x.overnight)) &&
    shiftStart(x) - 30 <= t && t < shiftEnd(x));
  check(s, `${u.key} không có ca lúc được giao phòng ${room.number} (ngày ${day})`);
  check(s.checkIn !== null && s.checkIn <= t, `${u.key} chưa vào ca khi được giao phòng ${room.number}`);
  check(!soleReceptionist(u.id, room.loc, s.day), `${u.key} là lễ tân duy nhất của ca, không giao dọn được`);
  if (source === 'SELF_CLAIM') {
    const busy = tasks.some(x => x.assignees.includes(u) && x.assignedAt !== null && x.assignedAt < t && (x.busyUntil === null || x.busyUntil > t) && x.status !== 'UNASSIGNED');
    check(!busy, `${u.key} đang giữ việc khác khi tự nhận phòng ${room.number}`);
  }
}
function assign(room, t, by, source, cleaners) {
  const task = room.openTask;
  check(task && task.status === 'UNASSIGNED', `phòng ${room.number} không có việc chờ giao`);
  cleaners.forEach(u => assertAssignee(u, room, t, source));
  if (source === 'SUPERVISION' && dayOf(t) >= FROM) check(onDuty(room.loc, t, u => u === by).length, `${by.key} giám sát không trong ca`);
  Object.assign(task, { status: 'IN_PROGRESS', assignees: cleaners.slice(), assignedDate: dayOf(t), assignedBy: by,
    assignedAt: t, assignSource: source, unassignedReason: null });
  move(room, t, 'CLEANING', 'SYSTEM', null, task);
}
function complete(room, t, u, note) {
  const task = room.openTask;
  check(task.status === 'IN_PROGRESS' && task.assignees.includes(u), `phòng ${room.number}: ${u.key} không trong nhóm dọn`);
  task.status = 'PENDING_INSPECTION'; task.note = note || null; task.busyUntil = t;
  move(room, t, 'INSPECTION', 'HOUSEKEEPING', u, task);
}
function inspect(room, t, inspector, source, result, reason) {
  const task = room.openTask;
  check(task.status === 'PENDING_INSPECTION', `phòng ${room.number} chưa chờ kiểm tra`);
  check(!task.assignees.includes(inspector), `${inspector.key} vừa dọn vừa kiểm tra phòng ${room.number}`);
  if (source === 'SUPERVISION' && dayOf(t) >= FROM) check(onDuty(room.loc, t, u => u === inspector).length, `${inspector.key} kiểm tra ngoài ca`);
  task.status = 'COMPLETED'; task.completedAt = t;
  const rec = { id: newId('da200000'), tenant_id: room.loc.tenant.id, task_id: task.id, room_id: room.id,
    inspector_id: inspector.id, result, reason: reason || null, inspected_at: ts(t), next_task_id: null, created_at: ts(t) };
  inspections.push(rec);
  room.openTask = null;
  if (result === 'PASS') move(room, t, 'AVAILABLE', source, inspector, task);
  else {
    const next = newTask(room, t, 'INSPECTION_FAILED', task);
    rec.next_task_id = next.id;
    move(room, t, 'DIRTY', source, inspector, next, reason);
  }
}
function selfReturn(room, t, u, reason) {
  const task = room.openTask;
  check(task.status === 'IN_PROGRESS' && task.assignSource === 'SELF_CLAIM' && task.assignedBy === u, 'chỉ trả được việc tự nhận');
  returnsRows.push({ id: newId('da300000'), tenant_id: room.loc.tenant.id, task_id: task.id, staff_id: u.id, reason,
    returned_at: ts(t), task_released: true, created_at: ts(t), created_by: u.id });
  Object.assign(task, { status: 'UNASSIGNED', assignees: [], assignedDate: null, assignedBy: null, assignedAt: null,
    assignSource: null, unassignedReason: 'SELF_RETURNED', busyUntil: t });
  move(room, t, 'DIRTY', 'HOUSEKEEPING', u, task);
}
function makeUnavailable(room, t, m, reason) {
  const task = room.openTask;
  if (task) { task.status = 'CANCELLED'; task.cancelReason = 'ROOM_UNAVAILABLE'; task.cancelledAt = t; room.openTask = null; }
  move(room, t, 'UNAVAILABLE', 'MANAGER', m, null, reason);
  room.unavailable = reason;
}

function addRooms(loc, list) {
  for (const [number, floor, type, cap] of list) {
    rooms.push({ id: newId('d6100000'), loc, number, floor, type, cap, status: null, lastT: -1e9, openTask: null, unavailable: null });
  }
}
const roomsAt = loc => rooms.filter(r => r.loc === loc);
const room = (loc, n) => rooms.find(r => r.loc === loc && r.number === n);
{
  const hnTypes = [RT.std, RT.std, RT.std, RT.sup, RT.sup, RT.sup, RT.dlx, RT.dlx, RT.fam, RT.suite];
  const list = [];
  for (const f of [2, 3, 4]) for (let i = 1; i <= 10; i++) { const t = hnTypes[i - 1]; list.push([`${f}${pad(i)}`, String(f), t, t === RT.fam || t === RT.suite ? 4 : 2]); }
  addRooms(HN, list);
  const dnTypes = [RT.std, RT.std, RT.sup, RT.sup, RT.dlx, RT.dlx, RT.fam, RT.suite];
  const l2 = [];
  for (const f of [1, 2, 3]) for (let i = 1; i <= 8; i++) { const t = dnTypes[i - 1]; l2.push([`${f}${pad(i)}`, String(f), t, t === RT.fam || t === RT.suite ? 4 : 2]); }
  addRooms(DN, l2);
  addRooms(HL, [['101', '1', RT.std, 2], ['102', '1', RT.std, 2], ['201', '2', RT.dlx, 2], ['202', '2', RT.fam, 4]]);
  addRooms(HG, [['B01', '1', RTsec.HG, 2], ['B02', '1', RTsec.HG, 2], ['B03', '1', RTsec.HG, 2], ['B04', '1', RTsec.HG, 3], ['B05', '1', RTsec.HG, 3], ['B06', '1', RTsec.HG, 4]]);
  addRooms(PC, [['101', '1', RTsec.PC, 2], ['102', '1', RTsec.PC, 2], ['201', '2', RTsec.PC, 2], ['202', '2', RTsec.PC, 2]]);
}
const roomQuota = t => rooms.filter(r => r.loc.tenant === t).length;
for (const t of tenants) { t.usage.rooms = roomQuota(t); check(t.usage.rooms <= t.subRow.quota_room, `${t.name}: vượt quota phòng`); }

// Kịch bản hôm nay cho từng phòng — b = độ lệch giờ của khách sạn (Đà Nẵng vào ca muộn hơn 1 giờ).
const U = k => byKey[k];
function scenarioFor(loc) {
  const b = loc.base;
  const T = (d, s) => at(d, s) + b;
  const occ = r => checkIn(r, T(-1, '15:30'));
  const S = {};
  if (loc === HN) {
    Object.assign(S, {
      '201': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '202': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '203': { start: T(0, '07:30'), run: r => reserve(r, T(0, '07:30')), label: 'Đã đặt — khách đến chiều nay' },
      '205': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '08:00')); }, label: 'Chờ dọn — việc chờ giao' },
      '206': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '07:00')); assign(r, T(0, '07:50'), mgr.HN, 'MANAGER', [U('buongphong.hn1')]); },
        label: 'Đang dọn — Hoa (Manager giao)' },
      '207': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '07:10')); assign(r, T(0, '07:25'), U('giamsat.hn1'), 'SUPERVISION', [U('buongphong.hn2'), U('buongphong.hn3')]); },
        label: 'Đang dọn — Nam + Mai (Giám sát giao)' },
      '208': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '06:15')); assign(r, T(0, '06:30'), mgr.HN, 'MANAGER', [U('buongphong.hn4')]); complete(r, T(0, '07:30'), U('buongphong.hn4'), 'Đã thay ga, bổ sung nước suối và khăn tắm.'); },
        label: 'Chờ kiểm tra — Ngọc đã báo xong' },
      '209': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '06:20')); assign(r, T(0, '06:55'), mgr.HN, 'MANAGER', [U('buongphong.hn1')]); complete(r, T(0, '07:35'), U('buongphong.hn1')); inspect(r, T(0, '07:45'), U('giamsat.hn1'), 'SUPERVISION', 'PASS'); },
        label: 'Sẵn sàng — dọn xong sáng nay, giám sát kiểm tra ĐẠT' },
      '210': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '301': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '06:10')); assign(r, T(0, '06:25'), mgr.HN, 'MANAGER', [U('buongphong.hn2')]); complete(r, T(0, '07:00'), U('buongphong.hn2')); inspect(r, T(0, '07:10'), mgr.HN, 'MANAGER', 'FAIL', 'Còn tóc trên gối, gương nhà tắm chưa lau.'); },
        label: 'Chờ dọn lại — kiểm tra KHÔNG ĐẠT' },
      '302': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '07:15')); assign(r, T(0, '07:40'), U('buongphong.hn4'), 'SELF_CLAIM', [U('buongphong.hn4')]); },
        label: 'Đang dọn — Ngọc tự nhận' },
      '303': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '06:30')); assign(r, T(0, '06:40'), U('buongphong.hn3'), 'SELF_CLAIM', [U('buongphong.hn3')]); selfReturn(r, T(0, '07:05'), U('buongphong.hn3'), 'Vòi sen bị rò, đã báo kỹ thuật — trả việc để nhận lại sau khi sửa.'); },
        label: 'Chờ dọn — Mai tự nhận rồi trả việc' },
      '304': { start: T(-2, '15:30'), run: r => { checkIn(r, T(-2, '15:30')); checkOut(r, T(-1, '15:45')); assign(r, T(-1, '16:00'), mgr.HN, 'MANAGER', [U('buongphong.hn5')]); },
        label: 'Đang dọn — Lực nhận từ HÔM QUA chưa xong (tồn đọng)' },
      '305': { start: T(-2, '15:30'), run: r => { checkIn(r, T(-2, '15:30')); checkOut(r, T(-1, '10:00')); makeUnavailable(r, T(-1, '11:00'), mgr.HN, 'Rò rỉ nước nhà tắm, chờ thợ sửa đường ống.'); },
        label: 'Không khả dụng — việc dọn bị hủy' },
      '306': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '05:50')); assign(r, T(0, '06:05'), mgr.HN, 'MANAGER', [U('buongphong.hn1')]); complete(r, T(0, '06:50'), U('buongphong.hn1')); },
        label: 'Chờ kiểm tra — Hoa đã báo xong' },
      '308': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '309': { start: T(0, '07:20'), run: r => reserve(r, T(0, '07:20')), label: 'Đã đặt' },
      '401': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '402': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '404': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '406': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '408': { start: T(0, '07:05'), run: r => reserve(r, T(0, '07:05')), label: 'Đã đặt' },
      '410': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '07:55')); }, label: 'Chờ dọn — việc chờ giao' },
    });
  } else if (loc === DN) {
    Object.assign(S, {
      '101': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '102': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '103': { start: T(0, '07:30'), run: r => reserve(r, T(0, '07:30')), label: 'Đã đặt' },
      '105': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '07:05')); assign(r, T(0, '07:20'), mgr.DN, 'MANAGER', [U('buongphong.dn1')]); },
        label: 'Đang dọn — Lệ (Manager giao)' },
      '106': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '06:20')); assign(r, T(0, '06:30'), mgr.DN, 'MANAGER', [U('buongphong.dn2')]); complete(r, T(0, '07:30'), U('buongphong.dn2'), 'Phòng có vết bẩn trên thảm, đã giặt tại chỗ.'); },
        label: 'Chờ kiểm tra — Thắm đã báo xong' },
      '107': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '06:00')); assign(r, T(0, '06:10'), U('giamsat.dn1'), 'SUPERVISION', [U('buongphong.dn1')]); complete(r, T(0, '07:00'), U('buongphong.dn1')); inspect(r, T(0, '07:10'), U('giamsat.dn1'), 'SUPERVISION', 'PASS'); },
        label: 'Sẵn sàng — giám sát kiểm tra ĐẠT' },
      '108': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '08:10')); }, label: 'Chờ dọn — việc chờ giao' },
      '201': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '05:45')); assign(r, T(0, '05:55'), mgr.DN, 'MANAGER', [U('buongphong.dn2')]); complete(r, T(0, '06:20'), U('buongphong.dn2')); inspect(r, T(0, '06:28'), mgr.DN, 'MANAGER', 'FAIL', 'Minibar chưa bổ sung, rèm cửa chưa kéo gọn.'); },
        label: 'Chờ dọn lại — kiểm tra KHÔNG ĐẠT' },
      '202': { start: T(-1, '15:30'), run: r => { occ(r); checkOut(r, T(0, '07:40')); }, label: 'Chờ dọn — việc chờ giao' },
      '203': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '205': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '207': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '302': { start: T(0, '07:15'), run: r => reserve(r, T(0, '07:15')), label: 'Đã đặt' },
      '303': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
      '305': { start: T(-1, '15:30'), run: occ, label: 'Đang có khách' },
    });
  }
  return S;
}

// Dựng lịch sử: tạo phòng → các lượt khách & dọn 7 ngày trước → kịch bản hôm nay.
const scenarioLabels = {};
for (const loc of [HN, DN, HG, PC, HL]) {
  const m = mgr[loc.key];
  const createDay = loc === HL ? -18 : loc === HG ? -40 : loc === PC ? -45 : -60;
  const cleanersAll = staffOf(loc).filter(u => hasPerm(u, 'HOUSEKEEPING') && u.pos.position_type === 'HOUSEKEEPING');
  roomsAt(loc).forEach((r, i) => {
    createRoom(r, at(createDay, '08:00') + i);
    if (loc === HL) return;   // khách sạn chưa hoạt động: phòng mới tạo, việc dọn chờ giao
    assign(r, at(createDay, '09:00') + i, m, 'MANAGER', [cleanersAll[i % cleanersAll.length]]);
    complete(r, at(createDay, '10:00') + i, cleanersAll[i % cleanersAll.length]);
    inspect(r, at(createDay, '10:30') + i, m, 'MANAGER', 'PASS');
  });
  if (loc !== HN && loc !== DN) continue;
  const S = scenarioFor(loc);
  const b = loc.base;
  const list = roomsAt(loc);
  let ptr = 0;
  for (let X = -7; X <= -1; X++) {
    const want = loc === HN ? 3 + (mod(X, 2)) : 2;
    let done = 0, tries = 0;
    while (done < want && tries < list.length * 2) {
      const r = list[ptr % list.length]; ptr++; tries++;
      const sc = S[r.number];
      const inT = at(X - 1, '15:30') + b + done, outT = at(X, '09:00') + b + done * 5;
      if (sc && sc.start <= at(X, '11:30') + b) continue;
      if (r.lastT >= inT || r.status !== 'AVAILABLE') continue;
      const cleaners = onDuty(loc, at(X, '09:20') + b, u => u.pos.position_type === 'HOUSEKEEPING');
      const sups = onDuty(loc, at(X, '09:20') + b, u => hasPerm(u, 'SUPERVISION') && !hasPerm(u, 'HOUSEKEEPING'))
        .concat(onDuty(loc, at(X, '09:20') + b, u => u.key === 'giamsat.dn1'));
      if (!cleaners.length) break;
      const team = [cleaners[(done + mod(X, 3)) % cleaners.length]];
      if (done === 1 && cleaners.length > 2) team.push(cleaners[(done + 1 + mod(X, 3)) % cleaners.length]);
      checkIn(r, inT);
      checkOut(r, outT);
      const bySup = sups.length && done % 2 === 1 && !team.includes(sups[0]);
      assign(r, at(X, '09:20') + b + done * 5, bySup ? sups[0] : m, bySup ? 'SUPERVISION' : 'MANAGER', team);
      complete(r, at(X, '10:30') + b + done * 7, team[0], done === 0 && X === -3 ? 'Khách để quên sạc điện thoại, đã gửi lễ tân.' : null);
      const inspBySup = sups.length && !team.includes(sups[0]) && done % 2 === 0;
      inspect(r, at(X, '10:50') + b + done * 7, inspBySup ? sups[0] : m, inspBySup ? 'SUPERVISION' : 'MANAGER', 'PASS');
      done++;
    }
    check(done === want, `${loc.key}: không xếp đủ lượt dọn ngày ${X}`);
  }
  for (const r of list) {
    const sc = S[r.number];
    if (sc) sc.run(r);
    scenarioLabels[`${loc.key}|${r.number}`] = sc ? sc.label : 'Sẵn sàng';
  }
}
// Mỗi phòng: trạng thái ↔ việc dọn đang mở khớp nhau.
for (const r of rooms) {
  const open = tasks.filter(t => t.room === r && ['UNASSIGNED', 'IN_PROGRESS', 'PENDING_INSPECTION'].includes(t.status));
  check(open.length <= 1, `phòng ${r.number} có hơn 1 việc dọn mở`);
  const st = open[0] ? open[0].status : null;
  const expect = { DIRTY: 'UNASSIGNED', CLEANING: 'IN_PROGRESS', INSPECTION: 'PENDING_INSPECTION' }[r.status] || null;
  check(st === expect, `phòng ${r.loc.key}-${r.number} (${r.status}) lệch việc dọn (${st})`);
  if (st === 'IN_PROGRESS') check(open[0].assignees.length > 0, `phòng ${r.number} đang dọn mà không có người`);
}

// ── 6. Tài sản ───────────────────────────────────────────────────────────────────
const cats = [];
function cat(name, kind, purpose, unit) { const c = { id: newId('db000000'), tenant_id: main.id, name, asset_kind: kind, purpose, unit: unit || null }; cats.push(c); return c; }
const C = {
  tv: cat('Smart TV', 'FIXED', 'GUEST_USE'), dieuHoa: cat('Điều hòa', 'FIXED', 'GUEST_USE'),
  tuLanh: cat('Tủ lạnh mini bar', 'FIXED', 'GUEST_USE'), ket: cat('Két sắt', 'FIXED', 'GUEST_USE'),
  hutBui: cat('Máy hút bụi', 'FIXED', 'FACILITY_MAINTENANCE'), mayGiat: cat('Máy giặt công nghiệp', 'FIXED', 'FACILITY_MAINTENANCE'),
  mayChay: cat('Máy chạy bộ', 'FIXED', 'GUEST_USE'), mayTinh: cat('Máy tính lễ tân', 'FIXED', 'FACILITY_MAINTENANCE'),
  bom: cat('Máy bơm lọc bể bơi', 'FIXED', 'FACILITY_MAINTENANCE'), tuMat: cat('Tủ mát nhà hàng', 'FIXED', 'FACILITY_MAINTENANCE'),
  nuoc: cat('Nước suối 500ml', 'CONSUMABLE', 'GUEST_USE', 'Chai'), khan: cat('Khăn tắm', 'CONSUMABLE', 'GUEST_USE', 'Cái'),
  dauGoi: cat('Dầu gội mini', 'CONSUMABLE', 'GUEST_USE', 'Chai'), banChai: cat('Bộ bàn chải đánh răng', 'CONSUMABLE', 'GUEST_USE', 'Bộ'),
  giay: cat('Giấy vệ sinh', 'CONSUMABLE', 'GUEST_USE', 'Cuộn'), lauSan: cat('Nước lau sàn', 'CONSUMABLE', 'FACILITY_MAINTENANCE', 'Can'),
  tuiRac: cat('Túi rác', 'CONSUMABLE', 'FACILITY_MAINTENANCE', 'Cuộn'), bongDen: cat('Bóng đèn LED', 'CONSUMABLE', 'FACILITY_MAINTENANCE', 'Cái'),
};
const fixed = [];
const codeSeq = {};
function asset(loc, c, name, place, status, note) {
  codeSeq[loc.key] = (codeSeq[loc.key] || 0) + 1;
  const a = { id: newId('db100000'), tenant_id: main.id, location_id: loc.id, category_id: c.id,
    asset_code: 'TS-' + String(codeSeq[loc.key]).padStart(5, '0'), name,
    room_id: place.number ? place.id : null, area_id: place.number ? null : place.id, status: status || 'GOOD',
    note: note || null, created_at: ts(at(loc === HN ? -59 : -58, '14:00') + codeSeq[loc.key]), loc };
  fixed.push(a);
  return a;
}
const AS = {};
for (const loc of [HN, DN]) {
  for (const r of roomsAt(loc)) {
    AS[`${loc.key}-tv-${r.number}`] = asset(loc, C.tv, `Smart TV 43" phòng ${r.number}`, r);
    AS[`${loc.key}-dh-${r.number}`] = asset(loc, C.dieuHoa, `Điều hòa phòng ${r.number}`, r);
    if (r.type === RT.dlx || r.type === RT.suite) {
      AS[`${loc.key}-tl-${r.number}`] = asset(loc, C.tuLanh, `Tủ lạnh mini bar phòng ${r.number}`, r);
      AS[`${loc.key}-ket-${r.number}`] = asset(loc, C.ket, `Két sắt phòng ${r.number}`, r);
    }
  }
}
const ar = A.HN;
AS.hnPc1 = asset(HN, C.mayTinh, 'Máy tính lễ tân quầy 1', ar.sanh);
AS.hnPc2 = asset(HN, C.mayTinh, 'Máy tính lễ tân quầy 2', ar.sanh);
AS.hnDhSanh = asset(HN, C.dieuHoa, 'Điều hòa âm trần sảnh', ar.sanh, 'UNDER_REPAIR', 'Đang chờ hãng thay block.');
AS.hnTuMat = asset(HN, C.tuMat, 'Tủ mát quầy bar', ar.nhaHang);
AS.hnRun1 = asset(HN, C.mayChay, 'Máy chạy bộ số 1', ar.gym, 'BROKEN');
AS.hnRun2 = asset(HN, C.mayChay, 'Máy chạy bộ số 2', ar.gym);
AS.hnHb1 = asset(HN, C.hutBui, 'Máy hút bụi số 1', ar.kho);
AS.hnHb2 = asset(HN, C.hutBui, 'Máy hút bụi số 2', ar.kho, 'BROKEN');
AS.hnHb3 = asset(HN, C.hutBui, 'Máy hút bụi số 3 (cũ)', ar.kho, 'DISPOSED', 'Hỏng mô tơ, sửa không kinh tế — đã thanh lý.');
AS.hnMg1 = asset(HN, C.mayGiat, 'Máy giặt công nghiệp 25kg', ar.giat, 'UNDER_REPAIR', 'Thay dây curoa, dự kiến xong trong tuần.');
AS.hnMg2 = asset(HN, C.mayGiat, 'Máy giặt công nghiệp 15kg', ar.giat);
const ad = A.DN;
AS.dnPc1 = asset(DN, C.mayTinh, 'Máy tính lễ tân', ad.sanh);
AS.dnTuMat = asset(DN, C.tuMat, 'Tủ mát nhà hàng', ad.nhaHang);
AS.dnBom = asset(DN, C.bom, 'Máy bơm lọc bể bơi', ad.beBoi, 'UNDER_REPAIR', 'Kỹ thuật đang thay phớt bơm.');
AS.dnHb1 = asset(DN, C.hutBui, 'Máy hút bụi số 1', ad.kho);
AS.dnHb2 = asset(DN, C.hutBui, 'Máy hút bụi số 2', ad.kho);
AS['HN-tv-210'].status = 'BROKEN';
AS['DN-tv-204'].status = 'BROKEN';
AS['DN-tv-301'].status = 'DISPOSED'; AS['DN-tv-301'].note = 'Màn hình vỡ, đã thay TV mới.';
AS['DN-tv-301b'] = asset(DN, C.tv, 'Smart TV 50" phòng 301 (thay mới)', room(DN, '301'));

const damages = [];
function damage(a, reporterKey, desc, reportedT, resolve) {
  const reporter = byKey[reporterKey];
  check(reporter.loc.id === a.location_id && (hasPerm(reporter, 'RECEPTION') || hasPerm(reporter, 'HOUSEKEEPING')), `${reporterKey} không được báo hỏng ở đây`);
  check(a.status !== 'DISPOSED' || resolve, 'không báo hỏng tài sản đã thanh lý');
  damages.push({ id: newId('db300000'), tenant_id: main.id, location_id: a.location_id, fixed_asset_id: a.id, reporter_id: reporter.id,
    description: desc, status: resolve ? 'RESOLVED' : 'NEW', reported_at: ts(reportedT),
    resolved_by: resolve ? resolve.by.id : null, resolved_at: resolve ? ts(resolve.t) : null,
    resolution_note: resolve ? resolve.note : null, created_at: ts(reportedT) });
}
damage(AS['HN-tv-210'], 'buongphong.hn1', 'TV không lên hình, chỉ có tiếng. Khách phòng 210 phản ánh tối qua.', at(0, '07:20'));
damage(AS.hnHb2, 'buongphong.hn2', 'Máy hút bụi số 2 kêu to và có mùi khét khi bật.', at(-1, '10:40'));
damage(AS.hnRun1, 'letan.hn3', 'Khách báo máy chạy bộ số 1 dừng đột ngột khi đang chạy.', at(-2, '18:15'));
damage(AS['DN-tv-204'], 'letan.dn3', 'Remote TV phòng 204 không điều khiển được, đã thay pin vẫn không nhận.', at(-1, '20:30'));
damage(AS['HN-ket-410'], 'letan.hn1', 'Két sắt phòng 410 báo lỗi E3, khách không mở được.', at(0, '06:50'));
damage(AS['DN-tl-206'], 'buongphong.dn2', 'Tủ lạnh mini bar phòng 206 không lạnh.', at(-3, '09:10'));
damage(AS.hnDhSanh, 'letan.hn2', 'Điều hòa sảnh chảy nước xuống quầy lễ tân.', at(-9, '08:30'),
  { by: mgr.HN, t: at(-8, '10:00'), note: 'Đã gọi hãng bảo hành, chuyển sang Đang sửa.' });
damage(AS.hnMg1, 'buongphong.hn5', 'Máy giặt 25kg không vắt, lồng quay rất chậm.', at(-6, '16:00'),
  { by: mgr.HN, t: at(-6, '17:30'), note: 'Kỹ thuật xác định đứt dây curoa, đã đặt phụ tùng.' });
damage(AS.dnBom, 'letan.dn1', 'Nước bể bơi đục, máy bơm lọc phát tiếng rít.', at(-4, '08:00'),
  { by: mgr.DN, t: at(-4, '11:00'), note: 'Tạm đóng bể bơi buổi chiều, chuyển máy bơm sang Đang sửa.' });
damage(AS['HN-tv-202'], 'buongphong.hn3', 'TV phòng 202 không bắt được kênh truyền hình.', at(-5, '09:00'),
  { by: U('giamsat.hn1'), t: at(-5, '09:40'), note: 'Dây cáp bị lỏng, đã cắm lại — hoạt động bình thường.' });

const consumables = [];
function stock(loc, c, qty, countedDay, byKey2) {
  consumables.push({ id: newId('db200000'), tenant_id: main.id, location_id: loc.id, category_id: c.id, quantity: qty,
    last_counted_at: ts(at(countedDay, '16:30')), last_counted_by: (byKey[byKey2] || mgr[loc.key]).id, created_at: ts(at(-58, '15:00')) });
}
stock(HN, C.nuoc, 420, -1, 'manager.hn'); stock(HN, C.khan, 180, -1, 'giamsat.hn1'); stock(HN, C.dauGoi, 95, -3, 'giamsat.hn1');
stock(HN, C.banChai, 140, -3, 'giamsat.hn1'); stock(HN, C.giay, 60, -2, 'manager.hn'); stock(HN, C.lauSan, 8, -7, 'manager.hn');
stock(HN, C.tuiRac, 25, -7, 'manager.hn'); stock(HN, C.bongDen, 0, -2, 'manager.hn');
stock(DN, C.nuoc, 310, -1, 'manager.dn'); stock(DN, C.khan, 150, -2, 'giamsat.dn1'); stock(DN, C.dauGoi, 12, -2, 'giamsat.dn1');
stock(DN, C.banChai, 88, -4, 'giamsat.dn1'); stock(DN, C.giay, 70, -4, 'manager.dn'); stock(DN, C.lauSan, 5, -6, 'manager.dn');
stock(DN, C.tuiRac, 18, -6, 'manager.dn');

// ── Ghi SQL ──────────────────────────────────────────────────────────────────────
const HEADER_COMMON = `-- CHỈ dùng cho dev/demo — cùng cơ chế với R__seed_test_accounts.sql. FILE SINH TỰ ĐỘNG, đừng sửa tay:
--   node backend/scripts/demo-seed/generate-demo-seed.js
-- NGÀY TƯƠNG ĐỐI: @d0 = CURRENT_DATE lúc Flyway chạy file này. Muốn dữ liệu khớp hôm nay thì xóa
-- database rồi chạy lại app — nên làm vào SÁNG NGÀY DEMO, sau 11:00 (mọi sự kiện "sáng nay" đều trước 11:00).
-- Mật khẩu mọi tài khoản: Test@123. Mọi INSERT bỏ qua dòng đã có (ON DUPLICATE KEY UPDATE id = id).
-- =============================================================================

SET NAMES utf8mb4;
SET @d0 = CURRENT_DATE;

`;
function writeFile(name, title, body) {
  const header = `-- =============================================================================\n-- DỮ LIỆU DEMO — ${title}\n` + HEADER_COMMON;
  fs.writeFileSync(path.join(OUT_DIR, name), header + body.replace(/\n{3,}/g, '\n\n'), 'utf8');
  console.log('  ghi', name, (Buffer.byteLength(body) / 1024).toFixed(0) + ' KB');
}
const table = (rows) => {   // bảng chú thích dạng cột
  const w = rows[0].map((_, i) => Math.max(...rows.map(r => String(r[i]).length)));
  return rows.map(r => '--   ' + r.map((c, i) => String(c).padEnd(w[i])).join('  ').trimEnd()).join('\n') + '\n';
};

// File 01
{
  let b = `--\n-- Tenant (Platform Admin thấy đủ 4 trạng thái; Bảng giá có thêm 1 mốc giá cũ):\n` + table([
    ['Tenant', 'Giám đốc', 'Trạng thái', 'Ghi chú'],
    ...tenants.map(t => [t.name, t.email, t.status + (t.suspend_reason ? '/' + t.suspend_reason : ''),
      t === main ? 'Chuỗi chính 3 KS, 4 hóa đơn, hạn kế tiếp d+20' : t === bienXanh ? 'Dùng thử còn 12 ngày'
        : t === hoangGia ? 'Sắp đến hạn gia hạn (d+5)' : t === phoCo ? 'Quá hạn 3 ngày — job khóa sau d+4'
          : t === doiThong ? 'Admin khóa — Giám đốc bị chặn' : 'Hết dùng thử — Giám đốc chỉ xem'])]) + '\n';
  b += insert('pricing_config', ['id', 'price_per_location', 'price_per_user', 'price_per_room', 'effective_from', 'created_at'], pricingRows);
  b += insert('tenants', ['id', 'name', 'contact_email', 'contact_phone', 'status', 'suspend_reason', 'suspended_at', 'created_at'],
    tenants.map(t => ({ id: t.id, name: t.name, contact_email: t.email, contact_phone: t.phone, status: t.status,
      suspend_reason: t.suspend_reason || null, suspended_at: t.suspended_at !== undefined ? ts(t.suspended_at) : null, created_at: ts(t.created) })));
  b += insert('subscriptions', ['id', 'tenant_id', 'quota_location', 'quota_user', 'quota_room', 'price_per_location', 'price_per_user',
    'price_per_room', 'is_trial', 'trial_ends_at', 'current_period_start', 'next_billing_date', 'created_at'],
    subs.map(s => Object.assign({ current_period_start: null, next_billing_date: null, created_at: ts(tenants.find(t => t.id === s.tenant_id).created) }, s)));
  b += insert('schedule_policies', ['id', 'tenant_id'], policies);
  b += insert('invoices', ['id', 'tenant_id', 'subscription_id', 'invoice_type', 'period_start', 'period_end', 'quota_location_snapshot',
    'quota_user_snapshot', 'quota_room_snapshot', 'amount', 'status', 'issued_at', 'paid_at', 'created_at'], invoices);
  writeFile('R__seed_demo_01_platform_billing.sql', 'tenant, gói dịch vụ, hóa đơn', b);
}
// File 02
{
  const roleLabel = u => u.role === 'STAFF' ? (u.perms.length ? u.perms.map(p => ({ RECEPTION: 'Lễ tân', HOUSEKEEPING: 'Dọn', SUPERVISION: 'Giám sát' })[p]).join('+') : 'Quyền chung') : u.role;
  const shiftLabel = u => u.shift ? `${{ S: 'Sáng', C: 'Chiều', D: 'Đêm' }[u.shift]}` : '';
  const note = u => [u.status !== 'ACTIVE' ? u.status : '', u.mustChange ? 'phải đổi mật khẩu lần đầu' : '',
    u.key === 'manager.hl' ? 'vào làm d+7 → KS Hạ Long chưa hoạt động' : '', u.key === 'manager.duphong' ? 'Manager dự bị, chưa gán KS' : ''].filter(Boolean).join('; ');
  let b = `--\n-- Tài khoản chuỗi Sen Việt (đuôi @swp391.test, mật khẩu Test@123):\n` + table([
    ['Email', 'Vai trò / quyền', 'KS', 'Ca', 'Ghi chú'],
    ...users.filter(u => u.tenant === main).map(u => [u.email, roleLabel(u), u.loc ? u.loc.key : '', shiftLabel(u), note(u)])]);
  b += `-- Tenant phụ: ${users.filter(u => u.tenant !== main).map(u => u.email).join(', ')}\n--\n`;
  b += `-- Hà Nội chấm công theo IP chế độ CẢNH BÁO: có mạng 113.160.92.0/24 và 127.0.0.1 / ::1 (máy chạy demo).\n`;
  b += `-- Đà Nẵng dùng bộ mẫu ca riêng 07–15 / 15–23 / 23–07. Danh mục có 1 phòng ban, 1 vị trí, 1 loại phòng đã ẩn.\n\n`;
  b += insert('locations', ['id', 'tenant_id', 'name', 'address', 'phone', 'star_rating', 'timezone', 'status', 'own_shift_templates', 'attendance_ip_mode', 'created_at'],
    locations.map(l => ({ id: l.id, tenant_id: l.tenant.id, name: l.name, address: l.address, phone: l.phone, star_rating: l.star,
      timezone: 'Asia/Ho_Chi_Minh', status: l.status, own_shift_templates: l.own, attendance_ip_mode: l.ipMode, created_at: ts(l.created) })));
  b += insert('departments', ['id', 'tenant_id', 'name', 'is_active'], departments);
  b += insert('positions', ['id', 'tenant_id', 'department_id', 'name', 'position_type', 'is_active'], positions);
  b += insert('room_types', ['id', 'tenant_id', 'name', 'is_active'], roomTypes);
  b += insert('areas', ['id', 'tenant_id', 'location_id', 'name'], areas);
  b += insert('shift_templates', ['id', 'tenant_id', 'location_id', 'name', 'start_time', 'end_time', 'description', 'is_active'],
    templates.map(t => ({ id: t.id, tenant_id: t.tenant_id, location_id: t.location_id, name: t.name, start_time: time(t.start),
      end_time: time(t.end), description: t.description, is_active: true })));
  const ordered = users.filter(u => u.role !== 'STAFF').concat(users.filter(u => u.role === 'STAFF'));
  b += insert('users', ['id', 'tenant_id', 'role', 'email', 'password_hash', 'must_change_password', 'status', 'full_name', 'phone',
    'location_id', 'position_id', 'start_work_date', 'date_of_birth', 'gender', 'address', 'avatar_url', 'terminated_at', 'terminated_by', 'created_at'],
    ordered.map(u => ({ id: u.id, tenant_id: u.tenant.id, role: u.role, email: u.email, password_hash: PASSWORD_HASH,
      must_change_password: !!u.mustChange, status: u.status, full_name: u.name, phone: u.phone,
      location_id: u.loc ? u.loc.id : null, position_id: u.pos ? u.pos.id : null,
      start_work_date: u.start !== undefined ? date(u.start) : null, date_of_birth: u.dob || null, gender: u.gender || null,
      address: u.address || null, avatar_url: u.role === 'DIRECTOR' ? null : avatar(u.name),
      terminated_at: u.terminatedAt !== undefined ? ts(u.terminatedAt) : null, terminated_by: u.terminatedBy ? u.terminatedBy.id : null,
      created_at: u.role === 'DIRECTOR' ? ts(u.created) : ts(at(Math.min(u.start, 0) - 1, '09:00')) })));
  b += insert('user_permissions', ['user_id', 'permission'],
    users.flatMap(u => u.perms.map(p => ({ user_id: u.id, permission: p }))), 'ON DUPLICATE KEY UPDATE permission = permission');
  b += insert('location_attendance_networks', ['id', 'tenant_id', 'location_id', 'cidr', 'label'], networks);
  writeFile('R__seed_demo_02_organization.sql', 'khách sạn, danh mục, tài khoản chuỗi "Sen Việt" và tenant phụ', b);
}
// File 03
{
  let b = `--\n-- Phòng: Hà Nội 30 (tầng 2–4), Đà Nẵng 24 (tầng 1–3), Hạ Long 4 (mới tạo, chờ dọn), tenant phụ 10.\n`;
  b += `-- Trạng thái phòng + việc dọn hôm nay nằm ở R__seed_demo_06_housekeeping.sql (cột status ở đây là trạng thái cuối).\n`;
  b += `-- Tài sản cố định ${fixed.length} (hỏng ${fixed.filter(a => a.status === 'BROKEN').length}, đang sửa ${fixed.filter(a => a.status === 'UNDER_REPAIR').length}, `;
  b += `thanh lý ${fixed.filter(a => a.status === 'DISPOSED').length}); vật tư ${consumables.length} dòng (Bóng đèn LED ở Hà Nội đã hết);\n`;
  b += `-- báo hỏng ${damages.length} (mới ${damages.filter(d => d.status === 'NEW').length}, đã xử lý ${damages.filter(d => d.status === 'RESOLVED').length}).\n\n`;
  b += insert('asset_categories', ['id', 'tenant_id', 'name', 'asset_kind', 'purpose', 'unit', 'is_active'], cats.map(c => Object.assign({ is_active: true }, c)));
  b += insert('rooms', ['id', 'tenant_id', 'location_id', 'room_number', 'floor', 'room_type_id', 'capacity', 'note', 'status', 'unavailable_reason', 'is_active', 'created_at'],
    rooms.map(r => ({ id: r.id, tenant_id: r.loc.tenant.id, location_id: r.loc.id, room_number: r.number, floor: r.floor,
      room_type_id: r.type.id, capacity: r.cap, note: r.type === RT.suite ? 'Phòng góc, view phố' : null, status: r.status,
      unavailable_reason: r.unavailable, is_active: true, created_at: ts(r.created) })));
  b += insert('fixed_assets', ['id', 'tenant_id', 'location_id', 'category_id', 'asset_code', 'name', 'room_id', 'area_id', 'status', 'note', 'created_at'], fixed);
  b += insert('consumable_items', ['id', 'tenant_id', 'location_id', 'category_id', 'quantity', 'last_counted_at', 'last_counted_by', 'created_at'], consumables);
  b += insert('damage_reports', ['id', 'tenant_id', 'location_id', 'fixed_asset_id', 'reporter_id', 'description', 'status', 'reported_at',
    'resolved_by', 'resolved_at', 'resolution_note', 'created_at'], damages);
  writeFile('R__seed_demo_03_rooms_assets.sql', 'phòng, tài sản cố định, vật tư, báo hỏng', b);
}
// File 04
{
  const nowRel = m => raw(`(NOW() ${m < 0 ? '-' : '+'} INTERVAL ${Math.abs(m)} MINUTE)`);
  const desc = s => `${s.requester.email.split('@')[0]} (${s.rs.tpl.name.toLowerCase()} d${s.rs.day >= 0 ? '+' : ''}${s.rs.day}) ⇄ ${s.peer.email.split('@')[0]} (${s.ps.tpl.name.toLowerCase()} d${s.ps.day >= 0 ? '+' : ''}${s.ps.day})`;
  let b = `--\n-- Ca làm Hà Nội + Đà Nẵng từ d${FROM} đến d+${TO}: ${shifts.length} ca (${shifts.filter(s => !s.staff).length} ca trống do nghỉ phép / nghỉ việc).\n`;
  b += `-- Mỗi người làm cố định 1 loại ca, nghỉ 1 ngày mỗi 7 ngày → luôn đúng policy mặc định; mỗi ca theo mẫu có lễ tân.\n`;
  b += `-- Chấm công ca đã qua: ~75% đúng giờ, còn lại đi muộn / về sớm / quên chấm ra / vắng; ca sáng hôm nay đã vào ca.\n`;
  b += `-- Hà Nội kiểm IP (CẢNH BÁO): đa số MATCHED, vài lần OUTSIDE (4G). Một số giờ do Manager nhập kèm lý do.\n--\n`;
  b += `-- Đổi ca:\n` + table([['Trạng thái', 'Cặp'], ...swaps.map(s => [s.status, desc(s)])]);
  b += `-- Đơn điều chỉnh công:\n` + table([['Trạng thái', 'Người gửi', 'Ca'], ...adjustments.map(a => [a.status, a.user.email.split('@')[0], `${a.shift.tpl.name.toLowerCase()} d${a.shift.day}`])]) + '\n';
  b += insert('shifts', ['id', 'tenant_id', 'location_id', 'staff_id', 'shift_date', 'start_time', 'end_time', 'is_overnight', 'duration_hours',
    'source_template_id', 'check_in_at', 'check_out_at', 'unassigned_reason', 'unassigned_at', 'created_at', 'created_by'],
    shifts.map(s => ({ id: s.id, tenant_id: main.id, location_id: s.loc.id, staff_id: s.staff, shift_date: date(s.day),
      start_time: time(s.start), end_time: time(s.start + 480), is_overnight: s.overnight, duration_hours: raw('8.00'),
      source_template_id: s.tpl.id, check_in_at: s.checkIn !== null ? ts(s.checkIn) : null, check_out_at: s.checkOut !== null ? ts(s.checkOut) : null,
      unassigned_reason: s.reason, unassigned_at: s.unassignedAt !== null ? ts(s.unassignedAt) : null,
      created_at: ts(at(Math.min(s.day - 7, -1), '10:00')), created_by: mgr[s.loc.key].id })));
  b += insert('shift_attendance_events', ['id', 'tenant_id', 'shift_id', 'staff_id', 'event_type', 'occurred_at', 'recorded_at', 'method',
    'recorded_by', 'ip_address', 'user_agent', 'verification_type', 'note', 'network_check', 'matched_cidr', 'created_at', 'created_by'], events);
  b += insert('attendance_adjustment_requests', ['id', 'tenant_id', 'location_id', 'shift_id', 'requester_id', 'requested_check_in_at',
    'requested_check_out_at', 'reason', 'status', 'reviewer_id', 'decided_at', 'review_note', 'created_at', 'created_by'],
    adjustments.map(a => ({ id: a.id, tenant_id: main.id, location_id: a.shift.loc.id, shift_id: a.shift.id, requester_id: a.user.id,
      requested_check_in_at: a.inT !== undefined ? ts(a.inT) : null, requested_check_out_at: a.outT !== undefined ? ts(a.outT) : null,
      reason: a.reason, status: a.status, reviewer_id: a.reviewer ? a.reviewer.id : null, decided_at: a.decided !== undefined ? ts(a.decided) : null,
      review_note: a.note || null, created_at: ts(a.created), created_by: a.user.id })));
  b += insert('shift_swap_requests', ['id', 'tenant_id', 'location_id', 'requester_id', 'requester_shift_id', 'peer_id', 'peer_shift_id',
    'status', 'peer_responded_at', 'expires_at', 'approver_id', 'decided_at', 'created_at', 'created_by'],
    swaps.map(s => ({ id: newId('d8300000'), tenant_id: main.id, location_id: s.loc.id, requester_id: s.requester.id,
      requester_shift_id: s.rs.id, peer_id: s.peer.id, peer_shift_id: s.ps.id, status: s.status,
      peer_responded_at: s.peerNow !== undefined ? nowRel(s.peerNow) : s.peerAt !== undefined ? ts(s.peerAt) : null,
      expires_at: s.expiresNow !== undefined ? nowRel(s.expiresNow) : ts(s.expires),
      approver_id: s.approver ? s.approver.id : null, decided_at: s.decided !== undefined ? ts(s.decided) : null,
      created_at: s.createdNow !== undefined ? nowRel(s.createdNow) : ts(s.created), created_by: s.requester.id })));
  writeFile('R__seed_demo_04_schedule_attendance.sql', 'ca làm, chấm công, đơn điều chỉnh công, đổi ca', b);
}
// File 05
{
  const png = makePng(160, 110);
  check(png.length < 5 * 1024 * 1024, 'ảnh quá 5MB');
  let b = `--\n-- Đơn nghỉ (chỉ nhân viên buồng phòng — duyệt không làm ca nào thiếu lễ tân):\n` + table([
    ['Người gửi', 'Loại', 'Ngày', 'Trạng thái'],
    ...leaves.map(l => [l.user.email.split('@')[0], l.type, `d${l.from}${l.to !== l.from ? '..d' + l.to : ''}`.replace(/d(\d)/g, 'd+$1'),
      l.status + (l.revokedBy ? ' (Manager thu hồi)' : l.status === 'CANCELLED' ? ' (tự hủy)' : '')])]);
  b += `-- Đơn của Manager Đà Nẵng do Giám đốc duyệt. Đơn nghỉ ốm d+1 kèm 1 ảnh PNG.\n\n`;
  b += insert('leave_requests', ['id', 'tenant_id', 'location_id', 'requester_id', 'leave_type', 'from_date', 'to_date', 'reason', 'status',
    'approver_id', 'decided_at', 'reject_reason', 'revoked_by', 'revoked_at', 'revoke_reason', 'original_to_date', 'created_at', 'created_by', 'updated_at'],
    leaves.map(l => {
      check(l.user.loc, 'người xin nghỉ phải có khách sạn');
      check(l.reason && l.reason.trim(), `đơn nghỉ ${l.user.key} thiếu lý do`);
      if (l.status === 'REJECTED') check(l.rejectReason, 'đơn từ chối thiếu lý do');
      if (l.approver) check(l.approver !== l.user, 'tự duyệt đơn');
      if (l.type === 'ANNUAL') check(l.from - dayOf(l.created) >= 3, `đơn phép năm ${l.user.key} báo trước chưa đủ 3 ngày`);
      check(l.from >= dayOf(l.created), 'đơn nghỉ bắt đầu trước ngày gửi');
      return { id: l.id, tenant_id: main.id, location_id: l.user.loc.id, requester_id: l.user.id, leave_type: l.type,
        from_date: date(l.from), to_date: date(l.to), reason: l.reason, status: l.status,
        approver_id: l.approver ? l.approver.id : null, decided_at: l.decided !== undefined ? ts(l.decided) : null,
        reject_reason: l.rejectReason || null, revoked_by: l.revokedBy ? l.revokedBy.id : null,
        revoked_at: l.revokedAt !== undefined ? ts(l.revokedAt) : null, revoke_reason: l.revokeReason || null, original_to_date: null,
        created_at: ts(l.created), created_by: l.user.id, updated_at: l.updated !== undefined ? ts(l.updated) : null };
    }));
  b += insert('leave_request_attachments', ['id', 'tenant_id', 'leave_request_id', 'file_name', 'content_type', 'size_bytes', 'data', 'created_at', 'created_by'],
    [{ id: newId('d9100000'), tenant_id: main.id, leave_request_id: sickLeave.id, file_name: 'giay-kham-benh.png', content_type: 'image/png',
      size_bytes: png.length, data: raw("X'" + png.toString('hex') + "'"), created_at: ts(sickLeave.created), created_by: sickLeave.user.id }]);
  writeFile('R__seed_demo_05_leave.sql', 'đơn nghỉ phép', b);
}
// File 06
{
  let b = `--\n-- Trạng thái phòng hôm nay (phòng không liệt kê: Sẵn sàng). 7 ngày trước mỗi ngày có 2–4 lượt dọn đã kiểm tra ĐẠT.\n`;
  for (const loc of [HN, DN]) {
    b += `-- ${loc.name}:\n` + table(roomsAt(loc).filter(r => scenarioLabels[`${loc.key}|${r.number}`] !== 'Sẵn sàng')
      .map(r => [r.number, r.status, scenarioLabels[`${loc.key}|${r.number}`]]));
  }
  b += '\n';
  const taskRow = t => ({ id: t.id, tenant_id: t.room.loc.tenant.id, location_id: t.room.loc.id, room_id: t.room.id, task_type: 'CHECKOUT',
    status: t.status, assigned_date: t.assignedDate !== null ? date(t.assignedDate) : null, assigned_by: t.assignedBy ? t.assignedBy.id : null,
    assigned_at: t.assignedAt !== null ? ts(t.assignedAt) : null, assignment_source: t.assignSource, completed_at: t.completedAt !== null ? ts(t.completedAt) : null,
    created_source: t.source, parent_task_id: t.parent ? t.parent.id : null, unassigned_reason: t.unassignedReason, cancel_reason: t.cancelReason,
    cancelled_at: t.cancelledAt !== null ? ts(t.cancelledAt) : null, completion_note: t.note, created_at: ts(t.created) });
  const cols = ['id', 'tenant_id', 'location_id', 'room_id', 'task_type', 'status', 'assigned_date', 'assigned_by', 'assigned_at', 'assignment_source',
    'completed_at', 'created_source', 'parent_task_id', 'unassigned_reason', 'cancel_reason', 'cancelled_at', 'completion_note', 'created_at'];
  b += insert('housekeeping_tasks', cols, tasks.filter(t => !t.parent).map(taskRow));
  b += insert('housekeeping_tasks', cols, tasks.filter(t => t.parent).map(taskRow));
  b += insert('housekeeping_task_assignees', ['task_id', 'staff_id'],
    tasks.flatMap(t => t.assignees.map(u => ({ task_id: t.id, staff_id: u.id }))), 'ON DUPLICATE KEY UPDATE staff_id = staff_id');
  b += insert('inspection_records', ['id', 'tenant_id', 'task_id', 'room_id', 'inspector_id', 'result', 'reason', 'inspected_at', 'next_task_id', 'created_at'], inspections);
  b += insert('housekeeping_task_returns', ['id', 'tenant_id', 'task_id', 'staff_id', 'reason', 'returned_at', 'task_released', 'created_at', 'created_by'], returnsRows);
  b += insert('room_status_history', ['id', 'tenant_id', 'room_id', 'from_status', 'to_status', 'changed_by', 'changed_at', 'change_source', 'reason', 'related_task_id', 'created_at'], history);
  writeFile('R__seed_demo_06_housekeeping.sql', 'việc dọn phòng, kiểm tra, lịch sử trạng thái phòng', b);
}

// ── Tóm tắt ──────────────────────────────────────────────────────────────────────
console.log(`\nĐã kiểm tra & sinh: ${tenants.length} tenant, ${locations.length} khách sạn, ${users.length} tài khoản, ${rooms.length} phòng,`);
console.log(`  ${shifts.length} ca, ${events.length} lượt chấm công, ${adjustments.length} đơn điều chỉnh, ${swaps.length} đổi ca, ${leaves.length} đơn nghỉ,`);
console.log(`  ${tasks.length} việc dọn, ${inspections.length} lượt kiểm tra, ${history.length} dòng lịch sử phòng, ${fixed.length} tài sản, ${damages.length} báo hỏng.`);
for (const t of tenants) console.log(`  ${t.name}: ${t.usage.locs}/${t.subRow.quota_location} KS, ${t.usage.staff}/${t.subRow.quota_user} NV, ${t.usage.rooms}/${t.subRow.quota_room} phòng`);
