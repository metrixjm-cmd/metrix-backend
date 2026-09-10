// Consolida filas de tenant_admin_index que quedaron escritas dentro de la BD
// de un tenant en vez del índice compartido que lee el login.
// Uso: mongosh "<uri>" --quiet --file scripts/fix-stray-login-index.js
const SHARED = 'metrix_db';
const shared = db.getSiblingDB(SHARED);

let moved = 0;
let skipped = 0;

db.adminCommand({ listDatabases: 1 }).databases
  .map(d => d.name)
  .filter(n => n.startsWith('metrix_tenant_'))
  .forEach(name => {
    const tenant = db.getSiblingDB(name);
    if (!tenant.getCollectionNames().includes('tenant_admin_index')) return;

    tenant.tenant_admin_index.find({}).forEach(row => {
      const dup = shared.tenant_admin_index.findOne({
        codigo_empresa: row.codigo_empresa,
        numero_usuario: row.numero_usuario,
      });
      if (dup) {
        print(`  ya existía: ${row.numero_usuario} (${row.codigo_empresa})`);
        skipped++;
        return;
      }
      const copy = Object.assign({}, row);
      delete copy._id;
      shared.tenant_admin_index.insertOne(copy);
      print(`  movida: ${row.numero_usuario} (${row.codigo_empresa}) desde ${name}`);
      moved++;
    });
  });

print('');
print(`movidas=${moved} yaExistian=${skipped}`);
print(`total en ${SHARED}.tenant_admin_index = ${shared.tenant_admin_index.countDocuments()}`);
