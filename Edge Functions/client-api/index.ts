// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from 'jsr:@supabase/supabase-js@2'

Deno.serve(async (req) => {
  try {
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const funcRequested = req.headers.get("X-Function");

    // Insert Records
    if (funcRequested == "insert-records") {
      const jsonRecord = await req.json();
      
      // Use upsert with 'onConflict' to prevent duplicate and update logout_time
      const { data, error } = await supabase
        .from(Deno.env.get('RECORDS_TABLE_NAME') ?? '')
        .upsert(jsonRecord, { onConflict: 'session_id' });

      if (error) throw error;

      return new Response(JSON.stringify({ status: "Success" }), { status: 201 });
    }
    // Sync Configurations
    else if (funcRequested == "fetch-all-config") {
      const { data, error } = await supabase
        .from(Deno.env.get("CONFIG_TABLE_NAME") ?? '')
        .select('category, item_value');

      if (error) throw error;
      return new Response(JSON.stringify(data), { status: 200 });
    }

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }

})