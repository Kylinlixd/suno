package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V5900__Reconcile_canonical_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        new SchemaReconciler(context.getConnection()).reconcile(CanonicalSchemaManifest.tables());
    }
}
