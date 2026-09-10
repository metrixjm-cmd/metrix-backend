// Rellena codigo_empresa en filas del índice de login que quedaron sin código,
// tomándolo de la instancia correspondiente.
// Uso: mongosh "<uri>" --quiet --file scripts/backfill-login-index-codes.js
const p = db.getSiblingDB('metrix_db');
let fixed = 0;

p.tenant_admin_index
  .find({ $or: [{ codigo_empresa: null }, { codigo_empresa: { $exists: false } }] })
  .forEach(row => {
    const instance = p.metrix_instances.findOne({ _id: row.instance_id });
    if (!instance || !instance.codigo_empresa) {
      print(`  sin instancia resoluble: ${row.numero_usuario}`);
      return;
    }
    p.tenant_admin_index.updateOne({ _id: row._id }, { $set: { codigo_empresa: instance.codigo_empresa } });
    print(`  ${row.numero_usuario} -> ${instance.codigo_empresa}`);
    fixed++;
  });

print(`reparadas=${fixed}`);
