// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from 'jsr:@supabase/supabase-js@2'

Deno.serve(async (req) => {
  try {
    // Validity check
    const isAdmin = (req.headers.get("X-SERVER-HEADER") === Deno.env.get("ADMIN_KEY")) 
    if (!isAdmin) return new Response("Forbidden", { status: 403 });

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const funcRequested = req.headers.get("X-Function");

    // Verify Connection
    if (funcRequested == "verify-connection") {
      return new Response(JSON.stringify({ status: "connected" }), { status: 200 });
    }
    // Sync Records -> RecordsTable
    else if (funcRequested == "fetch-new-data") {
      const { lastLoginTimestamp, lastLogoutTimestamp } = await req.json();

      //  Select entries that are new or have updated logout-time
      const { data, error } = await supabase
        .from(Deno.env.get("RECORDS_TABLE_NAME") ?? '')
        .select('*')
        .or(`login_time.gt.${lastLoginTimestamp},logout_time.gt.${lastLogoutTimestamp}`);

      if (error) throw error;

      return new Response(JSON.stringify(data), {
        headers: { 'Content-Type': 'application/json' },
        status: 200,
      })
    } 
    // Table Creation (First run) (configTable and RecordsTable) 
    else if (funcRequested == "setup-db") {
        // schema definition
        const sql = `
          CREATE TABLE IF NOT EXISTS ${Deno.env.get("CONFIG_TABLE_NAME")} (
            id SERIAL PRIMARY KEY,
            category TEXT NOT NULL,
            item_value TEXT NOT NULL,
            created_at TIMESTAMPTZ DEFAULT NOW(),
            UNIQUE(category, item_value)
          );

          CREATE TABLE IF NOT EXISTS ${Deno.env.get("RECORDS_TABLE_NAME")} (
            session_id TEXT PRIMARY KEY,
            login_time TEXT NOT NULL,
            logout_time TEXT,
            usn TEXT NOT NULL,
            name TEXT NOT NULL,
            details TEXT
          );
        `;

        const rlsSql = `
          -- Enable RLS for Config Table
          ALTER TABLE ${Deno.env.get("CONFIG_TABLE_NAME")} ENABLE ROW LEVEL SECURITY;
          
          DROP POLICY IF EXISTS "Allow public read access" ON ${Deno.env.get("CONFIG_TABLE_NAME")};
          
          CREATE POLICY "Allow public read access" 
          ON ${Deno.env.get("CONFIG_TABLE_NAME")} 
          FOR SELECT TO anon USING (true);
          
          -- Enable RLS for Records Table 
          ALTER TABLE ${Deno.env.get("RECORDS_TABLE_NAME")} ENABLE ROW LEVEL SECURITY;

          DROP POLICY IF EXISTS "Client can insert logs" ON ${Deno.env.get("RECORDS_TABLE_NAME")};
          
          CREATE POLICY "Client can insert logs" 
          ON ${Deno.env.get("RECORDS_TABLE_NAME")} 
          FOR INSERT TO anon WITH CHECK (true);
        `;

          // Call the RPC function 
          const { error } = await supabase.rpc('exec_sql', { query: sql+rlsSql });
          
          if (error) throw error;

          return new Response(JSON.stringify({ message: "Tables created successfully" }), {
            headers: { "Content-Type": "application/json" },
          });
    } 
    // Sync options (subjects, depts) -> configTable
    else if (funcRequested == "sync-categories") {
      const payload = await req.json();
      // Ensure we are working with an array even if only one item was sent
      const updates = Array.isArray(payload) ? payload : [payload];
      const configTable = Deno.env.get("CONFIG_TABLE_NAME");

      let totalSql = "";

      for (const update of updates) {
        const { category, toAdd, toDelete } = update;

        if (toDelete && toDelete.length > 0) {
          const formattedDelete = toDelete.map((item: string) => `'${item.replace(/'/g, "''")}'`).join(",");
          totalSql += `DELETE FROM ${configTable} WHERE category = '${category}' AND item_value IN (${formattedDelete}); `;
        }

        if (toAdd && toAdd.length > 0) {
          const values = toAdd.map((item: string) => `('${category}', '${item.replace(/'/g, "''")}')`).join(",");
          totalSql += `INSERT INTO ${configTable} (category, item_value) VALUES ${values} ON CONFLICT (category, item_value) DO NOTHING; `;
        }
      }

      if (totalSql) {
        const { error } = await supabase.rpc('exec_sql', { query: totalSql });
        if (error) throw error;
      }

      return new Response(JSON.stringify({ success: true }), { status: 200 });
    }
    // Delete records for Auto-delete -> RecordsTable
    else if (funcRequested == "delete-records") {
      // We only delete logs, NEVER the config table
      const { error } = await supabase
        .from(Deno.env.get("RECORDS_TABLE_NAME") ?? '')
        .delete()
        .neq('session_id', '0'); // Hack to delete all records safely

      if (error) throw error;
      return new Response(JSON.stringify({ message: "Cloud logs cleared" }), { status: 200 });
    }
    //Kill Switch (Drop ConfigTable and RecordsTable)
    else if (funcRequested == "teardown-db") {
      const recordsTable = Deno.env.get("RECORDS_TABLE_NAME");
      const configTable = Deno.env.get("CONFIG_TABLE_NAME");
      
      // SQL to completely remove the tables from the database
      const sql = `DROP TABLE IF EXISTS ${recordsTable}, ${configTable};`;
      
      const { error } = await supabase.rpc('exec_sql', { query: sql });
      if (error) throw error;

      return new Response(JSON.stringify({ message: "Cloud database wiped successfully" }), { status: 200 });
    }
  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }

})