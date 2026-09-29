package fr.taverne.mercenaires;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** HTTP transport for the shared game. Call from a background thread only. */
final class SupabaseGateway {
    private final String projectUrl, publishableKey;
    private String accessToken;

    SupabaseGateway(String projectUrl,String publishableKey){
        if(!projectUrl.matches("https://[a-z0-9-]+\\.supabase\\.co/?"))throw new IllegalArgumentException("URL Supabase invalide");
        if(publishableKey.isEmpty())throw new IllegalArgumentException("Clé publique manquante");
        this.projectUrl=projectUrl.replaceAll("/$","");this.publishableKey=publishableKey;
    }
    void useToken(String token){accessToken=token;}
    JSONObject currentUser()throws Exception{return new JSONObject(request("GET","/auth/v1/user",null,true));}
    JSONObject signUp(String email,String password)throws Exception{
        JSONObject request=new JSONObject().put("email",email).put("password",password);
        return objectRequest("POST","/auth/v1/signup",request,false);
    }
    JSONObject signIn(String email,String password)throws Exception{
        JSONObject request=new JSONObject().put("email",email).put("password",password);
        JSONObject result=objectRequest("POST","/auth/v1/token?grant_type=password",request,false);
        accessToken=result.getString("access_token");return result;
    }
    JSONObject refresh(String refreshToken)throws Exception{
        JSONObject result=objectRequest("POST","/auth/v1/token?grant_type=refresh_token",new JSONObject().put("refresh_token",refreshToken),false);
        accessToken=result.getString("access_token");return result;
    }
    void signOut(){accessToken=null;}
    JSONObject importLocalCharacters(String pseudo,JSONArray characters)throws Exception{
        return objectRequest("POST","/rest/v1/rpc/import_local_characters",new JSONObject().put("p_pseudo",pseudo).put("p_characters",characters),true);
    }
    JSONObject buy(String characterId,String itemId)throws Exception{
        return objectRequest("POST","/rest/v1/rpc/buy_consumable",new JSONObject().put("p_character",characterId).put("p_item",itemId),true);
    }
    JSONObject joinAndVote(String contractId,String characterId,JSONArray slotIds)throws Exception{
        return objectRequest("POST","/rest/v1/rpc/join_and_vote",new JSONObject().put("p_contract",contractId).put("p_character",characterId).put("p_slots",slotIds),true);
    }
    JSONObject lockDate(String contractId,String slotId,String isoDate)throws Exception{
        return objectRequest("POST","/rest/v1/rpc/lock_contract_date",new JSONObject().put("p_contract",contractId).put("p_slot",slotId).put("p_date",isoDate),true);
    }
    JSONObject completeContract(String contractId)throws Exception{
        return objectRequest("POST","/rest/v1/rpc/complete_contract",new JSONObject().put("p_contract",contractId),true);
    }
    JSONArray list(String table,String query)throws Exception{
        if(!table.matches("profiles|characters|contracts|slots|participants|votes|messages|catalog|rumours|campaign_entries"))throw new IllegalArgumentException("Table inconnue");
        return new JSONArray(request("GET","/rest/v1/"+table+"?"+query,null,true));
    }
    JSONArray names()throws Exception{return new JSONArray(request("POST","/rest/v1/rpc/shared_character_names",new JSONObject(),true));}
    JSONObject rpc(String name,JSONObject args)throws Exception{
        if(!name.matches("publish_contract|publish_contract_once|edit_contract|save_character|update_character_part|set_pseudo|set_character_gold|edit_rumour"))throw new IllegalArgumentException("Opération inconnue");
        return objectRequest("POST","/rest/v1/rpc/"+name,args,true);
    }
    void insertRumour(JSONObject entry)throws Exception{request("POST","/rest/v1/rumours",entry,true);}
    void insertRumourOnce(JSONObject entry)throws Exception{try{insertRumour(entry);}catch(IllegalStateException error){
        if(!error.getMessage().contains("(409)")||list("rumours","select=id&id=eq."+entry.getString("id")).length()!=1)throw error;
    }}
    void insertCampaignEntry(JSONObject entry)throws Exception{request("POST","/rest/v1/campaign_entries",entry,true);}
    void insertMessage(JSONObject entry)throws Exception{request("POST","/rest/v1/messages",entry,true);}
    void updateCharacterField(String id,String field,String value)throws Exception{
        if(!field.matches("inventory|campaign_notes"))throw new IllegalArgumentException("Champ inconnu");
        rpc("update_character_part",new JSONObject().put("p_id",id).put("p_field",field).put("p_value",value));
    }
    void remove(String table,String id)throws Exception{
        if(!table.matches("characters|contracts|rumours|campaign_entries"))throw new IllegalArgumentException("Table inconnue");
        request("DELETE","/rest/v1/"+table+"?id=eq."+id,null,true);
    }
    private JSONObject objectRequest(String method,String path,JSONObject body,boolean authenticated)throws Exception{
        String result=request(method,path,body,authenticated);
        // SQL functions returning scalar values may return a JSON number instead of an object.
        return result.startsWith("{")?new JSONObject(result):new JSONObject().put("value",result.isEmpty()?JSONObject.NULL:result);
    }
    private String request(String method,String path,JSONObject body,boolean authenticated)throws Exception{
        if(authenticated&&accessToken==null)throw new IllegalStateException("Connexion nécessaire");
        HttpsURLConnection connection=(HttpsURLConnection)new URL(projectUrl+path).openConnection();
        connection.setRequestMethod(method);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
        connection.setRequestProperty("apikey",publishableKey);
        if(authenticated)connection.setRequestProperty("Authorization","Bearer "+accessToken);
        if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
            try(OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
        try{
            int status=connection.getResponseCode();InputStream stream=status<400?connection.getInputStream():connection.getErrorStream();
            String value=stream==null?"":readLimited(stream);
            if(status>=400)throw new IllegalStateException("Serveur Supabase ("+status+") : "+value);
            return value;
        }finally{connection.disconnect();}
    }
    private static String readLimited(InputStream input)throws Exception{
        try(InputStream stream=input;ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;
            while((count=stream.read(buffer))!=-1){if(output.size()+count>2_000_000)throw new IllegalStateException("Réponse trop grande");output.write(buffer,0,count);}
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
